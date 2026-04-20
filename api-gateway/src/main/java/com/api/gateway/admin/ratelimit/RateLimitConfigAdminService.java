package com.api.gateway.admin.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

/**
 * Business logic cho Rate Limit Config Admin API.
 *
 * <p>Sau mỗi write operation, invalidate Redis cache tương ứng để
 * {@link RateLimitConfigService} load config mới từ DB trong request kế tiếp.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitConfigAdminService {

    private final RateLimitConfigR2dbcRepository configRepo;
    private final RateLimitConfigService         configService;

    // ─── Queries ────────────────────────────────────────────────────────────

    public Flux<RateLimitConfigDtos.ConfigResponse> getAll() {
        return configRepo.findAll().map(this::toResponse);
    }

    public Mono<RateLimitConfigDtos.ConfigResponse> getById(Long id) {
        return configRepo.findById(id)
                .switchIfEmpty(notFound(id))
                .map(this::toResponse);
    }

    public Mono<RateLimitConfigDtos.ConfigResponse> getGlobalDefault() {
        return configRepo.findGlobalDefault()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Global default config not found")))
                .map(this::toResponse);
    }

    public Mono<RateLimitConfigDtos.ConfigResponse> getByUsername(String username) {
        return configRepo.findByUsername(username)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No rate limit override found for user: " + username)))
                .map(this::toResponse);
    }

    // ─── Commands ───────────────────────────────────────────────────────────

    /**
     * Tạo per-user override mới.
     * username == null → update global default thay vì tạo mới.
     */
    public Mono<RateLimitConfigDtos.ConfigResponse> create(RateLimitConfigDtos.CreateRequest req) {
        validate(req.replenishRate(), req.burstCapacity());

        // Nếu username null → update global default
        if (req.username() == null) {
            return configRepo.findGlobalDefault()
                    .flatMap(existing -> {
                        var updated = new RateLimitConfigEntity(
                                existing.id(), null,
                                req.replenishRate(), req.burstCapacity(),
                                existing.enabled(),
                                req.description() != null ? req.description() : existing.description(),
                                existing.createdAt(), OffsetDateTime.now()
                        );
                        return configRepo.save(updated);
                    })
                    .flatMap(saved -> configService.invalidateAllConfigCache().thenReturn(saved))
                    .map(this::toResponse)
                    .doOnSuccess(r -> log.info("Global default rate limit updated: {}r/s, {}b", r.replenishRate(), r.burstCapacity()));
        }

        // Tạo per-user override
        return configRepo.existsByUsername(req.username())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Rate limit override already exists for user: " + req.username()
                                        + ". Use PUT /{id} to update."));
                    }
                    var entity = new RateLimitConfigEntity(
                            null, req.username(),
                            req.replenishRate(), req.burstCapacity(),
                            true, req.description(),
                            OffsetDateTime.now(), OffsetDateTime.now()
                    );
                    return configRepo.save(entity);
                })
                .flatMap(saved -> configService.invalidateCache(saved.username()).thenReturn(saved))
                .map(this::toResponse)
                .doOnSuccess(r -> log.info("Per-user rate limit created for [{}]: {}r/s, {}b",
                        r.username(), r.replenishRate(), r.burstCapacity()));
    }

    /**
     * Update một config entry (per-user hoặc global default).
     * Chỉ các field không null trong request mới được áp dụng.
     */
    public Mono<RateLimitConfigDtos.ConfigResponse> update(Long id, RateLimitConfigDtos.UpdateRequest req) {
        return configRepo.findById(id)
                .switchIfEmpty(notFound(id))
                .flatMap(existing -> {
                    int newRate     = req.replenishRate() != null ? req.replenishRate() : existing.replenishRate();
                    int newBurst    = req.burstCapacity() != null ? req.burstCapacity() : existing.burstCapacity();
                    boolean enabled = req.enabled()      != null ? req.enabled()       : existing.enabled();
                    String desc     = req.description()  != null ? req.description()   : existing.description();

                    validate(newRate, newBurst);

                    var updated = new RateLimitConfigEntity(
                            existing.id(), existing.username(),
                            newRate, newBurst, enabled, desc,
                            existing.createdAt(), OffsetDateTime.now()
                    );
                    return configRepo.save(updated);
                })
                .flatMap(saved -> {
                    // Invalidate cache: nếu global default → xóa tất cả, nếu per-user → xóa của user đó
                    Mono<Void> invalidate = saved.username() == null
                            ? configService.invalidateAllConfigCache()
                            : configService.invalidateCache(saved.username());
                    return invalidate.thenReturn(saved);
                })
                .map(this::toResponse)
                .doOnSuccess(r -> log.info("Rate limit config [{}] updated for user [{}]", id, r.username()));
    }

    /**
     * Xóa per-user override.
     * Không cho phép xóa global default (username IS NULL).
     */
    public Mono<Void> delete(Long id) {
        return configRepo.findById(id)
                .switchIfEmpty(notFound(id))
                .flatMap(entity -> {
                    if (entity.username() == null) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Cannot delete global default config. Use PUT to update it."));
                    }
                    return configRepo.delete(entity)
                            .then(configService.invalidateCache(entity.username()));
                })
                .doOnSuccess(v -> log.info("Rate limit override [{}] deleted", id));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private RateLimitConfigDtos.ConfigResponse toResponse(RateLimitConfigEntity e) {
        return new RateLimitConfigDtos.ConfigResponse(
                e.id(), e.username(),
                e.replenishRate(), e.burstCapacity(),
                e.enabled(), e.description(),
                e.createdAt(), e.updatedAt()
        );
    }

    private void validate(int replenishRate, int burstCapacity) {
        if (replenishRate <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "replenishRate must be > 0");
        }
        if (burstCapacity < replenishRate) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "burstCapacity must be >= replenishRate");
        }
    }

    private <T> Mono<T> notFound(Long id) {
        return Mono.error(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Rate limit config not found: " + id));
    }
}

package com.api.gateway.admin.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Business logic cho Rate Limit Config Admin API.
 *
 * <p>Sau mỗi write operation, invalidate Redis cache tương ứng để
 * RateLimitConfigService load config mới từ DB trong request kế tiếp.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitConfigAdminService {

    private final RateLimitConfigRepository configRepo;
    private final RateLimitConfigService    configService;

    // ─── Queries ────────────────────────────────────────────────────────────

    public List<RateLimitConfigDtos.ConfigResponse> getAll() {
        return StreamSupport.stream(configRepo.findAll().spliterator(), false)
                .map(this::toResponse)
                .toList();
    }

    public RateLimitConfigDtos.ConfigResponse getById(Long id) {
        return configRepo.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rate limit config not found: " + id));
    }

    public RateLimitConfigDtos.ConfigResponse getGlobalDefault() {
        return configRepo.findGlobalDefault()
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Global default config not found"));
    }

    public RateLimitConfigDtos.ConfigResponse getByUsername(String username) {
        return configRepo.findByUsername(username)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No rate limit override found for user: " + username));
    }

    // ─── Commands ───────────────────────────────────────────────────────────

    public RateLimitConfigDtos.ConfigResponse create(RateLimitConfigDtos.CreateRequest req) {
        validate(req.replenishRate(), req.burstCapacity());

        // Nếu username null → update global default
        if (req.username() == null) {
            RateLimitConfigEntity existing = configRepo.findGlobalDefault()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Global default config not found"));
            var updated = new RateLimitConfigEntity(
                    existing.id(), null,
                    req.replenishRate(), req.burstCapacity(),
                    existing.enabled(),
                    req.description() != null ? req.description() : existing.description(),
                    existing.createdAt(), OffsetDateTime.now()
            );
            var saved = configRepo.save(updated);
            configService.invalidateAllConfigCache();
            log.info("Global default rate limit updated: {}r/s, {}b", saved.replenishRate(), saved.burstCapacity());
            return toResponse(saved);
        }

        // Tạo per-user override
        if (configRepo.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Rate limit override already exists for user: " + req.username()
                            + ". Use PUT /{id} to update.");
        }
        var entity = new RateLimitConfigEntity(
                null, req.username(),
                req.replenishRate(), req.burstCapacity(),
                true, req.description(),
                OffsetDateTime.now(), OffsetDateTime.now()
        );
        var saved = configRepo.save(entity);
        configService.invalidateCache(saved.username());
        log.info("Per-user rate limit created for [{}]: {}r/s, {}b",
                saved.username(), saved.replenishRate(), saved.burstCapacity());
        return toResponse(saved);
    }

    public RateLimitConfigDtos.ConfigResponse update(Long id, RateLimitConfigDtos.UpdateRequest req) {
        RateLimitConfigEntity existing = configRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rate limit config not found: " + id));

        int     newRate  = req.replenishRate() != null ? req.replenishRate() : existing.replenishRate();
        int     newBurst = req.burstCapacity() != null ? req.burstCapacity() : existing.burstCapacity();
        boolean enabled  = req.enabled()       != null ? req.enabled()       : existing.enabled();
        String  desc     = req.description()   != null ? req.description()   : existing.description();

        validate(newRate, newBurst);

        var updated = new RateLimitConfigEntity(
                existing.id(), existing.username(),
                newRate, newBurst, enabled, desc,
                existing.createdAt(), OffsetDateTime.now()
        );
        var saved = configRepo.save(updated);

        if (saved.username() == null) {
            configService.invalidateAllConfigCache();
        } else {
            configService.invalidateCache(saved.username());
        }
        log.info("Rate limit config [{}] updated for user [{}]", id, saved.username());
        return toResponse(saved);
    }

    public void delete(Long id) {
        RateLimitConfigEntity entity = configRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rate limit config not found: " + id));
        if (entity.username() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot delete global default config. Use PUT to update it.");
        }
        configRepo.delete(entity);
        configService.invalidateCache(entity.username());
        log.info("Rate limit override [{}] deleted", id);
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
}

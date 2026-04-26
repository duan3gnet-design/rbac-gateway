package com.api.gateway.admin.ratelimit;

import com.api.gateway.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Service tra cứu cấu hình rate limit cho một user.
 *
 * <p><b>Lookup order:</b>
 * <ol>
 *   <li>Redis cache ({@code rl_cfg:{username}}) — TTL 5 phút</li>
 *   <li>DB: per-user override ({@code username = ?})</li>
 *   <li>DB: global default ({@code username IS NULL})</li>
 *   <li>Fallback: {@code application.yml} properties</li>
 * </ol>
 * </p>
 *
 * <p><b>Cache invalidation:</b> gọi {@link #invalidateCache(String)} sau mỗi
 * lần admin cập nhật config. Global default dùng key {@code rl_cfg:__default__}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitConfigService {

    static final String CACHE_PREFIX       = "rl_cfg:";
    static final String GLOBAL_CACHE_KEY   = CACHE_PREFIX + "__default__";
    static final Duration CACHE_TTL        = Duration.ofMinutes(5);

    // Separator trong Redis value: "replenishRate|burstCapacity"
    private static final String SEP = "|";

    private final RateLimitConfigR2dbcRepository configRepo;
    private final ReactiveStringRedisTemplate    redisTemplate;
    private final RateLimitProperties            fallbackProperties;

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Resolve config hiệu lực cho {@code username}.
     * Trả về Mono<int[2]>: [replenishRate, burstCapacity].
     */
    public Mono<int[]> resolveConfig(String username) {
        String cacheKey = CACHE_PREFIX + username;

        return getFromCache(cacheKey)
                .switchIfEmpty(
                        loadFromDb(username)
                                .flatMap(cfg -> putCache(cacheKey, cfg).thenReturn(cfg))
                )
                .onErrorResume(e -> {
                    log.warn("RateLimitConfigService error for [{}]: {} — using fallback", username, e.getMessage());
                    return Mono.just(fallbackConfig());
                });
    }

    /**
     * Resolve global default config (dùng cho anonymous / IP-based bucket).
     */
    public Mono<int[]> resolveGlobalDefault() {
        return getFromCache(GLOBAL_CACHE_KEY)
                .switchIfEmpty(
                        configRepo.findGlobalDefault()
                                .map(e -> new int[]{e.replenishRate(), e.burstCapacity()})
                                .defaultIfEmpty(fallbackConfig())
                                .flatMap(cfg -> putCache(GLOBAL_CACHE_KEY, cfg).thenReturn(cfg))
                )
                .onErrorResume(e -> {
                    log.warn("RateLimitConfigService global default error: {} — using fallback", e.getMessage());
                    return Mono.just(fallbackConfig());
                });
    }

    /**
     * Invalidate cache cho một user cụ thể (gọi sau khi admin update per-user config).
     */
    public Mono<Void> invalidateCache(String username) {
        String key = username == null ? GLOBAL_CACHE_KEY : CACHE_PREFIX + username;
        return redisTemplate.delete(key)
                .doOnSuccess(v -> log.debug("Rate limit cache invalidated: {}", key))
                .then();
    }

    /**
     * Invalidate toàn bộ cache config (gọi khi update global default).
     *
     * <p>Dùng SCAN thay vì KEYS để không blocking Redis event loop
     * khi có nhiều key — quan trọng khi đang chạy dưới tải cao.</p>
     */
    public Mono<Void> invalidateAllConfigCache() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(CACHE_PREFIX + "*")
                .count(100)
                .build();

        return redisTemplate.scan(options)
                .flatMap(key -> redisTemplate.delete(key)
                        .doOnSuccess(v -> log.debug("Cache key deleted: {}", key)))
                .doOnComplete(() -> log.debug("All rate limit config cache invalidated"))
                .then();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Mono<int[]> getFromCache(String key) {
        return redisTemplate.opsForValue().get(key)
                .map(this::deserialize);
    }

    private Mono<Boolean> putCache(String key, int[] cfg) {
        return redisTemplate.opsForValue()
                .set(key, serialize(cfg), CACHE_TTL);
    }

    /**
     * Load từ DB: thử per-user trước, fallback về global default.
     */
    private Mono<int[]> loadFromDb(String username) {
        return configRepo.findByUsername(username)
                .filter(RateLimitConfigEntity::enabled)
                .map(e -> new int[]{e.replenishRate(), e.burstCapacity()})
                .switchIfEmpty(
                        configRepo.findGlobalDefault()
                                .map(e -> new int[]{e.replenishRate(), e.burstCapacity()})
                                .defaultIfEmpty(fallbackConfig())
                );
    }

    private int[] fallbackConfig() {
        return new int[]{
                fallbackProperties.replenishRate(),
                fallbackProperties.burstCapacity()
        };
    }

    private String serialize(int[] cfg) {
        return cfg[0] + SEP + cfg[1];
    }

    private int[] deserialize(String value) {
        String[] parts = value.split("\\" + SEP);
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }
}

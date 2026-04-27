package com.api.gateway.admin.ratelimit;

import com.api.gateway.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * Service tra cứu cấu hình rate limit cho một user.
 *
 * <p><b>Lookup order:</b>
 * <ol>
 *   <li>Redis cache ({@code rl_cfg:{username}}) — TTL 5 phút</li>
 *   <li>DB: per-user override</li>
 *   <li>DB: global default</li>
 *   <li>Fallback: application.yml properties</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitConfigService {

    static final String   CACHE_PREFIX     = "rl_cfg:";
    static final String   GLOBAL_CACHE_KEY = CACHE_PREFIX + "__default__";
    static final Duration CACHE_TTL        = Duration.ofMinutes(5);

    private static final String SEP = "|";

    private final RateLimitConfigRepository configRepo;
    private final StringRedisTemplate       redisTemplate;
    private final RateLimitProperties       fallbackProperties;

    // ─── Public API ──────────────────────────────────────────────────────────

    public int[] resolveConfig(String username) {
        String cacheKey = CACHE_PREFIX + username;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return deserialize(cached);

            int[] cfg = loadFromDb(username);
            redisTemplate.opsForValue().set(cacheKey, serialize(cfg), CACHE_TTL);
            return cfg;
        } catch (Exception e) {
            log.warn("RateLimitConfigService error for [{}]: {} — using fallback", username, e.getMessage());
            return fallbackConfig();
        }
    }

    public int[] resolveGlobalDefault() {
        try {
            String cached = redisTemplate.opsForValue().get(GLOBAL_CACHE_KEY);
            if (cached != null) return deserialize(cached);

            int[] cfg = configRepo.findGlobalDefault()
                    .map(e -> new int[]{e.replenishRate(), e.burstCapacity()})
                    .orElseGet(this::fallbackConfig);
            redisTemplate.opsForValue().set(GLOBAL_CACHE_KEY, serialize(cfg), CACHE_TTL);
            return cfg;
        } catch (Exception e) {
            log.warn("RateLimitConfigService global default error: {} — using fallback", e.getMessage());
            return fallbackConfig();
        }
    }

    public void invalidateCache(String username) {
        String key = username == null ? GLOBAL_CACHE_KEY : CACHE_PREFIX + username;
        redisTemplate.delete(key);
        log.debug("Rate limit cache invalidated: {}", key);
    }

    public void invalidateAllConfigCache() {
        Set<String> keys = redisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("All rate limit config cache invalidated ({} keys)", keys.size());
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private int[] loadFromDb(String username) {
        return configRepo.findByUsername(username)
                .filter(RateLimitConfigEntity::enabled)
                .map(e -> new int[]{e.replenishRate(), e.burstCapacity()})
                .orElseGet(() ->
                        configRepo.findGlobalDefault()
                                .map(e -> new int[]{e.replenishRate(), e.burstCapacity()})
                                .orElseGet(this::fallbackConfig)
                );
    }

    private int[] fallbackConfig() {
        return new int[]{
                fallbackProperties.replenishRate(),
                fallbackProperties.burstCapacity()
        };
    }

    private String serialize(int[] cfg)  { return cfg[0] + SEP + cfg[1]; }

    private int[] deserialize(String v) {
        String[] parts = v.split("\\" + SEP);
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }
}

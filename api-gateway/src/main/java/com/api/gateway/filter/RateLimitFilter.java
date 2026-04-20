package com.api.gateway.filter;

import com.api.gateway.config.RateLimitProperties;
import com.api.gateway.admin.ratelimit.RateLimitConfigService;
import com.api.gateway.validator.JwtValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Rate Limiting Filter — Token Bucket per User via Redis Lua script.
 *
 * <p>Order: -99 (chạy sau JwtAuthenticationFilter -100, trước downstream proxy)</p>
 *
 * <p><b>Config lookup order</b> (delegate sang {@link RateLimitConfigService}):
 * <ol>
 *   <li>Redis cache (TTL 5 phút)</li>
 *   <li>DB: per-user override</li>
 *   <li>DB: global default</li>
 *   <li>Fallback: {@code application.yml}</li>
 * </ol>
 * </p>
 *
 * <p><b>Bucket key strategy:</b>
 * <ul>
 *   <li>Authenticated: {@code rate_limit:user:{username}}</li>
 *   <li>Anonymous: {@code rate_limit:ip:{clientIp}}</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final JwtValidator                jwtValidator;
    private final RateLimitProperties         fallbackProperties;
    private final RateLimitConfigService      rateLimitConfigService;
    private final ObjectMapper                objectMapper;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** Lua script: Token Bucket algorithm — atomic check-and-decrement */
    private static final DefaultRedisScript<List<Long>> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/rate_limit.lua"))
        );
        RATE_LIMIT_SCRIPT.setResultType((Class<List<Long>>) (Class<?>) List.class);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        return resolveIdentity(exchange)
                .flatMap(identity -> {
                    // identity = "user:{username}" hoặc "ip:{ip}"
                    boolean isUser = identity.startsWith("user:");
                    String  username = isUser ? identity.substring(5) : null;

                    Mono<int[]> configMono = isUser
                            ? rateLimitConfigService.resolveConfig(username)
                            : rateLimitConfigService.resolveGlobalDefault();

                    return configMono.flatMap(cfg ->
                            checkRateLimit(exchange, chain, identity, cfg[0], cfg[1])
                    );
                });
    }

    /**
     * Xác định identity cho bucket.
     * Ưu tiên: username từ JWT > IP address
     */
    private Mono<String> resolveIdentity(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return jwtValidator.validate(authHeader.substring(7))
                    .map(claims -> "user:" + claims.username())
                    .onErrorResume(e -> Mono.just("ip:" + extractClientIp(exchange)));
        }

        return Mono.just("ip:" + extractClientIp(exchange));
    }

    /**
     * Thực hiện rate limit check bằng Lua script trên Redis.
     */
    @SuppressWarnings("unchecked")
    private Mono<Void> checkRateLimit(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String identity,
            int replenishRate,
            int burstCapacity) {

        String tokensKey    = "rate_limit:" + identity + ".tokens";
        String timestampKey = "rate_limit:" + identity + ".timestamp";

        List<String> keys = List.of(tokensKey, timestampKey);
        String[] args = {
                String.valueOf(replenishRate),
                String.valueOf(burstCapacity),
                String.valueOf(Instant.now().getEpochSecond()),
                "1"
        };

        return redisTemplate.execute(RATE_LIMIT_SCRIPT, keys, args)
                .next()
                .cast(List.class)
                .flatMap(result -> {
                    List<Long> results = (List<Long>) result;
                    boolean allowed          = !results.isEmpty() && results.get(0) == 1L;
                    long    tokensRemaining  = results.size() > 1 ? results.get(1) : 0L;

                    exchange.getResponse().getHeaders().add("X-RateLimit-Limit",          String.valueOf(burstCapacity));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Remaining",      String.valueOf(tokensRemaining));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Replenish-Rate", String.valueOf(replenishRate));

                    if (allowed) {
                        return chain.filter(exchange);
                    }

                    log.warn("Rate limit exceeded — identity: {}, path: {}",
                            identity, exchange.getRequest().getPath().value());
                    return tooManyRequests(exchange, tokensRemaining);
                })
                .onErrorResume(e -> {
                    log.error("Redis rate limit error for [{}]: {}", identity, e.getMessage());
                    if (fallbackProperties.allowOnRedisFailure()) {
                        log.warn("Redis unavailable — allowing request through (fail-open)");
                        return chain.filter(exchange);
                    }
                    return tooManyRequests(exchange, 0L);
                });
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, long tokensRemaining) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("Retry-After", "1");

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status",    429,
                "error",     "Too Many Requests",
                "message",   "Rate limit exceeded. Please retry after 1 second.",
                "path",      exchange.getRequest().getPath().value()
        );

        try {
            byte[] bytes  = objectMapper.writeValueAsBytes(body);
            var    buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private boolean isExcludedPath(String path) {
        return fallbackProperties.excludedPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String extractClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return -99;
    }
}

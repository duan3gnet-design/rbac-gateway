package com.api.gateway.filter;

import com.api.gateway.admin.ratelimit.RateLimitConfigService;
import com.api.gateway.config.RateLimitProperties;
import com.auth.service.dto.ClaimsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Rate Limiting Filter — Token Bucket per User via Redis Lua script.
 *
 * <p>Order: -99 (chạy sau JwtAuthenticationFilter interceptor, trước Gateway proxy)</p>
 * <p>Claims đọc từ request attribute "jwt.claims" do JwtAuthenticationFilter đặt vào.</p>
 */
@Slf4j
@Component
@Order(-99)
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {

    private final StringRedisTemplate         redisTemplate;
    private final RateLimitProperties         fallbackProperties;
    private final RateLimitConfigService      rateLimitConfigService;
    private final ObjectMapper                objectMapper;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final DefaultRedisScript<List<Long>> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/rate_limit.lua"))
        );
        RATE_LIMIT_SCRIPT.setResultType((Class<List<Long>>) (Class<?>) List.class);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest  request  = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        if (isExcludedPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        ClaimsResponse claims = (ClaimsResponse) request.getAttribute("jwt.claims");
        String identity = (claims != null)
                ? "user:" + claims.username()
                : "ip:" + extractClientIp(request);

        boolean isUser   = identity.startsWith("user:");
        String  username = isUser ? identity.substring(5) : null;

        int[] cfg = isUser
                ? rateLimitConfigService.resolveConfig(username)
                : rateLimitConfigService.resolveGlobalDefault();

        checkRateLimit(request, response, chain, identity, cfg[0], cfg[1]);
    }

    private void checkRateLimit(HttpServletRequest request, HttpServletResponse response,
                                FilterChain chain, String identity,
                                int replenishRate, int burstCapacity) throws IOException, ServletException {
        String tokensKey    = "rate_limit:" + identity + ".tokens";
        String timestampKey = "rate_limit:" + identity + ".timestamp";

        List<String> keys = List.of(tokensKey, timestampKey);
        String[] args = {
                String.valueOf(replenishRate),
                String.valueOf(burstCapacity),
                String.valueOf(Instant.now().getEpochSecond()),
                "1"
        };

        try {
            List<Long> results = redisTemplate.execute(RATE_LIMIT_SCRIPT, keys, args);
            boolean allowed    = results != null && !results.isEmpty() && results.get(0) == 1L;
            long    tokensLeft = (results != null && results.size() > 1) ? results.get(1) : 0L;

            response.addHeader("X-RateLimit-Limit",          String.valueOf(burstCapacity));
            response.addHeader("X-RateLimit-Remaining",      String.valueOf(tokensLeft));
            response.addHeader("X-RateLimit-Replenish-Rate", String.valueOf(replenishRate));

            if (allowed) {
                chain.doFilter(request, response);
                return;
            }

            log.warn("Rate limit exceeded — identity: {}, path: {}", identity, request.getRequestURI());
            writeTooManyRequests(response, request.getRequestURI(), tokensLeft);

        } catch (Exception e) {
            log.error("Redis rate limit error for [{}]: {}", identity, e.getMessage());
            if (fallbackProperties.allowOnRedisFailure()) {
                log.warn("Redis unavailable — allowing request through (fail-open)");
                chain.doFilter(request, response);
            } else {
                writeTooManyRequests(response, request.getRequestURI(), 0L);
            }
        }
    }

    private void writeTooManyRequests(HttpServletResponse response, String path, long tokensRemaining)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.addHeader("Retry-After", "1");

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status",    429,
                "error",     "Too Many Requests",
                "message",   "Rate limit exceeded. Please retry after 1 second.",
                "path",      path
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private boolean isExcludedPath(String path) {
        return fallbackProperties.excludedPaths().stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

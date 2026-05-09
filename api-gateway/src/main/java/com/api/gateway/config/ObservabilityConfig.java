package com.api.gateway.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ObservabilityConfig — đăng ký custom metrics cho api-gateway.
 *
 * <p>Metrics được export qua /actuator/prometheus và scrape bởi Prometheus.</p>
 *
 * <p>Custom metrics:</p>
 * <ul>
 *   <li>{@code gateway.auth.failures}  — số lần JWT validation thất bại (401/403)</li>
 *   <li>{@code gateway.rate_limit.exceeded} — số lần rate limit bị vượt (429)</li>
 *   <li>{@code gateway.circuit_breaker.fallback} — số lần circuit breaker kích hoạt fallback</li>
 *   <li>{@code gateway.rbac.denied}    — số lần RBAC từ chối quyền truy cập</li>
 * </ul>
 */
@Configuration
public class ObservabilityConfig {

    // ── JWT / Auth ────────────────────────────────────────────────────────────

    @Bean
    public Counter jwtAuthFailureCounter(MeterRegistry registry) {
        return Counter.builder("gateway.auth.failures")
                .description("Number of JWT authentication failures (401)")
                .tag("reason", "invalid_token")
                .register(registry);
    }

    @Bean
    public Counter rbacDeniedCounter(MeterRegistry registry) {
        return Counter.builder("gateway.rbac.denied")
                .description("Number of RBAC permission denials (403)")
                .register(registry);
    }

    // ── Rate Limit ────────────────────────────────────────────────────────────

    @Bean
    public Counter rateLimitExceededCounter(MeterRegistry registry) {
        return Counter.builder("gateway.rate_limit.exceeded")
                .description("Number of requests rejected due to rate limiting (429)")
                .register(registry);
    }

    // ── Circuit Breaker ───────────────────────────────────────────────────────

    @Bean
    public Counter circuitBreakerFallbackCounter(MeterRegistry registry) {
        return Counter.builder("gateway.circuit_breaker.fallback")
                .description("Number of circuit breaker fallback activations")
                .register(registry);
    }

    // ── Request processing timer ──────────────────────────────────────────────

    @Bean
    public Timer gatewayRequestTimer(MeterRegistry registry) {
        return Timer.builder("gateway.request.duration")
                .description("End-to-end request processing time at gateway layer")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
    }
}

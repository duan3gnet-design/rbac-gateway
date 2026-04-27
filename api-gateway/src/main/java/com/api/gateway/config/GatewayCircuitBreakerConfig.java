package com.api.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit Breaker config cho Gateway MVC (blocking, không phải Reactive).
 * Dùng Resilience4JCircuitBreakerFactory thay vì ReactiveResilience4JCircuitBreakerFactory.
 */
@Configuration
public class GatewayCircuitBreakerConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> authServiceCBCustomizer() {
        return factory -> factory.configure(
                builder -> builder
                        .circuitBreakerConfig(CircuitBreakerConfig.custom()
                                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                                .slidingWindowSize(10)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(Duration.ofSeconds(10))
                                .permittedNumberOfCallsInHalfOpenState(3)
                                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                                .build())
                        .timeLimiterConfig(TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofSeconds(3))
                                .build()),
                "authServiceCB"
        );
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> resourceServiceCBCustomizer() {
        return factory -> factory.configure(
                builder -> builder
                        .circuitBreakerConfig(CircuitBreakerConfig.custom()
                                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                                .slidingWindowSize(10)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(Duration.ofSeconds(15))
                                .permittedNumberOfCallsInHalfOpenState(3)
                                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                                .build())
                        .timeLimiterConfig(TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofSeconds(5))
                                .build()),
                "resourceServiceCB"
        );
    }
}

package com.api.gateway.integration;

import com.api.gateway.config.PostgreSQLContainerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.Set;

/**
 * Base class cho tất cả integration test.
 *
 * <h3>Một Spring context duy nhất cho toàn suite</h3>
 * <p>Không dùng @DirtiesContext. Thay vào đó cleanDynamicDbState() chạy
 * trong @BeforeEach để reset DB/Redis về trạng thái seed trước mỗi test.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgreSQLContainerConfig.class)
abstract class AbstractIntegrationTest {

    protected static final com.github.tomakehurst.wiremock.WireMockServer wireMock =
            PostgreSQLContainerConfig.WIRE_MOCK;

    @LocalServerPort
    protected int gatewayPort;

    protected WebTestClient webClient;

    @Autowired
    protected CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterAll
    static void stopWireMock() {
        // WireMock dùng chung toàn suite, JVM shutdown hook sẽ dọn
    }

    @BeforeEach
    void baseSetUp() {
        wireMock.resetAll();

        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(CircuitBreaker::reset);

        // Flush Redis rate limit keys
        Set<String> rateLimitKeys = redisTemplate.keys("rate_limit:*");
        if (rateLimitKeys != null && !rateLimitKeys.isEmpty()) {
            redisTemplate.delete(rateLimitKeys);
        }

        cleanDynamicDbState();

        webClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Reset DB về trạng thái seed trước mỗi test.
     */
    private void cleanDynamicDbState() {
        // 1. Xóa route_permissions của dynamic routes
        jdbcTemplate.update("""
                DELETE FROM route_permissions
                WHERE route_id NOT IN (
                    'auth-login','auth-register','auth-refresh',
                    'auth-logout','auth-logout-all','resource-service'
                )
                """);

        // 2. Xóa dynamic gateway_routes
        jdbcTemplate.update("""
                DELETE FROM gateway_routes
                WHERE id NOT IN (
                    'auth-login','auth-register','auth-refresh',
                    'auth-logout','auth-logout-all','resource-service'
                )
                """);

        // 3. Reset seeded routes về trạng thái gốc
        String wireMockUri = "http://localhost:" + PostgreSQLContainerConfig.WIRE_MOCK.port();
        jdbcTemplate.update("""
                UPDATE gateway_routes
                SET uri        = ?,
                    enabled    = TRUE,
                    updated_at = now()
                WHERE id IN (
                    'auth-login','auth-register','auth-refresh',
                    'auth-logout','auth-logout-all','resource-service'
                )
                """, wireMockUri);

        // 4. Xóa per-user rate limit overrides
        jdbcTemplate.update("DELETE FROM rate_limit_config WHERE username IS NOT NULL");

        // 5. Reset global default
        jdbcTemplate.update("""
                UPDATE rate_limit_config
                SET replenish_rate = 5,
                    burst_capacity = 5,
                    enabled        = TRUE,
                    description    = 'Test global default',
                    updated_at     = now()
                WHERE username IS NULL
                """);
    }
}

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
import java.util.List;
import java.util.Set;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({PostgreSQLContainerConfig.class})
abstract class AbstractIntegrationTest {

    private static final List<String> CB_NAMES = List.of("authServiceCB", "resourceServiceCB");

    /** Routes được seed bởi init.sql — không bị cleanup giữa các test */
    private static final List<String> SEEDED_ROUTE_IDS = List.of("auth-login",
            "auth-register",
            "auth-refresh",
            "auth-logout",
            "auth-logout-all",
            "auth-validate",
            "auth-google",
            "oauth2-authorization",
            "oauth2-login-page",
            "resource-products",
            "resource-products-detail",
            "resource-orders-get",
            "resource-orders-get-detail",
            "resource-orders-create",
            "resource-orders-create-detail",
            "resource-orders-update",
            "resource-orders-delete",
            "resource-admin-users-get",
            "resource-admin-users-get-detail",
            "resource-admin-users-create",
            "resource-admin-users-update",
            "resource-admin-users-delete",
            "resource-profile-get",
            "resource-profile-update"
    );

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
    static void stopWireMock() {}

    @BeforeEach
    void baseSetUp() {
        wireMock.resetAll();

        // Reset CB về CLOSED trước mỗi test
        CB_NAMES.forEach(name ->
                circuitBreakerRegistry.find(name).ifPresent(CircuitBreaker::reset));

        // Flush Redis
        Set<String> rateLimitKeys = redisTemplate.keys("rate_limit:*");
        if (rateLimitKeys != null && !rateLimitKeys.isEmpty()) redisTemplate.delete(rateLimitKeys);
        Set<String> configCacheKeys = redisTemplate.keys("rl_cfg:*");
        if (configCacheKeys != null && !configCacheKeys.isEmpty()) redisTemplate.delete(configCacheKeys);

        cleanDynamicDbState();

        webClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    private void cleanDynamicDbState() {
        String seededIds = SEEDED_ROUTE_IDS.stream()
                .map(id -> "'" + id + "'")
                .reduce((a, b) -> a + "," + b)
                .orElse("''");

        // Xóa route_permissions của non-seeded routes
        jdbcTemplate.update(
                "DELETE FROM route_permissions WHERE route_id NOT IN (" + seededIds + ")");

        // Xóa non-seeded routes
        jdbcTemplate.update(
                "DELETE FROM gateway_routes WHERE id NOT IN (" + seededIds + ")");

        // Reset URI của seeded routes về WireMock (test có thể đã đổi URI)
        String wireMockUri = "http://localhost:" + PostgreSQLContainerConfig.WIRE_MOCK.port();
        jdbcTemplate.update(
                "UPDATE gateway_routes SET uri = ?, enabled = TRUE, updated_at = now()" +
                " WHERE id IN (" + seededIds + ")",
                wireMockUri);

        // Reset rate limit config
        jdbcTemplate.update("DELETE FROM rate_limit_config WHERE username IS NOT NULL");
        jdbcTemplate.update("""
                UPDATE rate_limit_config
                SET replenish_rate = 5, burst_capacity = 5,
                    enabled = TRUE, updated_at = now()
                WHERE username IS NULL
                """);
    }
}

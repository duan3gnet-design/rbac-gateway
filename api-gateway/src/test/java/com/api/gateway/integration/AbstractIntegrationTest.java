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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

/**
 * Base class cho tất cả integration test.
 *
 * WireMock được start static trong PostgreSQLContainerConfig trước khi
 * PostgreSQL container (và Spring context) khởi động — nhờ vậy URI trong
 * DB đã đúng ngay từ đầu, không cần refresh route cache.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgreSQLContainerConfig.class)
abstract class AbstractIntegrationTest {

    // Tham chiếu tới WireMock instance được start trong PostgreSQLContainerConfig
    protected static final com.github.tomakehurst.wiremock.WireMockServer wireMock =
            PostgreSQLContainerConfig.WIRE_MOCK;

    @LocalServerPort
    protected int gatewayPort;

    protected WebTestClient webClient;

    @Autowired
    protected CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterAll
    static void stopWireMock() {
        // Không stop — WireMock dùng chung toàn suite, JVM shutdown hook sẽ dọn
    }

    @BeforeEach
    void baseSetUp() {
        wireMock.resetAll();

        // Reset tất cả CB về CLOSED
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(CircuitBreaker::reset);

        webClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }
}

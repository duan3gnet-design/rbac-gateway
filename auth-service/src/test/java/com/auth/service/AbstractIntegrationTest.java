package com.auth.service;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class cho tất cả integration tests.
 *
 * Yêu cầu: Docker Desktop đang chạy.
 * Nếu Docker không available → test bị SKIP (không fail) nhờ @ExtendWith(DockerAvailableCondition.class).
 */
@Testcontainers
@ExtendWith(AbstractIntegrationTest.DockerAvailableCondition.class)
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("auth_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final RedisContainer redis =
            new RedisContainer("redis:7-alpine");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    /**
     * JUnit 5 ExecutionCondition: skip toàn bộ test class nếu Docker không available.
     * Thay vì throw exception → SKIP với message rõ ràng.
     */
    static class DockerAvailableCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            try {
                DockerClientFactory.instance().client();
                return ConditionEvaluationResult.enabled("Docker is available");
            } catch (Exception e) {
                return ConditionEvaluationResult.disabled(
                        "Docker not available — skipping integration tests. " +
                        "Start Docker Desktop and re-run. Reason: " + e.getMessage());
            }
        }
    }
}

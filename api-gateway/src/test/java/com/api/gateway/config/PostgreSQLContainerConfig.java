package com.api.gateway.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Testcontainers config:
 * 1. Start WireMock sớm để lấy port
 * 2. Generate init SQL với URI đúng WireMock port
 * 3. Start PostgreSQL container với init SQL đó (JDBC, không phải R2DBC)
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgreSQLContainerConfig {

    public static final WireMockServer WIRE_MOCK;

    @SuppressWarnings("rawtypes")
    public static final GenericContainer REDIS_CONTAINER;

    static {
        WIRE_MOCK = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        WIRE_MOCK.start();

        REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        REDIS_CONTAINER.start();

        System.setProperty("REDIS_HOST", REDIS_CONTAINER.getHost());
        System.setProperty("REDIS_PORT", String.valueOf(REDIS_CONTAINER.getMappedPort(6379)));
    }

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() throws IOException {
        String wireMockUri = "http://localhost:" + WIRE_MOCK.port();

        String initSql = buildInitSql(wireMockUri);
        Path tmpSql = Files.createTempFile("gateway-test-init-", ".sql");
        Files.writeString(tmpSql, initSql, StandardCharsets.UTF_8);

        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("gateway_test")
                .withUsername("test")
                .withPassword("test")
                .withCopyToContainer(
                        MountableFile.forHostPath(tmpSql),
                        "/docker-entrypoint-initdb.d/init.sql"
                );
    }

    private String buildInitSql(String wireMockUri) {
        return """
                CREATE TABLE IF NOT EXISTS gateway_routes (
                    id           VARCHAR(100) PRIMARY KEY,
                    uri          VARCHAR(500) NOT NULL,
                    predicates   TEXT         NOT NULL DEFAULT '[]',
                    filters      TEXT         NOT NULL DEFAULT '[]',
                    route_order  INT          NOT NULL DEFAULT 0,
                    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
                    created_at   TIMESTAMPTZ  DEFAULT now(),
                    updated_at   TIMESTAMPTZ  DEFAULT now()
                );

                CREATE TABLE IF NOT EXISTS rate_limit_config (
                    id              BIGSERIAL    PRIMARY KEY,
                    username        VARCHAR(255) NULL UNIQUE,
                    replenish_rate  INT          NOT NULL,
                    burst_capacity  INT          NOT NULL,
                    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
                    description     VARCHAR(500) NULL,
                    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                    CONSTRAINT chk_rates_positive CHECK (replenish_rate > 0 AND burst_capacity > 0),
                    CONSTRAINT chk_burst_gte_rate CHECK (burst_capacity >= replenish_rate)
                );

                CREATE TABLE IF NOT EXISTS resources (
                    id   BIGSERIAL    PRIMARY KEY,
                    name VARCHAR(100) NOT NULL UNIQUE
                );

                CREATE TABLE IF NOT EXISTS actions (
                    id   BIGSERIAL   PRIMARY KEY,
                    name VARCHAR(50) NOT NULL UNIQUE
                );

                CREATE TABLE IF NOT EXISTS permissions (
                    id          BIGSERIAL    PRIMARY KEY,
                    role        VARCHAR(100) NOT NULL,
                    resource_id BIGINT       NOT NULL REFERENCES resources(id),
                    action_id   BIGINT       NOT NULL REFERENCES actions(id),
                    UNIQUE (role, resource_id, action_id)
                );

                CREATE TABLE IF NOT EXISTS route_permissions (
                    route_id      VARCHAR(100) NOT NULL REFERENCES gateway_routes(id) ON DELETE CASCADE,
                    permission_id BIGINT       NOT NULL REFERENCES permissions(id)    ON DELETE CASCADE,
                    PRIMARY KEY (route_id, permission_id)
                );

                INSERT INTO rate_limit_config (username, replenish_rate, burst_capacity, description)
                VALUES (NULL, 5, 5, 'Test global default') ON CONFLICT DO NOTHING;

                INSERT INTO resources (name) VALUES ('products'), ('orders'), ('users'), ('profile')
                ON CONFLICT DO NOTHING;

                INSERT INTO actions (name) VALUES ('READ'), ('CREATE'), ('UPDATE'), ('DELETE')
                ON CONFLICT DO NOTHING;

                INSERT INTO permissions (role, resource_id, action_id)
                SELECT 'ROLE_ADMIN', r.id, a.id FROM resources r, actions a ON CONFLICT DO NOTHING;

                INSERT INTO permissions (role, resource_id, action_id)
                VALUES
                    ('ROLE_USER', (SELECT id FROM resources WHERE name='products'), (SELECT id FROM actions WHERE name='READ')),
                    ('ROLE_USER', (SELECT id FROM resources WHERE name='orders'),   (SELECT id FROM actions WHERE name='READ')),
                    ('ROLE_USER', (SELECT id FROM resources WHERE name='orders'),   (SELECT id FROM actions WHERE name='CREATE')),
                    ('ROLE_USER', (SELECT id FROM resources WHERE name='profile'),  (SELECT id FROM actions WHERE name='READ')),
                    ('ROLE_USER', (SELECT id FROM resources WHERE name='profile'),  (SELECT id FROM actions WHERE name='UPDATE'))
                ON CONFLICT DO NOTHING;

                INSERT INTO gateway_routes (id, uri, predicates, filters, route_order) VALUES

                ('auth-login', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/auth/login"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 1),

                ('auth-register', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/auth/register"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 2),

                ('auth-refresh', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/auth/refresh"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 3),

                ('auth-logout', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/auth/logout"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 4),

                ('auth-logout-all', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/auth/logout-all"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 5),

                ('resource-service', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/**"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 6)

                ON CONFLICT DO NOTHING;

                INSERT INTO route_permissions (route_id, permission_id)
                SELECT 'resource-service', p.id
                FROM permissions p WHERE p.role = 'ROLE_USER' ON CONFLICT DO NOTHING;
                """.formatted(wireMockUri);
    }
}

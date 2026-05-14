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

@TestConfiguration(proxyBeanMethods = false)
public class PostgreSQLContainerConfig {

    public static final WireMockServer WIRE_MOCK;

    @SuppressWarnings("rawtypes")
    public static final GenericContainer REDIS_CONTAINER;

    static {
        WIRE_MOCK = new WireMockServer(WireMockConfiguration
                .wireMockConfig()
                .dynamicPort()
                .http2PlainDisabled(true));
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
        String initSql     = buildInitSql(wireMockUri);

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
                -- ── Tables ───────────────────────────────────────────────────────────

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

                -- ── View: route_permission_rules ──────────────────────────────────────
                --
                -- Cú pháp jsonb đúng trong PostgreSQL:
                --   elem->'args'->>'pattern'  (-> trả jsonb, ->> trả text)
                -- KHÔNG dùng:
                --   elem->>'args'->>'pattern'  (->> trả text, text không có -> operator)
                --
                -- http_method NULL = route không có Method predicate = match mọi method.

                CREATE OR REPLACE VIEW route_permission_rules AS
                SELECT
                    gr.id AS route_id,
                    (
                        SELECT elem->'args'->>'pattern'
                        FROM jsonb_array_elements(gr.predicates::jsonb) AS elem
                        WHERE elem->>'name' = 'Path'
                        LIMIT 1
                    ) AS path_pattern,
                    upper(
                        (
                            SELECT elem->'args'->>'methods'
                            FROM jsonb_array_elements(gr.predicates::jsonb) AS elem
                            WHERE elem->>'name' = 'Method'
                            LIMIT 1
                        )
                    ) AS http_method,
                    lower(r.name) || ':' || upper(a.name) AS permission_code
                FROM gateway_routes gr
                JOIN route_permissions rp ON rp.route_id  = gr.id
                JOIN permissions       p  ON p.id         = rp.permission_id
                JOIN resources         r  ON r.id         = p.resource_id
                JOIN actions           a  ON a.id         = p.action_id
                WHERE gr.enabled = TRUE;

                -- ── Seed: resources + actions ────────────────────────────────────────

                INSERT INTO resources (name)
                VALUES ('products'), ('orders'), ('users'), ('profile'), ('admin'), ('auth')
                ON CONFLICT (name) DO NOTHING;

                INSERT INTO actions (name)
                VALUES ('READ'), ('CREATE'), ('UPDATE'), ('DELETE'), ('LOGOUT_ALL')
                ON CONFLICT (name) DO NOTHING;

                -- ── Seed: permissions ────────────────────────────────────────────────

                INSERT INTO permissions (role, resource_id, action_id)
                SELECT 'ROLE_ADMIN', r.id, a.id
                FROM resources r, actions a
                WHERE r.name IN ('products','orders','users','profile','admin')
                  AND a.name IN ('READ','CREATE','UPDATE','DELETE')
                ON CONFLICT DO NOTHING;

                INSERT INTO permissions (role, resource_id, action_id)
                VALUES
                    ('ROLE_USER', (SELECT id FROM resources WHERE name='products'), (SELECT id FROM actions WHERE name='READ')),
                    ('ROLE_USER', (SELECT id FROM resources WHERE name='orders'),   (SELECT id FROM actions WHERE name='READ')),
                    ('ROLE_USER', (SELECT id FROM resources WHERE name='orders'),   (SELECT id FROM actions WHERE name='CREATE')),
                    ('ROLE_USER', (SELECT id FROM resources WHERE name='profile'),  (SELECT id FROM actions WHERE name='READ')),
                    ('ROLE_USER', (SELECT id FROM resources WHERE name='profile'),  (SELECT id FROM actions WHERE name='UPDATE')),
                    ('ROLE_USER', (SELECT id FROM resources WHERE name='auth'),     (SELECT id FROM actions WHERE name='LOGOUT_ALL'))
                ON CONFLICT DO NOTHING;

                -- ── Seed: gateway_routes ─────────────────────────────────────────────

                INSERT INTO gateway_routes (id, uri, predicates, filters, route_order) VALUES

                ('auth-login', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/auth/login"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"POST","backoff.firstBackoff":"100ms","backoff.maxBackoff":"500ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 10),
                
                ('auth-register', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/auth/register"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 20),
                
                ('auth-refresh', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/auth/refresh"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 30),
                
                ('auth-logout', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/auth/logout"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 40),
                
                ('auth-logout-all', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/auth/logout-all"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 50),
                
                ('auth-validate', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/auth/validate"}},{"name":"Method","args":{"methods":"GET"}}]',
                 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"500ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 60),
                
                ('auth-google', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/auth/google"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 70),
                
                ('oauth2-authorization', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/oauth2/**"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 80),
                
                ('oauth2-login-page', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/login/oauth2/**"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
                 90),
                
                -- ─── Resource Service routes — tách chi tiết theo path/method ────────────────
                --
                -- Lý do tách thay vì dùng 1 route catch-all "/api/resources/**":
                --   View route_permission_rules join route_permissions để build rules
                --   (path_pattern, http_method, permission_code). Nếu dùng 1 route với nhiều
                --   permissions, checker không phân biệt được path/method nào cần permission gì
                --   → tất cả permissions đều valid cho tất cả paths trong route.
                --
                --   Tách thành routes chi tiết → mỗi route có đúng 1 permission tương ứng
                --   → view sinh ra rules chính xác như cũ.
                
                -- Products (ROLE_USER: products:READ)
                ('resource-products', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/products"}},{"name":"Method","args":{"methods":"GET"}}]',
                 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 200),
                
                ('resource-products-detail', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/products/**"}},{"name":"Method","args":{"methods":"GET"}}]',
                 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 210),
                
                -- Orders (ROLE_USER: orders:READ, orders:CREATE, orders:UPDATE, orders:DELETE)
                ('resource-orders-get', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/orders"}},{"name":"Method","args":{"methods":"GET"}}]',
                 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 220),
                
                ('resource-orders-get-detail', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/orders/**"}},{"name":"Method","args":{"methods":"GET"}}]',
                 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 230),
                
                ('resource-orders-create', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/orders"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 240),
                
                ('resource-orders-create-detail', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/orders/**"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 250),
                
                ('resource-orders-update', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/orders/**"}},{"name":"Method","args":{"methods":"PUT"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 260),
                
                ('resource-orders-delete', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/orders/**"}},{"name":"Method","args":{"methods":"DELETE"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 270),
                
                -- Admin Users (ROLE_ADMIN: users:READ, users:CREATE, users:UPDATE, users:DELETE)
                ('resource-admin-users-get', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users"}},{"name":"Method","args":{"methods":"GET"}}]',
                 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 300),
                
                ('resource-admin-users-get-detail', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users/**"}},{"name":"Method","args":{"methods":"GET"}}]',
                 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 310),
                
                ('resource-admin-users-create', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users/**"}},{"name":"Method","args":{"methods":"POST"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 320),
                
                ('resource-admin-users-update', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users/**"}},{"name":"Method","args":{"methods":"PUT"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 330),
                
                ('resource-admin-users-delete', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users/**"}},{"name":"Method","args":{"methods":"DELETE"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 340),
                
                -- Profile (ROLE_USER: profile:READ, profile:UPDATE)
                ('resource-profile-get', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/profile/**"}},{"name":"Method","args":{"methods":"GET"}}]',
                 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 400),
                
                ('resource-profile-update', '%1$s',
                 '[{"name":"Path","args":{"pattern":"/api/resources/profile/**"}},{"name":"Method","args":{"methods":"PUT"}}]',
                 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
                 410)

                ON CONFLICT DO NOTHING;

                -- ── Seed: route_permissions ──────────────────────────────────────────

                -- Products
                INSERT INTO route_permissions (route_id, permission_id)
                SELECT r.id, p.id FROM (VALUES
                    ('resource-products'),
                    ('resource-products-detail')
                ) AS r(id)
                CROSS JOIN (
                    SELECT p.id FROM permissions p
                    JOIN resources res ON res.id = p.resource_id
                    JOIN actions   a   ON a.id   = p.action_id
                    WHERE res.name = 'products' AND a.name = 'READ' AND p.role = 'ROLE_USER'
                ) AS p
                ON CONFLICT DO NOTHING;
                
                -- Orders — GET
                INSERT INTO route_permissions (route_id, permission_id)
                SELECT r.id, p.id FROM (VALUES
                    ('resource-orders-get'),
                    ('resource-orders-get-detail')
                ) AS r(id)
                CROSS JOIN (
                    SELECT p.id FROM permissions p
                    JOIN resources res ON res.id = p.resource_id
                    JOIN actions   a   ON a.id   = p.action_id
                    WHERE res.name = 'orders' AND a.name = 'READ' AND p.role = 'ROLE_USER'
                ) AS p
                ON CONFLICT DO NOTHING;
                
                -- Orders — POST (CREATE)
                INSERT INTO route_permissions (route_id, permission_id)
                SELECT r.id, p.id FROM (VALUES
                    ('resource-orders-create'),
                    ('resource-orders-create-detail')
                ) AS r(id)
                CROSS JOIN (
                    SELECT p.id FROM permissions p
                    JOIN resources res ON res.id = p.resource_id
                    JOIN actions   a   ON a.id   = p.action_id
                    WHERE res.name = 'orders' AND a.name = 'CREATE' AND p.role = 'ROLE_USER'
                ) AS p
                ON CONFLICT DO NOTHING;
                
                -- Orders — PUT (UPDATE)
                INSERT INTO route_permissions (route_id, permission_id)
                SELECT 'resource-orders-update', p.id
                FROM permissions p
                JOIN resources res ON res.id = p.resource_id
                JOIN actions   a   ON a.id   = p.action_id
                WHERE res.name = 'orders' AND a.name = 'UPDATE' AND p.role = 'ROLE_USER'
                ON CONFLICT DO NOTHING;
                
                -- Orders — DELETE
                INSERT INTO route_permissions (route_id, permission_id)
                SELECT 'resource-orders-delete', p.id
                FROM permissions p
                JOIN resources res ON res.id = p.resource_id
                JOIN actions   a   ON a.id   = p.action_id
                WHERE res.name = 'orders' AND a.name = 'DELETE' AND p.role = 'ROLE_USER'
                ON CONFLICT DO NOTHING;
                
                -- Admin Users — GET
                INSERT INTO route_permissions (route_id, permission_id)
                SELECT r.id, p.id FROM (VALUES
                    ('resource-admin-users-get'),
                    ('resource-admin-users-get-detail')
                ) AS r(id)
                CROSS JOIN (
                    SELECT p.id FROM permissions p
                    JOIN resources res ON res.id = p.resource_id
                    JOIN actions   a   ON a.id   = p.action_id
                    WHERE res.name = 'users' AND a.name = 'READ' AND p.role = 'ROLE_ADMIN'
                ) AS p
                ON CONFLICT DO NOTHING;
                
                -- Admin Users — POST (CREATE)
                INSERT INTO route_permissions (route_id, permission_id)
                SELECT 'resource-admin-users-create', p.id
                FROM permissions p
                JOIN resources res ON res.id = p.resource_id
                JOIN actions   a   ON a.id   = p.action_id
                WHERE res.name = 'users' AND a.name = 'CREATE' AND p.role = 'ROLE_ADMIN'
                ON CONFLICT DO NOTHING;
                
                -- Admin Users — PUT (UPDATE)
                INSERT INTO route_permissions (route_id, permission_id)
                SELECT 'resource-admin-users-update', p.id
                FROM permissions p
                JOIN resources res ON res.id = p.resource_id
                JOIN actions   a   ON a.id   = p.action_id
                WHERE res.name = 'users' AND a.name = 'UPDATE' AND p.role = 'ROLE_ADMIN'
                ON CONFLICT DO NOTHING;
                
                -- Admin Users — DELETE
                INSERT INTO route_permissions (route_id, permission_id)
                SELECT 'resource-admin-users-delete', p.id
                FROM permissions p
                JOIN resources res ON res.id = p.resource_id
                JOIN actions   a   ON a.id   = p.action_id
                WHERE res.name = 'users' AND a.name = 'DELETE' AND p.role = 'ROLE_ADMIN'
                ON CONFLICT DO NOTHING;
                
                -- Profile — GET (READ)
                INSERT INTO route_permissions (route_id, permission_id)
                SELECT 'resource-profile-get', p.id
                FROM permissions p
                JOIN resources res ON res.id = p.resource_id
                JOIN actions   a   ON a.id   = p.action_id
                WHERE res.name = 'profile' AND a.name = 'READ' AND p.role = 'ROLE_USER'
                ON CONFLICT DO NOTHING;
                
                -- Profile — PUT (UPDATE)
                INSERT INTO route_permissions (route_id, permission_id)
                SELECT 'resource-profile-update', p.id
                FROM permissions p
                JOIN resources res ON res.id = p.resource_id
                JOIN actions   a   ON a.id   = p.action_id
                WHERE res.name = 'profile' AND a.name = 'UPDATE' AND p.role = 'ROLE_USER'
                ON CONFLICT DO NOTHING;

                -- auth-logout-all → ROLE_USER auth:LOGOUT_ALL
                
                INSERT INTO route_permissions (route_id, permission_id)
                SELECT 'auth-logout-all', p.id
                FROM permissions p
                JOIN resources r ON r.id = p.resource_id
                JOIN actions   a ON a.id = p.action_id
                WHERE r.name = 'auth' AND a.name = 'LOGOUT_ALL' AND p.role = 'ROLE_USER'
                ON CONFLICT DO NOTHING;

                -- ── Seed: rate_limit_config ──────────────────────────────────────────

                INSERT INTO rate_limit_config (username, replenish_rate, burst_capacity, description)
                VALUES (NULL, 5, 5, 'Test global default')
                ON CONFLICT DO NOTHING;
                
                SELECT route_id, path_pattern, http_method, permission_code FROM route_permission_rules;
                """.formatted(wireMockUri);
    }
}

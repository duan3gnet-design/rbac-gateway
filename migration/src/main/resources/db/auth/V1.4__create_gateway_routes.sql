-- V1.4__create_gateway_routes.sql

CREATE TABLE gateway_routes (
    id           VARCHAR(100) PRIMARY KEY,
    uri          VARCHAR(500) NOT NULL,
    predicates   TEXT         NOT NULL DEFAULT '[]',
    filters      TEXT         NOT NULL DEFAULT '[]',
    route_order  INT          NOT NULL DEFAULT 0,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- ─── Auth Service routes ──────────────────────────────────────────────────────

INSERT INTO gateway_routes (id, uri, predicates, filters, route_order) VALUES

('auth-login', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/login"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"POST","backoff.firstBackoff":"100ms","backoff.maxBackoff":"500ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 10),

('auth-register', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/register"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 20),

('auth-refresh', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/refresh"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 30),

('auth-logout', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/logout"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 40),

('auth-logout-all', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/logout-all"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 50),

('auth-validate', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/validate"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"500ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 60),

('auth-google', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/google"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 70),

('oauth2-authorization', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/oauth2/**"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 80),

('oauth2-login-page', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/login/oauth2/**"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
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
('resource-products', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/products"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 200),

('resource-products-detail', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/products/**"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 210),

-- Orders (ROLE_USER: orders:READ, orders:CREATE, orders:UPDATE, orders:DELETE)
('resource-orders-get', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/orders"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 220),

('resource-orders-get-detail', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/orders/**"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 230),

('resource-orders-create', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/orders"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 240),

('resource-orders-create-detail', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/orders/**"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 250),

('resource-orders-update', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/orders/**"}},{"name":"Method","args":{"methods":"PUT"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 260),

('resource-orders-delete', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/orders/**"}},{"name":"Method","args":{"methods":"DELETE"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 270),

-- Admin Users (ROLE_ADMIN: users:READ, users:CREATE, users:UPDATE, users:DELETE)
('resource-admin-users-get', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 300),

('resource-admin-users-get-detail', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users/**"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 310),

('resource-admin-users-create', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users/**"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 320),

('resource-admin-users-update', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users/**"}},{"name":"Method","args":{"methods":"PUT"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 330),

('resource-admin-users-delete', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users/**"}},{"name":"Method","args":{"methods":"DELETE"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 340),

-- Profile (ROLE_USER: profile:READ, profile:UPDATE)
('resource-profile-get', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/profile/**"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 400),

('resource-profile-update', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/profile/**"}},{"name":"Method","args":{"methods":"PUT"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 410);

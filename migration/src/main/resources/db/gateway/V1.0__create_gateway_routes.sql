-- V1.0__create_gateway_routes.sql (rbac_gateway database)

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

('auth-login', 'lb://rbac-auth',
 '[{"name":"Path","args":{"pattern":"/api/auth/login"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"POST","backoff.firstBackoff":"100ms","backoff.maxBackoff":"500ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 10),

('auth-register', 'lb://rbac-auth',
 '[{"name":"Path","args":{"pattern":"/api/auth/register"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 20),

('auth-refresh', 'lb://rbac-auth',
 '[{"name":"Path","args":{"pattern":"/api/auth/refresh"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 30),

('auth-logout', 'lb://rbac-auth',
 '[{"name":"Path","args":{"pattern":"/api/auth/logout"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 40),

('auth-logout-all', 'lb://rbac-auth',
 '[{"name":"Path","args":{"pattern":"/api/auth/logout-all"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 50),

('auth-validate', 'lb://rbac-auth',
 '[{"name":"Path","args":{"pattern":"/api/auth/validate"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"500ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 60),

('auth-google', 'lb://rbac-auth',
 '[{"name":"Path","args":{"pattern":"/api/auth/google"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 70),

('oauth2-authorization', 'lb://rbac-auth',
 '[{"name":"Path","args":{"pattern":"/oauth2/**"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 80),

('oauth2-login-page', 'lb://rbac-auth',
 '[{"name":"Path","args":{"pattern":"/login/oauth2/**"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 90),

-- ─── Resource Service routes ──────────────────────────────────────────────────

('resource-products', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/products"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 200),

('resource-products-detail', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/products/**"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 210),

('resource-orders-get', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/orders"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 220),

('resource-orders-get-detail', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/orders/**"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 230),

('resource-orders-create', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/orders"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 240),

('resource-orders-create-detail', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/orders/**"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 250),

('resource-orders-update', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/orders/**"}},{"name":"Method","args":{"methods":"PUT"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 260),

('resource-orders-delete', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/orders/**"}},{"name":"Method","args":{"methods":"DELETE"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 270),

('resource-admin-users-get', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 300),

('resource-admin-users-get-detail', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users/**"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 310),

('resource-admin-users-create', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users/**"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 320),

('resource-admin-users-update', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users/**"}},{"name":"Method","args":{"methods":"PUT"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 330),

('resource-admin-users-delete', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/admin/users/**"}},{"name":"Method","args":{"methods":"DELETE"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 340),

('resource-profile-get', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/profile/**"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 400),

('resource-profile-update', 'lb://rbac-resource',
 '[{"name":"Path","args":{"pattern":"/api/resources/profile/**"}},{"name":"Method","args":{"methods":"PUT"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 410);

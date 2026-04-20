-- V1.4__create_gateway_routes.sql
-- Bảng lưu dynamic routes cho API Gateway
-- filters và predicates lưu dạng JSON text

CREATE TABLE gateway_routes (
    id           VARCHAR(100) PRIMARY KEY,         -- route id, ví dụ: "auth-login"
    uri          VARCHAR(500) NOT NULL,             -- upstream URI, ví dụ: "http://localhost:8081"
    predicates   TEXT         NOT NULL DEFAULT '[]', -- JSON array: [{"name":"Path","args":{"pattern":"/api/auth/login"}}, ...]
    filters      TEXT         NOT NULL DEFAULT '[]', -- JSON array: [{"name":"CircuitBreaker","args":{...}}, ...]
    route_order  INT          NOT NULL DEFAULT 0,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- ─── Seed: routes auth-service ───────────────────────────────────────────────

INSERT INTO gateway_routes (id, uri, predicates, filters, route_order) VALUES

('auth-login', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/login"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"POST","backoff.firstBackoff":"100ms","backoff.maxBackoff":"500ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 1),

('auth-register', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/register"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 2),

('auth-refresh', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/refresh"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 3),

('auth-logout', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/logout"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 4),

('auth-logout-all', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/logout-all"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 5),

('auth-validate', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/validate"}},{"name":"Method","args":{"methods":"GET"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"500ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 6),

('auth-google', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/api/auth/google"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 7),

('oauth2-authorization', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/oauth2/**"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 8),

('oauth2-login-page', 'http://localhost:8081',
 '[{"name":"Path","args":{"pattern":"/login/oauth2/**"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 9),

-- ─── Seed: routes resource-service ──────────────────────────────────────────

('resource-service', 'http://localhost:8082',
 '[{"name":"Path","args":{"pattern":"/api/resources/**"}}]',
 '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET","backoff.firstBackoff":"100ms","backoff.maxBackoff":"1000ms","backoff.factor":"2"}},{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 10);

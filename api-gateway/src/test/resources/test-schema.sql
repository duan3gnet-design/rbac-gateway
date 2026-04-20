-- ============================================================
-- Schema
-- ============================================================
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
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(255)    NULL UNIQUE,
    replenish_rate  INT             NOT NULL,
    burst_capacity  INT             NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    description     VARCHAR(500)    NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_rates_positive CHECK (replenish_rate > 0 AND burst_capacity > 0),
    CONSTRAINT chk_burst_gte_rate CHECK (burst_capacity >= replenish_rate)
);

CREATE INDEX IF NOT EXISTS idx_rlc_username ON rate_limit_config(username) WHERE username IS NOT NULL;

-- ============================================================
-- Seed — uri sẽ được test override qua @DynamicPropertySource
-- bằng cách UPDATE sau khi WireMock port được biết.
-- ============================================================
INSERT INTO gateway_routes (id, uri, predicates, filters, route_order) VALUES

('auth-login', 'http://localhost:0',
 '[{"name":"Path","args":{"pattern":"/api/auth/login"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 1),

('auth-register', 'http://localhost:0',
 '[{"name":"Path","args":{"pattern":"/api/auth/register"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 2),

('auth-refresh', 'http://localhost:0',
 '[{"name":"Path","args":{"pattern":"/api/auth/refresh"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 3),

('auth-logout', 'http://localhost:0',
 '[{"name":"Path","args":{"pattern":"/api/auth/logout"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 4),

('auth-logout-all', 'http://localhost:0',
 '[{"name":"Path","args":{"pattern":"/api/auth/logout-all"}},{"name":"Method","args":{"methods":"POST"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"authServiceCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
 5),

('resource-service', 'http://localhost:0',
 '[{"name":"Path","args":{"pattern":"/api/resources/**"}}]',
 '[{"name":"CircuitBreaker","args":{"name":"resourceServiceCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
 6);

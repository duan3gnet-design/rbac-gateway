-- V1.2__create_rate_limit_config.sql (rbac_gateway database)

CREATE TABLE rate_limit_config (
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

CREATE INDEX idx_rlc_username ON rate_limit_config(username) WHERE username IS NOT NULL;

INSERT INTO rate_limit_config (username, replenish_rate, burst_capacity, description)
VALUES (NULL, 20, 40, 'Global default — applies to all authenticated users without a specific override');

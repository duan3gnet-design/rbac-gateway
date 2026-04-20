-- V1.6__create_rate_limit_config.sql
-- Bảng cấu hình rate limit: global default + override per user
--
-- Lookup priority (trong RateLimitFilter):
--   1. username IS NOT NULL AND username = :currentUser  → per-user override
--   2. username IS NULL AND is_default = TRUE            → global default
--   3. fallback về application.yml (rate-limit.replenish-rate / burst-capacity)

CREATE TABLE rate_limit_config (
    id              BIGSERIAL       PRIMARY KEY,
    -- NULL  = global default row (is_default phải TRUE)
    -- value = username của user được override riêng
    username        VARCHAR(255)    NULL UNIQUE,
    replenish_rate  INT             NOT NULL,   -- tokens/giây được nạp lại
    burst_capacity  INT             NOT NULL,   -- max tokens trong bucket
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    description     VARCHAR(500)    NULL,       -- ghi chú cho admin UI
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_rates_positive CHECK (replenish_rate > 0 AND burst_capacity > 0),
    CONSTRAINT chk_burst_gte_rate CHECK (burst_capacity >= replenish_rate)
);

CREATE INDEX idx_rlc_username ON rate_limit_config(username) WHERE username IS NOT NULL;

-- Global default: áp dụng cho tất cả user không có override riêng
INSERT INTO rate_limit_config (username, replenish_rate, burst_capacity, description)
VALUES (NULL, 20, 40, 'Global default — applies to all authenticated users without a specific override');

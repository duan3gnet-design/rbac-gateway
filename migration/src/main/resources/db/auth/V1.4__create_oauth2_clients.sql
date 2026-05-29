-- V1.4__create_oauth2_clients.sql
-- OAuth2 client registry for client_credentials grant type.
-- Used by services (e.g. k8s operators, CI pipelines) that authenticate
-- as a "machine user" without an interactive login.
--
-- client_id   = username used in AuthenticationManager
-- client_secret = BCrypt-hashed password (same encoder as user passwords)
-- scopes      = comma-separated list, e.g. "openid,profile"
-- granted_types = comma-separated allowed grant types

CREATE TABLE oauth2_clients (
    id              BIGSERIAL PRIMARY KEY,
    client_id       VARCHAR(128) NOT NULL UNIQUE,
    client_secret   VARCHAR(256) NOT NULL,   -- BCrypt hash
    scopes          VARCHAR(512) NOT NULL DEFAULT 'openid',
    granted_types   VARCHAR(256) NOT NULL DEFAULT 'client_credentials',
    description     VARCHAR(512),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Add index for fast lookup during authentication
CREATE INDEX idx_oauth2_clients_client_id ON oauth2_clients(client_id);

-- Seed: a default k8s service-account client
-- Password = 'changeme-k8s-secret' (BCrypt) — CHANGE in production via ENV!
INSERT INTO oauth2_clients (client_id, client_secret, scopes, granted_types, description)
VALUES (
    'k8s-service-account',
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
    'openid',
    'client_credentials',
    'Default Kubernetes service account client – rotate secret before production'
);

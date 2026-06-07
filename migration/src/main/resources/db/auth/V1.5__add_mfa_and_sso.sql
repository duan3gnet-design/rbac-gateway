-- V1.5__add_mfa_and_sso.sql
-- MFA (Multi-Factor Authentication) and SSO support

-- ── Users: add MFA flag and SSO columns ────────────────────────────────────
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS mfa_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS sso_provider_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS sso_subject   VARCHAR(512);

-- Index for SSO user lookup
CREATE INDEX IF NOT EXISTS idx_users_sso ON public.users(sso_provider_id, sso_subject)
    WHERE sso_provider_id IS NOT NULL;

-- ── MFA Secrets (TOTP) ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.mfa_secrets
(
    id         BIGSERIAL    NOT NULL,
    user_id    BIGINT       NOT NULL,
    secret     VARCHAR(256) NOT NULL,
    verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT mfa_secrets_pkey PRIMARY KEY (id),
    CONSTRAINT mfa_secrets_user_id_fkey FOREIGN KEY (user_id)
        REFERENCES public.users (id) ON DELETE CASCADE,
    CONSTRAINT mfa_secrets_user_id_key UNIQUE (user_id)
);

-- ── MFA Backup Codes ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.mfa_backup_codes
(
    id         BIGSERIAL    NOT NULL,
    user_id    BIGINT       NOT NULL,
    code_hash  VARCHAR(256) NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT mfa_backup_codes_pkey PRIMARY KEY (id),
    CONSTRAINT mfa_backup_codes_user_id_fkey FOREIGN KEY (user_id)
        REFERENCES public.users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mfa_backup_codes_user_unused
    ON public.mfa_backup_codes(user_id) WHERE used = FALSE;

-- ── SSO Providers ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.sso_providers
(
    id            BIGSERIAL    NOT NULL,
    provider_id   VARCHAR(128) NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    type          VARCHAR(32)  NOT NULL DEFAULT 'oidc',
    issuer_uri    VARCHAR(512),
    client_id     VARCHAR(256),
    client_secret VARCHAR(512),
    scopes        VARCHAR(512)  NOT NULL DEFAULT 'openid,profile,email',
    default_roles VARCHAR(512)  NOT NULL DEFAULT 'ROLE_USER',
    enabled       BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT sso_providers_pkey        PRIMARY KEY (id),
    CONSTRAINT sso_providers_provider_id_key UNIQUE (provider_id)
);

-- Example: seed a disabled placeholder — enable & fill secrets via admin API
INSERT INTO public.sso_providers (provider_id, display_name, type, issuer_uri, client_id, client_secret, scopes, default_roles, enabled)
VALUES (
    'azure-ad',
    'Microsoft Azure AD',
    'oidc',
    'https://login.microsoftonline.com/{tenant-id}/v2.0',
    'your-azure-client-id',
    'your-azure-client-secret',
    'openid,profile,email',
    'ROLE_USER',
    FALSE
) ON CONFLICT (provider_id) DO NOTHING;

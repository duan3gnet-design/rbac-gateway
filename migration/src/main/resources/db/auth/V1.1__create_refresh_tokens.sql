CREATE TABLE refresh_tokens (
                                id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                token       VARCHAR(255) NOT NULL UNIQUE,
                                username    VARCHAR(255) NOT NULL,
                                expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
                                revoked     BOOLEAN NOT NULL DEFAULT FALSE,
                                created_at  TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_username ON refresh_tokens(username);
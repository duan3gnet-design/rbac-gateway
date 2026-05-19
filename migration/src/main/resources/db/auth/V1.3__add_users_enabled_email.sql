-- V1.3: Thêm cột enabled và email vào bảng users
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS email   VARCHAR(255) NULL;

-- Index để tìm theo email nhanh
CREATE INDEX IF NOT EXISTS idx_users_email ON public.users(email);

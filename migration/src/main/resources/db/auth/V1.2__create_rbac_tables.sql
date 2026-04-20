-- V3__create_rbac_tables.sql

-- Bảng resource (products, orders, users...)
CREATE TABLE resources (
                           id      BIGSERIAL PRIMARY KEY,
                           name    VARCHAR(100) NOT NULL UNIQUE  -- "products", "orders", "users"
);

-- Bảng action (GET, POST, PUT, DELETE)
CREATE TABLE actions (
                         id      BIGSERIAL PRIMARY KEY,
                         name    VARCHAR(50) NOT NULL UNIQUE   -- "READ", "CREATE", "UPDATE", "DELETE"
);

-- Bảng permission = role + resource + action
CREATE TABLE permissions (
                             id          BIGSERIAL PRIMARY KEY,
                             role        VARCHAR(100) NOT NULL,    -- "ROLE_ADMIN", "ROLE_USER"
                             resource_id BIGINT NOT NULL REFERENCES resources(id),
                             action_id   BIGINT NOT NULL REFERENCES actions(id),
                             UNIQUE (role, resource_id, action_id)
);

CREATE TABLE public.role_permissions (
                                         role_id int8 NOT NULL,
                                         permission_id int8 NOT NULL,
                                         CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id),
                                         CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.permissions(id) ON DELETE CASCADE,
                                         CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE
);
-- Seed data
INSERT INTO resources (name) VALUES ('products'), ('orders'), ('users'), ('profile');
INSERT INTO actions (name) VALUES ('READ'), ('CREATE'), ('UPDATE'), ('DELETE');

INSERT INTO permissions (role, resource_id, action_id)
SELECT 'ROLE_ADMIN', r.id, a.id FROM resources r, actions a; -- ADMIN có tất cả

INSERT INTO permissions (role, resource_id, action_id)
VALUES
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'products'), (SELECT id FROM actions WHERE name = 'READ')),
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'orders'),   (SELECT id FROM actions WHERE name = 'READ')),
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'orders'),   (SELECT id FROM actions WHERE name = 'CREATE')),
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'profile'),  (SELECT id FROM actions WHERE name = 'READ')),
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'profile'),  (SELECT id FROM actions WHERE name = 'UPDATE'));
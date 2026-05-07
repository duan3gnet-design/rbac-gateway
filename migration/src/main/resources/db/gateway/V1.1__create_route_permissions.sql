-- V1.1__create_route_permissions.sql (rbac_gateway database)

-- Bảng resource (products, orders, users...) — dùng cho RBAC lookup ở gateway
CREATE TABLE resources (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(100) NOT NULL UNIQUE
);

-- Bảng action (READ, CREATE, UPDATE, DELETE)
CREATE TABLE actions (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(50) NOT NULL UNIQUE
);

-- Bảng permission = role + resource + action
CREATE TABLE permissions (
    id          BIGSERIAL PRIMARY KEY,
    role        VARCHAR(100) NOT NULL,
    resource_id BIGINT NOT NULL REFERENCES resources(id),
    action_id   BIGINT NOT NULL REFERENCES actions(id),
    UNIQUE (role, resource_id, action_id)
);

CREATE TABLE route_permissions (
    route_id      VARCHAR(100) NOT NULL REFERENCES gateway_routes(id) ON DELETE CASCADE,
    permission_id BIGINT       NOT NULL REFERENCES permissions(id)    ON DELETE CASCADE,
    PRIMARY KEY (route_id, permission_id)
);

CREATE INDEX idx_rp_route ON route_permissions(route_id);
CREATE INDEX idx_rp_perm  ON route_permissions(permission_id);

-- View cho PermissionQueryRepository
CREATE OR REPLACE VIEW permission_view AS
SELECT
    p.id,
    p.role,
    r.name  AS resource,
    a.name  AS action,
    lower(r.name) || ':' || upper(a.name) AS code
FROM permissions p
JOIN resources r ON r.id = p.resource_id
JOIN actions   a ON a.id = p.action_id;

-- Seed resources & actions
INSERT INTO resources (name) VALUES ('products'), ('orders'), ('users'), ('profile'), ('admin'), ('auth');
INSERT INTO actions (name)   VALUES ('READ'), ('CREATE'), ('UPDATE'), ('DELETE'), ('LOGOUT_ALL');

-- Permissions ROLE_ADMIN → tất cả
INSERT INTO permissions (role, resource_id, action_id)
SELECT 'ROLE_ADMIN', r.id, a.id FROM resources r, actions a
WHERE a.name IN ('READ', 'CREATE', 'UPDATE', 'DELETE');

-- Permissions ROLE_USER
INSERT INTO permissions (role, resource_id, action_id) VALUES
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'products'), (SELECT id FROM actions WHERE name = 'READ')),
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'orders'),   (SELECT id FROM actions WHERE name = 'READ')),
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'orders'),   (SELECT id FROM actions WHERE name = 'CREATE')),
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'orders'),   (SELECT id FROM actions WHERE name = 'UPDATE')),
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'orders'),   (SELECT id FROM actions WHERE name = 'DELETE')),
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'profile'),  (SELECT id FROM actions WHERE name = 'READ')),
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'profile'),  (SELECT id FROM actions WHERE name = 'UPDATE')),
    ('ROLE_USER', (SELECT id FROM resources WHERE name = 'auth'),     (SELECT id FROM actions WHERE name = 'LOGOUT_ALL'));

-- Route permissions: products
INSERT INTO route_permissions (route_id, permission_id)
SELECT r.id, p.id FROM (VALUES ('resource-products'), ('resource-products-detail')) AS r(id)
CROSS JOIN (
    SELECT p.id FROM permissions p
    JOIN resources res ON res.id = p.resource_id
    JOIN actions   a   ON a.id   = p.action_id
    WHERE res.name = 'products' AND a.name = 'READ' AND p.role = 'ROLE_USER'
) AS p ON CONFLICT DO NOTHING;

-- Route permissions: orders GET
INSERT INTO route_permissions (route_id, permission_id)
SELECT r.id, p.id FROM (VALUES ('resource-orders-get'), ('resource-orders-get-detail')) AS r(id)
CROSS JOIN (
    SELECT p.id FROM permissions p
    JOIN resources res ON res.id = p.resource_id
    JOIN actions   a   ON a.id   = p.action_id
    WHERE res.name = 'orders' AND a.name = 'READ' AND p.role = 'ROLE_USER'
) AS p ON CONFLICT DO NOTHING;

-- Route permissions: orders POST
INSERT INTO route_permissions (route_id, permission_id)
SELECT r.id, p.id FROM (VALUES ('resource-orders-create'), ('resource-orders-create-detail')) AS r(id)
CROSS JOIN (
    SELECT p.id FROM permissions p
    JOIN resources res ON res.id = p.resource_id
    JOIN actions   a   ON a.id   = p.action_id
    WHERE res.name = 'orders' AND a.name = 'CREATE' AND p.role = 'ROLE_USER'
) AS p ON CONFLICT DO NOTHING;

-- Route permissions: orders PUT
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-orders-update', p.id
FROM permissions p
JOIN resources res ON res.id = p.resource_id
JOIN actions   a   ON a.id   = p.action_id
WHERE res.name = 'orders' AND a.name = 'UPDATE' AND p.role = 'ROLE_USER'
ON CONFLICT DO NOTHING;

-- Route permissions: orders DELETE
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-orders-delete', p.id
FROM permissions p
JOIN resources res ON res.id = p.resource_id
JOIN actions   a   ON a.id   = p.action_id
WHERE res.name = 'orders' AND a.name = 'DELETE' AND p.role = 'ROLE_USER'
ON CONFLICT DO NOTHING;

-- Route permissions: admin users GET
INSERT INTO route_permissions (route_id, permission_id)
SELECT r.id, p.id FROM (VALUES ('resource-admin-users-get'), ('resource-admin-users-get-detail')) AS r(id)
CROSS JOIN (
    SELECT p.id FROM permissions p
    JOIN resources res ON res.id = p.resource_id
    JOIN actions   a   ON a.id   = p.action_id
    WHERE res.name = 'users' AND a.name = 'READ' AND p.role = 'ROLE_ADMIN'
) AS p ON CONFLICT DO NOTHING;

-- Route permissions: admin users POST/PUT/DELETE
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-admin-users-create', p.id FROM permissions p
JOIN resources res ON res.id = p.resource_id JOIN actions a ON a.id = p.action_id
WHERE res.name = 'users' AND a.name = 'CREATE' AND p.role = 'ROLE_ADMIN' ON CONFLICT DO NOTHING;

INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-admin-users-update', p.id FROM permissions p
JOIN resources res ON res.id = p.resource_id JOIN actions a ON a.id = p.action_id
WHERE res.name = 'users' AND a.name = 'UPDATE' AND p.role = 'ROLE_ADMIN' ON CONFLICT DO NOTHING;

INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-admin-users-delete', p.id FROM permissions p
JOIN resources res ON res.id = p.resource_id JOIN actions a ON a.id = p.action_id
WHERE res.name = 'users' AND a.name = 'DELETE' AND p.role = 'ROLE_ADMIN' ON CONFLICT DO NOTHING;

-- Route permissions: profile
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-profile-get', p.id FROM permissions p
JOIN resources res ON res.id = p.resource_id JOIN actions a ON a.id = p.action_id
WHERE res.name = 'profile' AND a.name = 'READ' AND p.role = 'ROLE_USER' ON CONFLICT DO NOTHING;

INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-profile-update', p.id FROM permissions p
JOIN resources res ON res.id = p.resource_id JOIN actions a ON a.id = p.action_id
WHERE res.name = 'profile' AND a.name = 'UPDATE' AND p.role = 'ROLE_USER' ON CONFLICT DO NOTHING;

-- Route permissions: auth-logout-all
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'auth-logout-all', p.id FROM permissions p
JOIN resources res ON res.id = p.resource_id JOIN actions a ON a.id = p.action_id
WHERE res.name = 'auth' AND a.name = 'LOGOUT_ALL' AND p.role = 'ROLE_USER' ON CONFLICT DO NOTHING;

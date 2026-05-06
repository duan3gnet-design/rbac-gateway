-- V1.5__create_route_permissions.sql

CREATE TABLE IF NOT EXISTS route_permissions (
    route_id      VARCHAR(100) NOT NULL REFERENCES gateway_routes(id) ON DELETE CASCADE,
    permission_id BIGINT       NOT NULL REFERENCES permissions(id)    ON DELETE CASCADE,
    PRIMARY KEY (route_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_rp_route ON route_permissions(route_id);
CREATE INDEX IF NOT EXISTS idx_rp_perm  ON route_permissions(permission_id);

-- View cho PermissionQueryRepository (AdminRouteService.getAllPermissions)
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

-- ─── Helper macro: tìm permission_id theo role/resource/action ───────────────

-- Products
INSERT INTO route_permissions (route_id, permission_id)
SELECT r.id, p.id FROM (VALUES
    ('resource-products'),
    ('resource-products-detail')
) AS r(id)
CROSS JOIN (
    SELECT p.id FROM permissions p
    JOIN resources res ON res.id = p.resource_id
    JOIN actions   a   ON a.id   = p.action_id
    WHERE res.name = 'products' AND a.name = 'READ' AND p.role = 'ROLE_USER'
) AS p
ON CONFLICT DO NOTHING;

-- Orders — GET
INSERT INTO route_permissions (route_id, permission_id)
SELECT r.id, p.id FROM (VALUES
    ('resource-orders-get'),
    ('resource-orders-get-detail')
) AS r(id)
CROSS JOIN (
    SELECT p.id FROM permissions p
    JOIN resources res ON res.id = p.resource_id
    JOIN actions   a   ON a.id   = p.action_id
    WHERE res.name = 'orders' AND a.name = 'READ' AND p.role = 'ROLE_USER'
) AS p
ON CONFLICT DO NOTHING;

-- Orders — POST (CREATE)
INSERT INTO route_permissions (route_id, permission_id)
SELECT r.id, p.id FROM (VALUES
    ('resource-orders-create'),
    ('resource-orders-create-detail')
) AS r(id)
CROSS JOIN (
    SELECT p.id FROM permissions p
    JOIN resources res ON res.id = p.resource_id
    JOIN actions   a   ON a.id   = p.action_id
    WHERE res.name = 'orders' AND a.name = 'CREATE' AND p.role = 'ROLE_USER'
) AS p
ON CONFLICT DO NOTHING;

-- Orders — PUT (UPDATE)
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-orders-update', p.id
FROM permissions p
JOIN resources res ON res.id = p.resource_id
JOIN actions   a   ON a.id   = p.action_id
WHERE res.name = 'orders' AND a.name = 'UPDATE' AND p.role = 'ROLE_USER'
ON CONFLICT DO NOTHING;

-- Orders — DELETE
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-orders-delete', p.id
FROM permissions p
JOIN resources res ON res.id = p.resource_id
JOIN actions   a   ON a.id   = p.action_id
WHERE res.name = 'orders' AND a.name = 'DELETE' AND p.role = 'ROLE_USER'
ON CONFLICT DO NOTHING;

-- Admin Users — GET
INSERT INTO route_permissions (route_id, permission_id)
SELECT r.id, p.id FROM (VALUES
    ('resource-admin-users-get'),
    ('resource-admin-users-get-detail')
) AS r(id)
CROSS JOIN (
    SELECT p.id FROM permissions p
    JOIN resources res ON res.id = p.resource_id
    JOIN actions   a   ON a.id   = p.action_id
    WHERE res.name = 'users' AND a.name = 'READ' AND p.role = 'ROLE_ADMIN'
) AS p
ON CONFLICT DO NOTHING;

-- Admin Users — POST (CREATE)
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-admin-users-create', p.id
FROM permissions p
JOIN resources res ON res.id = p.resource_id
JOIN actions   a   ON a.id   = p.action_id
WHERE res.name = 'users' AND a.name = 'CREATE' AND p.role = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- Admin Users — PUT (UPDATE)
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-admin-users-update', p.id
FROM permissions p
JOIN resources res ON res.id = p.resource_id
JOIN actions   a   ON a.id   = p.action_id
WHERE res.name = 'users' AND a.name = 'UPDATE' AND p.role = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- Admin Users — DELETE
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-admin-users-delete', p.id
FROM permissions p
JOIN resources res ON res.id = p.resource_id
JOIN actions   a   ON a.id   = p.action_id
WHERE res.name = 'users' AND a.name = 'DELETE' AND p.role = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- Profile — GET (READ)
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-profile-get', p.id
FROM permissions p
JOIN resources res ON res.id = p.resource_id
JOIN actions   a   ON a.id   = p.action_id
WHERE res.name = 'profile' AND a.name = 'READ' AND p.role = 'ROLE_USER'
ON CONFLICT DO NOTHING;

-- Profile — PUT (UPDATE)
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-profile-update', p.id
FROM permissions p
JOIN resources res ON res.id = p.resource_id
JOIN actions   a   ON a.id   = p.action_id
WHERE res.name = 'profile' AND a.name = 'UPDATE' AND p.role = 'ROLE_USER'
ON CONFLICT DO NOTHING;

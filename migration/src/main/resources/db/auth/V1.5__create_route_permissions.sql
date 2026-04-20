-- V1.5__create_route_permissions.sql
-- Bảng junction: gắn permission vào gateway route
-- Dùng bởi AdminRouteService để enforce RBAC per-route trên Gateway

CREATE TABLE IF NOT EXISTS route_permissions (
    route_id      VARCHAR(100) NOT NULL REFERENCES gateway_routes(id) ON DELETE CASCADE,
    permission_id BIGINT       NOT NULL REFERENCES permissions(id)    ON DELETE CASCADE,
    PRIMARY KEY (route_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_rp_route  ON route_permissions(route_id);
CREATE INDEX IF NOT EXISTS idx_rp_perm   ON route_permissions(permission_id);

-- View phẳng để query permissions dễ hơn (dùng bởi PermissionQueryRepository)
CREATE OR REPLACE VIEW permission_view AS
SELECT
    p.id,
    p.role,
    r.name AS resource,
    a.name AS action
FROM permissions p
JOIN resources r ON r.id = p.resource_id
JOIN actions   a ON a.id = p.action_id;

-- Seed: resource-service route yêu cầu các ROLE_USER permissions
INSERT INTO route_permissions (route_id, permission_id)
SELECT 'resource-service', p.id
FROM permissions p
WHERE p.role = 'ROLE_USER'
ON CONFLICT DO NOTHING;

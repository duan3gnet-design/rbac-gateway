-- V1.8__seed_admin_route_permissions.sql
--
-- V1.4 đã tạo 2 routes này, V1.2 chưa có resource 'admin' và 'auth'.

-- ── Thêm resources + actions còn thiếu ───────────────────────────────────────

INSERT INTO resources (name) VALUES ('admin'), ('auth')
ON CONFLICT (name) DO NOTHING;

INSERT INTO actions (name) VALUES ('LOGOUT_ALL')
ON CONFLICT (name) DO NOTHING;

-- ── Permissions: ROLE_ADMIN → admin:* ────────────────────────────────────────

INSERT INTO permissions (role, resource_id, action_id)
SELECT 'ROLE_ADMIN', r.id, a.id
FROM resources r, actions a
WHERE r.name = 'admin'
  AND a.name IN ('READ', 'CREATE', 'UPDATE', 'DELETE')
ON CONFLICT DO NOTHING;

-- ── Permissions: ROLE_USER → auth:LOGOUT_ALL ─────────────────────────────────

INSERT INTO permissions (role, resource_id, action_id)
SELECT 'ROLE_USER', r.id, a.id
FROM resources r, actions a
WHERE r.name = 'auth' AND a.name = 'LOGOUT_ALL'
ON CONFLICT DO NOTHING;

-- ── Route permissions: auth-logout-all → ROLE_USER auth:LOGOUT_ALL ───────────

INSERT INTO route_permissions (route_id, permission_id)
SELECT 'auth-logout-all', p.id
FROM permissions p
JOIN resources r ON r.id = p.resource_id
JOIN actions   a ON a.id = p.action_id
WHERE r.name = 'auth' AND a.name = 'LOGOUT_ALL' AND p.role = 'ROLE_USER'
ON CONFLICT DO NOTHING;

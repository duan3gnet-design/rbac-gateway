-- ============================================================
-- Seed data cho integration tests
-- Chạy BEFORE_TEST_METHOD → mỗi test method đều có data sạch
-- ============================================================

-- Xóa data cũ theo đúng thứ tự FK (tránh constraint violation)
DELETE FROM refresh_tokens;
DELETE FROM user_roles;
DELETE FROM users;
DELETE FROM permissions;
DELETE FROM roles;
DELETE FROM actions;
DELETE FROM resources;

-- ── Actions ──────────────────────────────────────────────────
INSERT INTO actions (id, name) VALUES (1, 'READ');
INSERT INTO actions (id, name) VALUES (2, 'CREATE');
INSERT INTO actions (id, name) VALUES (3, 'UPDATE');
INSERT INTO actions (id, name) VALUES (4, 'DELETE');
-- Reset sequence sau khi insert với explicit ID
ALTER SEQUENCE actions_id_seq RESTART WITH 100;

-- ── Resources ────────────────────────────────────────────────
INSERT INTO resources (id, name) VALUES (1, 'products');
INSERT INTO resources (id, name) VALUES (2, 'orders');
INSERT INTO resources (id, name) VALUES (3, 'users');
INSERT INTO resources (id, name) VALUES (4, 'profile');
ALTER SEQUENCE resources_id_seq RESTART WITH 100;

-- ── Roles ────────────────────────────────────────────────────
INSERT INTO roles (id, name) VALUES (1, 'ROLE_USER');
INSERT INTO roles (id, name) VALUES (2, 'ROLE_ADMIN');
ALTER SEQUENCE roles_id_seq RESTART WITH 100;

-- ── Permissions: ROLE_USER ───────────────────────────────────
INSERT INTO permissions (id, role, resource_id, action_id) VALUES (1,  'ROLE_USER',  1, 1); -- products:READ
INSERT INTO permissions (id, role, resource_id, action_id) VALUES (2,  'ROLE_USER',  2, 1); -- orders:READ
INSERT INTO permissions (id, role, resource_id, action_id) VALUES (3,  'ROLE_USER',  2, 2); -- orders:CREATE
INSERT INTO permissions (id, role, resource_id, action_id) VALUES (4,  'ROLE_USER',  4, 1); -- profile:READ
INSERT INTO permissions (id, role, resource_id, action_id) VALUES (5,  'ROLE_USER',  4, 3); -- profile:UPDATE

-- ── Permissions: ROLE_ADMIN ──────────────────────────────────
INSERT INTO permissions (id, role, resource_id, action_id) VALUES (6,  'ROLE_ADMIN', 3, 1); -- users:READ
INSERT INTO permissions (id, role, resource_id, action_id) VALUES (7,  'ROLE_ADMIN', 3, 2); -- users:CREATE
INSERT INTO permissions (id, role, resource_id, action_id) VALUES (8,  'ROLE_ADMIN', 3, 3); -- users:UPDATE
INSERT INTO permissions (id, role, resource_id, action_id) VALUES (9,  'ROLE_ADMIN', 3, 4); -- users:DELETE
INSERT INTO permissions (id, role, resource_id, action_id) VALUES (10, 'ROLE_ADMIN', 1, 1); -- products:READ
INSERT INTO permissions (id, role, resource_id, action_id) VALUES (11, 'ROLE_ADMIN', 2, 1); -- orders:READ
ALTER SEQUENCE permissions_id_seq RESTART WITH 100;

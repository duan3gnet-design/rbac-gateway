-- V1.7__create_route_permission_rules_view.sql
--
-- View phẳng cho RbacPermissionChecker:
-- mỗi row = 1 rule (route_id, path_pattern, http_method, permission_code).
--
-- Cú pháp jsonb đúng:
--   elem->'args'->>'pattern'   (-> giữ jsonb, ->> lấy text value)
--   elem->>'name'              (lấy text của field "name")
-- KHÔNG dùng elem->>'args'->>'pattern' vì ->> trả TEXT,
-- và TEXT không hỗ trợ toán tử -> / ->>.

CREATE OR REPLACE VIEW route_permission_rules AS
SELECT
    gr.id AS route_id,
    (
        SELECT elem->'args'->>'pattern'
        FROM jsonb_array_elements(gr.predicates::jsonb) AS elem
        WHERE elem->>'name' = 'Path'
        LIMIT 1
    ) AS path_pattern,
    upper(
        (
            SELECT elem->'args'->>'methods'
            FROM jsonb_array_elements(gr.predicates::jsonb) AS elem
            WHERE elem->>'name' = 'Method'
            LIMIT 1
        )
    ) AS http_method,
    lower(r.name) || ':' || upper(a.name) AS permission_code
FROM gateway_routes gr
JOIN route_permissions rp ON rp.route_id  = gr.id
JOIN permissions       p  ON p.id         = rp.permission_id
JOIN resources         r  ON r.id         = p.resource_id
JOIN actions           a  ON a.id         = p.action_id
WHERE gr.enabled = TRUE;

COMMENT ON VIEW route_permission_rules IS
    'Flat RBAC rules: (route_id, path_pattern, http_method, permission_code). '
    'http_method NULL = match all methods. '
    'Consumed by RbacPermissionChecker cache.';

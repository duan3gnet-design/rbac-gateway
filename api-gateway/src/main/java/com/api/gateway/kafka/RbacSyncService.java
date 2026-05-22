package com.api.gateway.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sync RBAC data từ auth-service vào gateway DB.
 *
 * <h3>Chiến lược đồng bộ</h3>
 * <ul>
 *   <li><b>Resource/Action</b>: upsert theo tên — gateway chỉ dùng tên để check
 *       permission, không quan tâm id từ auth-service.</li>
 *   <li><b>Permission</b>: upsert theo (resource_name, action_name) →
 *       resolve id nội bộ trong gateway DB → upsert vào bảng permissions.</li>
 *   <li><b>DELETED</b>: soft cascade — xóa permission trước, sau đó xóa
 *       resource/action chỉ khi không còn permission nào tham chiếu.</li>
 * </ul>
 *
 * <h3>Tại sao không dùng id từ auth-service?</h3>
 * <p>Gateway DB có thể có id sequence riêng (auto-increment).
 * Đồng bộ theo <i>tên</i> (business key) an toàn hơn và tránh conflict.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RbacSyncService {

    private final JdbcTemplate jdbc;

    // ─── Resource ─────────────────────────────────────────────────────────────

    @Transactional
    public void upsertResource(String name) {
        jdbc.update("""
                INSERT INTO resources (name)
                VALUES (?)
                ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
                """, name);
        log.info("[RbacSync] Upserted resource '{}'", name);
    }

    @Transactional
    public void deleteResource(String name) {
        // Chỉ xóa nếu không còn permission nào dùng resource này
        int permCount = countPermissionsByResourceName(name);
        if (permCount > 0) {
            log.warn("[RbacSync] Skip delete resource '{}' — {} permission(s) still reference it",
                    name, permCount);
            return;
        }
        int deleted = jdbc.update("DELETE FROM resources WHERE name = ?", name);
        log.info("[RbacSync] Deleted resource '{}' ({} row)", name, deleted);
    }

    @Transactional
    public void renameResource(String oldName, String newName) {
        // UPDATE + cascade thông qua FK tự động (resource_id không đổi)
        int updated = jdbc.update("UPDATE resources SET name = ? WHERE name = ?", newName, oldName);
        if (updated == 0) {
            // Resource chưa có → upsert
            upsertResource(newName);
        } else {
            log.info("[RbacSync] Renamed resource '{}' → '{}'", oldName, newName);
        }
    }

    // ─── Action ───────────────────────────────────────────────────────────────

    @Transactional
    public void upsertAction(String name) {
        jdbc.update("""
                INSERT INTO actions (name)
                VALUES (?)
                ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
                """, name);
        log.info("[RbacSync] Upserted action '{}'", name);
    }

    @Transactional
    public void deleteAction(String name) {
        int permCount = countPermissionsByActionName(name);
        if (permCount > 0) {
            log.warn("[RbacSync] Skip delete action '{}' — {} permission(s) still reference it",
                    name, permCount);
            return;
        }
        int deleted = jdbc.update("DELETE FROM actions WHERE name = ?", name);
        log.info("[RbacSync] Deleted action '{}' ({} row)", name, deleted);
    }

    @Transactional
    public void renameAction(String oldName, String newName) {
        int updated = jdbc.update("UPDATE actions SET name = ? WHERE name = ?", newName, oldName);
        if (updated == 0) {
            upsertAction(newName);
        } else {
            log.info("[RbacSync] Renamed action '{}' → '{}'", oldName, newName);
        }
    }

    // ─── Permission ───────────────────────────────────────────────────────────

    @Transactional
    public void upsertPermission(String resourceName, String actionName) {
        // Đảm bảo resource và action tồn tại trước
        upsertResource(resourceName);
        upsertAction(actionName);

        jdbc.update("""
                INSERT INTO permissions (resource_id, action_id)
                SELECT r.id, a.id
                FROM resources r, actions a
                WHERE r.name = ? AND a.name = ?
                ON CONFLICT (resource_id, action_id) DO NOTHING
                """, resourceName, actionName);
        log.info("[RbacSync] Upserted permission {}:{}", resourceName, actionName);
    }

    @Transactional
    public void deletePermission(String resourceName, String actionName) {
        int deleted = jdbc.update("""
                DELETE FROM permissions
                WHERE resource_id = (SELECT id FROM resources WHERE name = ?)
                  AND action_id   = (SELECT id FROM actions   WHERE name = ?)
                """, resourceName, actionName);
        log.info("[RbacSync] Deleted permission {}:{} ({} row)", resourceName, actionName, deleted);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private int countPermissionsByResourceName(String name) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM permissions p
                JOIN resources r ON r.id = p.resource_id
                WHERE r.name = ?
                """, Integer.class, name);
        return count != null ? count : 0;
    }

    private int countPermissionsByActionName(String name) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM permissions p
                JOIN actions a ON a.id = p.action_id
                WHERE a.name = ?
                """, Integer.class, name);
        return count != null ? count : 0;
    }
}

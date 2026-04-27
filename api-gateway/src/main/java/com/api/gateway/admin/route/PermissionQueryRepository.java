package com.api.gateway.admin.route;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Query permissions bằng JOIN vì Spring Data JDBC không hỗ trợ @ManyToOne.
 * permissions JOIN resources JOIN actions → PermissionResponse
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PermissionQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String SQL = """
            SELECT p.id, p.role, r.name AS resource, a.name AS action
            FROM permissions p
            JOIN resources r ON r.id = p.resource_id
            JOIN actions   a ON a.id = p.action_id
            ORDER BY p.role, r.name, a.name
            """;

    public List<AdminDtos.PermissionResponse> findAll() {
        return jdbcTemplate.query(SQL, (rs, rowNum) -> {
            Long   id       = rs.getLong("id");
            String role     = rs.getString("role");
            String resource = rs.getString("resource");
            String action   = rs.getString("action");
            String code     = resource + ":" + action;
            return new AdminDtos.PermissionResponse(id, role, resource, action, code);
        });
    }
}

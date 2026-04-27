package com.api.gateway.admin.route;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Custom JDBC repository cho bảng route_permissions (composite PK).
 */
@Repository
@RequiredArgsConstructor
public class RoutePermissionRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<Long> findPermissionIdsByRouteId(String routeId) {
        return jdbcTemplate.query(
                "SELECT permission_id FROM route_permissions WHERE route_id = ?",
                (rs, rowNum) -> rs.getLong("permission_id"),
                routeId
        );
    }

    public void deleteByRouteId(String routeId) {
        jdbcTemplate.update(
                "DELETE FROM route_permissions WHERE route_id = ?",
                routeId
        );
    }

    public void insert(String routeId, Long permissionId) {
        jdbcTemplate.update("""
                INSERT INTO route_permissions (route_id, permission_id)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """,
                routeId, permissionId
        );
    }
}

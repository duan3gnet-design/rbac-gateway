package com.api.gateway.admin.route;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Custom R2DBC repository cho bảng route_permissions (composite PK).
 * Spring Data R2DBC không hỗ trợ composite key qua interface nên dùng DatabaseClient.
 */
@Repository
@RequiredArgsConstructor
public class RoutePermissionRepository {

    private final DatabaseClient db;

    public Flux<Long> findPermissionIdsByRouteId(String routeId) {
        return db.sql("SELECT permission_id FROM route_permissions WHERE route_id = :routeId")
                .bind("routeId", routeId)
                .map(row -> row.get("permission_id", Long.class))
                .all();
    }

    public Mono<Void> deleteByRouteId(String routeId) {
        return db.sql("DELETE FROM route_permissions WHERE route_id = :routeId")
                .bind("routeId", routeId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Void> insert(String routeId, Long permissionId) {
        return db.sql("""
                INSERT INTO route_permissions (route_id, permission_id)
                VALUES (:routeId, :permissionId)
                ON CONFLICT DO NOTHING
                """)
                .bind("routeId", routeId)
                .bind("permissionId", permissionId)
                .fetch()
                .rowsUpdated()
                .then();
    }
}

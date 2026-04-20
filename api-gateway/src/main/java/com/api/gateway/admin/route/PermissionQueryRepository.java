package com.api.gateway.admin.route;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Query permissions bằng JOIN vì R2DBC không hỗ trợ @ManyToOne.
 * permissions JOIN resources JOIN actions → PermissionResponse
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PermissionQueryRepository {

    private final DatabaseClient db;

    private static final String SQL = """
            SELECT p.id, p.role, r.name AS resource, a.name AS action
            FROM permissions p
            JOIN resources r ON r.id = p.resource_id
            JOIN actions   a ON a.id = p.action_id
            ORDER BY p.role, r.name, a.name
            """;

    public Flux<AdminDtos.PermissionResponse> findAll() {
        return db.sql(SQL)
                .map(row -> {
                    Long   id       = row.get("id",       Long.class);
                    String role     = row.get("role",     String.class);
                    String resource = row.get("resource", String.class);
                    String action   = row.get("action",   String.class);
                    String code     = resource + ":" + action;
                    return new AdminDtos.PermissionResponse(id, role, resource, action, code);
                })
                .all();
    }
}

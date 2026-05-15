package com.api.gateway.admin.route;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs dùng cho Admin Route Management API.
 * Tách khỏi entity để không expose trực tiếp DB model.
 */
public final class AdminDtos {

    private AdminDtos() {}

    // ─── Route ──────────────────────────────────────────────────────────────

    /** Request body khi tạo / cập nhật route */
    public record RouteRequest(
            String id,
            String uri,
            String predicates,
            String filters,
            int routeOrder,
            boolean enabled
    ) {}

    /** Response trả về cho client */
    public record RouteResponse(
            String id,
            String uri,
            String predicates,
            String filters,
            int routeOrder,
            boolean enabled,
            List<Long> permissionIds,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    /** PATCH toggle enabled */
    public record ToggleRequest(boolean enabled) {}

    /** PUT assign permissions vào route */
    public record AssignPermissionsRequest(List<Long> permissionIds) {}

    // ─── Permission ─────────────────────────────────────────────────────────

    /** Permission hiển thị trên UI */
    public record PermissionResponse(
            Long id,
            String role,
            String resource,
            String action,
            String code           // = "resource:action"
    ) {}

    // ─── Eureka ──────────────────────────────────────────────────────────────

    /** Thông tin một service instance đang đăng ký trên Eureka */
    public record EurekaServiceResponse(
            String serviceId,
            String instanceId,
            String homePageUrl,
            String ipAddr,
            int    port
    ) {}
}

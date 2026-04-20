package com.api.gateway.admin.route;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST API cho Route Management UI.
 *
 * <p>Tất cả endpoint đều yêu cầu ROLE_ADMIN (enforce tại JwtAuthenticationFilter).
 *
 * <pre>
 * GET    /api/admin/routes                       — lấy tất cả routes
 * GET    /api/admin/routes/{id}                  — lấy route theo id
 * POST   /api/admin/routes                       — tạo route mới
 * PUT    /api/admin/routes/{id}                  — cập nhật route
 * DELETE /api/admin/routes/{id}                  — xóa route
 * PATCH  /api/admin/routes/{id}/toggle           — bật/tắt route
 * GET    /api/admin/routes/{id}/permissions      — lấy permission của route
 * PUT    /api/admin/routes/{id}/permissions      — gán permissions vào route
 * GET    /api/admin/permissions                  — lấy tất cả permissions
 * </pre>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminRouteController {

    private final AdminRouteService service;

    // ─── Routes ─────────────────────────────────────────────────────────────

    @GetMapping("/routes")
    public Flux<AdminDtos.RouteResponse> getAllRoutes() {
        return service.getAllRoutes();
    }

    @GetMapping("/routes/{id}")
    public Mono<AdminDtos.RouteResponse> getRoute(@PathVariable String id) {
        return service.getRouteById(id);
    }

    @PostMapping("/routes")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AdminDtos.RouteResponse> createRoute(@RequestBody AdminDtos.RouteRequest req) {
        return service.createRoute(req);
    }

    @PutMapping("/routes/{id}")
    public Mono<AdminDtos.RouteResponse> updateRoute(
            @PathVariable String id,
            @RequestBody AdminDtos.RouteRequest req) {
        return service.updateRoute(id, req);
    }

    @DeleteMapping("/routes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteRoute(@PathVariable String id) {
        return service.deleteRoute(id);
    }

    @PatchMapping("/routes/{id}/toggle")
    public Mono<AdminDtos.RouteResponse> toggleRoute(
            @PathVariable String id,
            @RequestBody AdminDtos.ToggleRequest req) {
        return service.toggleRoute(id, req.enabled());
    }

    // ─── Route Permissions ──────────────────────────────────────────────────

    @GetMapping("/routes/{routeId}/permissions")
    public Flux<Long> getRoutePermissions(@PathVariable String routeId) {
        return service.getPermissionIdsByRoute(routeId);
    }

    @PutMapping("/routes/{routeId}/permissions")
    public Mono<java.util.List<Long>> assignPermissions(
            @PathVariable String routeId,
            @RequestBody AdminDtos.AssignPermissionsRequest req) {
        return service.assignPermissions(routeId, req.permissionIds());
    }

    // ─── Permissions ────────────────────────────────────────────────────────

    @GetMapping("/permissions")
    public Flux<AdminDtos.PermissionResponse> getAllPermissions() {
        return service.getAllPermissions();
    }
}

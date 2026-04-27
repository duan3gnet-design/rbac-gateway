package com.api.gateway.admin.route;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API cho Route Management UI.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminRouteController {

    private final AdminRouteService service;

    // ─── Routes ─────────────────────────────────────────────────────────────

    @GetMapping("/routes")
    public List<AdminDtos.RouteResponse> getAllRoutes() {
        return service.getAllRoutesList();
    }

    @GetMapping("/routes/{id}")
    public AdminDtos.RouteResponse getRoute(@PathVariable String id) {
        return service.getRouteById(id);
    }

    @PostMapping("/routes")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminDtos.RouteResponse createRoute(@RequestBody AdminDtos.RouteRequest req) {
        return service.createRoute(req);
    }

    @PutMapping("/routes/{id}")
    public AdminDtos.RouteResponse updateRoute(
            @PathVariable String id,
            @RequestBody AdminDtos.RouteRequest req) {
        return service.updateRoute(id, req);
    }

    @DeleteMapping("/routes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoute(@PathVariable String id) {
        service.deleteRoute(id);
    }

    @PatchMapping("/routes/{id}/toggle")
    public AdminDtos.RouteResponse toggleRoute(
            @PathVariable String id,
            @RequestBody AdminDtos.ToggleRequest req) {
        return service.toggleRoute(id, req.enabled());
    }

    // ─── Route Permissions ──────────────────────────────────────────────────

    @GetMapping("/routes/{routeId}/permissions")
    public List<Long> getRoutePermissions(@PathVariable String routeId) {
        return service.getPermissionIdsByRoute(routeId);
    }

    @PutMapping("/routes/{routeId}/permissions")
    public List<Long> assignPermissions(
            @PathVariable String routeId,
            @RequestBody AdminDtos.AssignPermissionsRequest req) {
        return service.assignPermissions(routeId, req.permissionIds());
    }

    // ─── Permissions ────────────────────────────────────────────────────────

    @GetMapping("/permissions")
    public List<AdminDtos.PermissionResponse> getAllPermissions() {
        return service.getAllPermissions();
    }
}

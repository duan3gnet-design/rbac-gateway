package com.api.gateway.admin.route;

import com.api.gateway.entity.GatewayRouteEntity;
import com.api.gateway.repository.GatewayRouteRepository;
import com.api.gateway.route.RouteRefreshEvent;
import com.api.gateway.validator.RbacPermissionChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic cho Admin Route Management.
 *
 * <p>Sau mỗi thao tác write:</p>
 * <ul>
 *   <li>Publish {@link RouteRefreshEvent} → {@code DatabaseRouteLocator} reload routes
 *       + {@code RbacPermissionChecker} invalidate rules cache.</li>
 *   <li>{@code assignPermissions()} gọi thêm {@code rbacChecker.invalidateCache()}
 *       vì permission mapping thay đổi nhưng route structure không đổi
 *       (không muốn reload toàn bộ RouterFunction chỉ vì assign permission).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRouteService {

    private final GatewayRouteRepository    routeRepo;
    private final RoutePermissionRepository permissionRepo;
    private final PermissionQueryRepository permQueryRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final RbacPermissionChecker     rbacChecker;

    // ─── Queries ────────────────────────────────────────────────────────────

    public List<AdminDtos.RouteResponse> getAllRoutesList() {
        List<GatewayRouteEntity> entities = new ArrayList<>();
        routeRepo.findAll().forEach(entities::add);
        return entities.stream().map(this::toResponse).toList();
    }

    public AdminDtos.RouteResponse getRouteById(String id) {
        return routeRepo.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Route not found: " + id));
    }

    public List<AdminDtos.PermissionResponse> getAllPermissions() {
        return permQueryRepo.findAll();
    }

    public List<Long> getPermissionIdsByRoute(String routeId) {
        return permissionRepo.findPermissionIdsByRouteId(routeId);
    }

    // ─── Commands ───────────────────────────────────────────────────────────

    public AdminDtos.RouteResponse createRoute(AdminDtos.RouteRequest req) {
        if (routeRepo.existsById(req.id())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Route already exists: " + req.id());
        }
        GatewayRouteEntity entity = new GatewayRouteEntity(
                req.id(), req.uri(),
                req.predicates(), req.filters(),
                req.routeOrder(), req.enabled(),
                OffsetDateTime.now(), OffsetDateTime.now(), true
        );
        var saved = routeRepo.save(entity);
        publishRefresh("create", saved.id());
        return toResponse(saved);
    }

    public AdminDtos.RouteResponse updateRoute(String id, AdminDtos.RouteRequest req) {
        GatewayRouteEntity existing = routeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Route not found: " + id));
        GatewayRouteEntity updated = new GatewayRouteEntity(
                existing.id(),
                req.uri(), req.predicates(), req.filters(),
                req.routeOrder(), req.enabled(),
                existing.createdAt(), OffsetDateTime.now()
        );
        var saved = routeRepo.save(updated);
        publishRefresh("update", saved.id());
        return toResponse(saved);
    }

    @Transactional
    public void deleteRoute(String id) {
        GatewayRouteEntity entity = routeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Route not found: " + id));
        permissionRepo.deleteByRouteId(id);
        routeRepo.delete(entity);
        publishRefresh("delete", id);
    }

    public AdminDtos.RouteResponse toggleRoute(String id, boolean enabled) {
        GatewayRouteEntity existing = routeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Route not found: " + id));
        GatewayRouteEntity updated = new GatewayRouteEntity(
                existing.id(), existing.uri(),
                existing.predicates(), existing.filters(),
                existing.routeOrder(), enabled,
                existing.createdAt(), OffsetDateTime.now()
        );
        var saved = routeRepo.save(updated);
        publishRefresh("toggle", saved.id());
        return toResponse(saved);
    }

    @Transactional
    public List<Long> assignPermissions(String routeId, List<Long> permissionIds) {
        if (!routeRepo.existsById(routeId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Route not found: " + routeId);
        }
        permissionRepo.deleteByRouteId(routeId);
        permissionIds.forEach(pid -> permissionRepo.insert(routeId, pid));

        // Permission mapping thay đổi → rules view đã stale.
        // Dùng invalidateCache() thay vì publishRefresh() để tránh
        // reload toàn bộ RouterFunction (route structure không đổi).
        rbacChecker.invalidateCache();

        return permissionRepo.findPermissionIdsByRouteId(routeId);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private AdminDtos.RouteResponse toResponse(GatewayRouteEntity e) {
        List<Long> ids = permissionRepo.findPermissionIdsByRouteId(e.id());
        return new AdminDtos.RouteResponse(
                e.id(), e.uri(), e.predicates(), e.filters(),
                e.routeOrder(), e.enabled(), ids,
                e.createdAt(), e.updatedAt()
        );
    }

    private void publishRefresh(String action, String routeId) {
        log.info("Publishing RouteRefreshEvent after [{}] on route [{}]", action, routeId);
        // RouteRefreshEvent listener trong DatabaseRouteLocator VÀ
        // RbacPermissionChecker đều lắng nghe event này →
        // cả route cache lẫn rules cache đều bị invalidate cùng lúc.
        eventPublisher.publishEvent(new RouteRefreshEvent(this));
    }
}

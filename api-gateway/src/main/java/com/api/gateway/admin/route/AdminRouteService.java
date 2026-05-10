package com.api.gateway.admin.route;

import com.api.gateway.entity.GatewayRouteEntity;
import com.api.gateway.repository.GatewayRouteRepository;
import com.api.gateway.route.RouteRefreshPublisher;
import com.api.gateway.validator.RbacPermissionChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 *   <li>Gọi {@link RouteRefreshPublisher#publish(String, String)} →
 *       publish message lên Redis Pub/Sub channel →
 *       <strong>tất cả nodes</strong> (kể cả node hiện tại) nhận message
 *       → mỗi node tự invalidate local cache của
 *       {@code DatabaseRouteLocator} và {@code RbacPermissionChecker}.</li>
 *   <li>{@code assignPermissions()} gọi {@code rbacChecker.invalidateCache()}
 *       để đảm bảo rules cache stale ngay lập tức trên node hiện tại,
 *       đồng thời publish Redis event để propagate sang các node khác.</li>
 * </ul>
 *
 * <h3>Multi-node flow</h3>
 * <pre>
 *   Admin API → AdminRouteService.publishRefresh()
 *                   → RouteRefreshPublisher → Redis "gateway:route-refresh"
 *                       → RouteRefreshSubscriber (on EVERY node)
 *                           → Spring RouteRefreshEvent (local)
 *                               → DatabaseRouteLocator.onRefreshRoutes()
 *                               → RbacPermissionChecker.onRefreshRoutes()
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRouteService {

    private final GatewayRouteRepository    routeRepo;
    private final RoutePermissionRepository permissionRepo;
    private final PermissionQueryRepository permQueryRepo;
    private final RouteRefreshPublisher     refreshPublisher;
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

        // Permission mapping thay đổi → broadcast sang tất cả nodes.
        // Cũng invalidate local cache ngay lập tức (trước khi Redis message
        // được nhận lại qua subscriber) để đảm bảo node hiện tại phản hồi
        // đúng với request tiếp theo.
        rbacChecker.invalidateCache();
        publishRefresh("assign-permissions", routeId);

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

    /**
     * Publish refresh message lên Redis → broadcast sang tất cả nodes.
     * Node hiện tại cũng sẽ nhận lại message này qua
     * {@link com.api.gateway.route.RouteRefreshSubscriber}.
     */
    private void publishRefresh(String action, String routeId) {
        log.info("[AdminRouteService] Broadcasting route refresh: action='{}', route='{}'",
                action, routeId);
        refreshPublisher.publish(action, routeId);
    }
}

package com.api.gateway.admin.route;

import com.api.gateway.entity.GatewayRouteEntity;
import com.api.gateway.repository.GatewayRouteRepository;
import com.api.gateway.route.RouteRefreshEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Business logic cho Admin Route Management.
 *
 * <p>Sau mỗi thao tác write, publish RouteRefreshEvent để DatabaseRouteLocator
 * tự động reload route definitions.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRouteService {

    private final GatewayRouteRepository  routeRepo;
    private final RoutePermissionRepository permissionRepo;
    private final PermissionQueryRepository permQueryRepo;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Queries ────────────────────────────────────────────────────────────

    public List<AdminDtos.RouteResponse> getAllRoutes() {
        return routeRepo.findAll().iterator()
                .next() == null ? List.of() :
                toList(routeRepo.findAll());
    }

    public List<AdminDtos.RouteResponse> getAllRoutesList() {
        List<GatewayRouteEntity> entities = new java.util.ArrayList<>();
        routeRepo.findAll().forEach(entities::add);
        return entities.stream().map(this::toResponse).toList();
    }

    public AdminDtos.RouteResponse getRouteById(String id) {
        return routeRepo.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Route not found: " + id));
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
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Route already exists: " + req.id());
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Route not found: " + id));
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Route not found: " + id));
        permissionRepo.deleteByRouteId(id);
        routeRepo.delete(entity);
        publishRefresh("delete", id);
    }

    public AdminDtos.RouteResponse toggleRoute(String id, boolean enabled) {
        GatewayRouteEntity existing = routeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Route not found: " + id));
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Route not found: " + routeId);
        }
        permissionRepo.deleteByRouteId(routeId);
        permissionIds.forEach(pid -> permissionRepo.insert(routeId, pid));
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

    private List<AdminDtos.RouteResponse> toList(Iterable<GatewayRouteEntity> entities) {
        List<AdminDtos.RouteResponse> result = new java.util.ArrayList<>();
        entities.forEach(e -> result.add(toResponse(e)));
        return result;
    }

    private void publishRefresh(String action, String routeId) {
        log.info("Publishing RouteRefreshEvent after [{}] on route [{}]", action, routeId);
        eventPublisher.publishEvent(new RouteRefreshEvent(this));
    }
}

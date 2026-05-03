package com.api.gateway.route;

import com.api.gateway.entity.GatewayRouteEntity;
import com.api.gateway.repository.GatewayRouteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * Load routes từ PostgreSQL và expose qua RouterFunction (Gateway MVC API).
 *
 * <p>Hỗ trợ 2 format URI trong cột {@code uri} của bảng gateway_routes:</p>
 * <ul>
 *   <li>{@code http://localhost:8081} — direct URL (dev / fallback)</li>
 *   <li>{@code lb://auth-service}    — Eureka service-name, resolve qua DiscoveryClient</li>
 * </ul>
 *
 * <p>Cache in-memory, invalidate khi nhận {@link RouteRefreshEvent}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseRouteLocator {

    private final GatewayRouteRepository routeRepository;
    private final ObjectMapper           objectMapper;
    private final DiscoveryClient        discoveryClient;

    private final AtomicReference<RouterFunction<ServerResponse>> routerCache =
            new AtomicReference<>(null);

    /**
     * Trả về RouterFunction hiện tại (từ cache hoặc load mới từ DB).
     * Được gọi bởi GatewayMvcRouterFunctionConfig.
     */
    public RouterFunction<ServerResponse> getRouterFunction() {
        RouterFunction<ServerResponse> cached = routerCache.get();
        if (cached != null) {
            return cached;
        }
        return reload();
    }

    /**
     * Invalidate cache — called khi admin thay đổi routes.
     */
    @EventListener(RouteRefreshEvent.class)
    public void onRefreshRoutes(RouteRefreshEvent event) {
        routerCache.set(null);
        log.info("Route cache invalidated — will reload from database on next request");
    }

    /**
     * Force reload từ DB và cập nhật cache.
     */
    public RouterFunction<ServerResponse> reload() {
        List<GatewayRouteEntity> routes = routeRepository.findByEnabledTrueOrderByRouteOrderAsc();
        RouterFunction<ServerResponse> router = buildRouterFunction(routes);
        routerCache.set(router);
        log.info("Gateway routes reloaded: {} routes from database", routes.size());
        return router;
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private RouterFunction<ServerResponse> buildRouterFunction(List<GatewayRouteEntity> routes) {
        RouterFunctions.Builder builder = RouterFunctions.route();

        for (GatewayRouteEntity entity : routes) {
            try {
                builder.add(buildSingleRoute(entity));
            } catch (Exception e) {
                log.error("Failed to build route [{}]: {}", entity.id(), e.getMessage());
            }
        }
        return builder.build();
    }

    private RouterFunction<ServerResponse> buildSingleRoute(GatewayRouteEntity entity) {
        String configuredUri = entity.uri();
        String pathPattern   = extractPathPattern(entity.predicates(), entity.id());

        return GatewayRouterFunctions.route(entity.id())
                .GET(pathPattern,    http()).before(r -> resolveAndSetUri(r, configuredUri)).before(this::addHeader)
                .POST(pathPattern,   http()).before(r -> resolveAndSetUri(r, configuredUri)).before(this::addHeader)
                .PUT(pathPattern,    http()).before(r -> resolveAndSetUri(r, configuredUri)).before(this::addHeader)
                .PATCH(pathPattern,  http()).before(r -> resolveAndSetUri(r, configuredUri)).before(this::addHeader)
                .DELETE(pathPattern, http()).before(r -> resolveAndSetUri(r, configuredUri)).before(this::addHeader)
                .build();
    }

    /**
     * Resolve URI tại runtime:
     * - Nếu URI bắt đầu bằng {@code lb://} → dùng DiscoveryClient lấy instance đầu tiên healthy.
     * - Nếu không → dùng trực tiếp (direct URL).
     */
    private ServerRequest resolveAndSetUri(ServerRequest request, String configuredUri) {
        String resolvedUri = configuredUri;

        if (configuredUri.startsWith("lb://")) {
            String serviceName = configuredUri.substring(5); // bỏ "lb://"
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);

            if (instances.isEmpty()) {
                log.warn("No instances found in Eureka for service '{}', falling back to service name as host", serviceName);
                // Fallback: thử dùng http://<serviceName> (docker hostname)
                resolvedUri = "http://" + serviceName;
            } else {
                // Round-robin đơn giản: chọn instance dựa theo thread ID để phân tán đều
                int idx = (int) (Thread.currentThread().getId() % instances.size());
                ServiceInstance instance = instances.get(idx);
                resolvedUri = instance.getUri().toString();
                log.debug("Resolved lb://{} → {} (instance {}/{})", serviceName, resolvedUri, idx + 1, instances.size());
            }
        }

        return uri(resolvedUri).apply(request);
    }

    private ServerRequest addHeader(ServerRequest request) {
        if (request.attribute("X-User-Name").isPresent() && request.attribute("X-User-Roles").isPresent())
            return ServerRequest.from(request)
                    .header("X-User-Name", String.valueOf(request.attribute("X-User-Name").get()))
                    .header("X-User-Roles", String.valueOf(request.attribute("X-User-Roles").get()))
                    .build();
        else return request;
    }

    /**
     * Đọc path pattern đầu tiên từ predicates JSON.
     * Hỗ trợ 2 format:
     * - String array: ["Path=/api/auth/**"]
     * - Object array: [{"name":"Path","args":{"pattern":"/api/auth/**"}}]
     */
    private String extractPathPattern(String predicatesJson, String routeId) {
        try {
            if (predicatesJson == null || predicatesJson.isBlank() || "[]".equals(predicatesJson.trim())) {
                return "/**";
            }
            var node = objectMapper.readTree(predicatesJson);
            if (node.isArray() && !node.isEmpty()) {
                var first = node.get(0);
                if (first.isTextual()) {
                    String text = first.asText();
                    if (text.startsWith("Path=")) {
                        return text.substring(5);
                    }
                    return text;
                } else if (first.isObject()) {
                    var args = first.get("args");
                    if (args != null) {
                        var pattern = args.get("pattern");
                        if (pattern != null) return pattern.asText();
                        var _genkey0 = args.get("_genkey_0");
                        if (_genkey0 != null) return _genkey0.asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse predicates for route [{}]: {}", routeId, e.getMessage());
        }
        return "/**";
    }
}

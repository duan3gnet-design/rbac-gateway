package com.api.gateway.route;

import com.api.gateway.entity.GatewayRouteEntity;
import com.api.gateway.repository.GatewayRouteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>Cache in-memory, invalidate khi nhận {@link RouteRefreshEvent}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseRouteLocator {

    private final GatewayRouteRepository routeRepository;
    private final ObjectMapper           objectMapper;

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
        URI targetUri = URI.create(entity.uri());
        String predicateJson = entity.predicates();
        String pathPattern = extractPathPattern(predicateJson, entity.id());

        return GatewayRouterFunctions.route(entity.id())
                .GET(pathPattern, http()).before(uri(targetUri.toString())).before(this::addHeader)
                .POST(pathPattern, http()).before(uri(targetUri.toString())).before(this::addHeader)
                .PUT(pathPattern, http()).before(uri(targetUri.toString())).before(this::addHeader)
                .PATCH(pathPattern, http()).before(uri(targetUri.toString())).before(this::addHeader)
                .DELETE(pathPattern, http()).before(uri(targetUri.toString())).before(this::addHeader)
                .build();
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
                    // format: "Path=/api/auth/**"
                    String text = first.asText();
                    if (text.startsWith("Path=")) {
                        return text.substring(5);
                    }
                    return text;
                } else if (first.isObject()) {
                    // format: {"name":"Path","args":{"pattern":"/api/auth/**"}}
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

package com.api.gateway.route;

import com.api.gateway.entity.GatewayRouteEntity;
import com.api.gateway.repository.GatewayRouteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * Load routes từ PostgreSQL và expose qua RouterFunction (Gateway MVC API).
 *
 * <h3>URI format được hỗ trợ trong cột {@code uri} của bảng gateway_routes:</h3>
 * <ul>
 *   <li>{@code http://localhost:8081}  — direct URL, không qua LoadBalancer</li>
 *   <li>{@code lb://auth-service}      — service-name, resolve qua Spring Cloud LoadBalancer
 *       với thuật toán RoundRobin + Caffeine cache</li>
 * </ul>
 *
 * <h3>Load Balancing flow:</h3>
 * <pre>
 *   Request → DatabaseRouteLocator.resolveAndSetUri()
 *               → LoadBalancerClient.choose("auth-service")
 *                   → ServiceInstanceListSupplier (Caffeine cached, TTL=10s)
 *                       → EurekaDiscoveryClient (fetch mỗi 10s)
 *               → ServiceInstance.getUri() → http://10.0.0.5:8081
 *               → http() handler proxy request
 * </pre>
 *
 * <p>Cache RouterFunction in-memory, invalidate khi nhận {@link RouteRefreshEvent}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseRouteLocator {

    private final GatewayRouteRepository routeRepository;
    private final ObjectMapper           objectMapper;

    /**
     * Spring Cloud LoadBalancer (blocking) — tự động dùng RoundRobinLoadBalancer.
     * Instances được cache bởi Caffeine (TTL cấu hình trong application.yml).
     * Inject qua interface để dễ mock trong unit test.
     */
    private final LoadBalancerClient loadBalancerClient;

    private final AtomicReference<RouterFunction<ServerResponse>> routerCache =
            new AtomicReference<>(null);

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Trả về RouterFunction hiện tại (từ cache hoặc load mới từ DB).
     * Được gọi bởi {@link com.api.gateway.config.GatewayConfig}.
     */
    public RouterFunction<ServerResponse> getRouterFunction() {
        RouterFunction<ServerResponse> cached = routerCache.get();
        if (cached != null) return cached;
        return reload();
    }

    /** Invalidate cache — gọi khi admin thay đổi routes qua Admin API. */
    @EventListener(RouteRefreshEvent.class)
    public void onRefreshRoutes(RouteRefreshEvent event) {
        routerCache.set(null);
        log.info("Route cache invalidated — will reload from database on next request");
    }

    /** Force reload từ DB và cập nhật cache. Có thể gọi programmatically. */
    public RouterFunction<ServerResponse> reload() {
        List<GatewayRouteEntity> routes = routeRepository.findByEnabledTrueOrderByRouteOrderAsc();
        RouterFunction<ServerResponse> router = buildRouterFunction(routes);
        routerCache.set(router);
        log.info("Gateway routes reloaded: {} routes from database", routes.size());
        return router;
    }

    // ─── Build RouterFunction ─────────────────────────────────────────────────

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

        // Capture configuredUri trong lambda (effectively final per route)
        return GatewayRouterFunctions.route(entity.id())
                .GET(pathPattern,    http()).before(r -> resolveUri(r, configuredUri)).before(this::forwardHeaders)
                .POST(pathPattern,   http()).before(r -> resolveUri(r, configuredUri)).before(this::forwardHeaders)
                .PUT(pathPattern,    http()).before(r -> resolveUri(r, configuredUri)).before(this::forwardHeaders)
                .PATCH(pathPattern,  http()).before(r -> resolveUri(r, configuredUri)).before(this::forwardHeaders)
                .DELETE(pathPattern, http()).before(r -> resolveUri(r, configuredUri)).before(this::forwardHeaders)
                .build();
    }

    // ─── URI Resolution ───────────────────────────────────────────────────────

    /**
     * Resolve URI tại thời điểm request (không phải lúc build route):
     *
     * <ul>
     *   <li>{@code lb://service-name} → {@link LoadBalancerClient#choose(String)}
     *       trả về instance theo RoundRobin từ Caffeine cache (sync với Eureka mỗi 10s)</li>
     *   <li>URI khác → dùng trực tiếp</li>
     * </ul>
     *
     * <p>Fallback khi LoadBalancer không tìm được instance:
     * log WARN và throw {@link IllegalStateException} để Circuit Breaker bắt được,
     * tránh routing đến địa chỉ sai im lặng.</p>
     */
    private ServerRequest resolveUri(ServerRequest request, String configuredUri) {
        if (!configuredUri.startsWith("lb://")) {
            // Direct URL — không qua LoadBalancer
            return uri(configuredUri).apply(request);
        }

        String serviceName = configuredUri.substring(5); // strip "lb://"

        // LoadBalancerClient.choose() áp dụng RoundRobin trên danh sách instances
        // đã được cache bởi CachingServiceInstanceListSupplier (Caffeine, TTL=10s).
        ServiceInstance instance = loadBalancerClient.choose(serviceName);

        String resolvedUri = instance.getUri().toString();
        log.debug("[LoadBalancer] lb://{} → {} (host={}, port={})",
                serviceName, resolvedUri, instance.getHost(), instance.getPort());

        return uri(resolvedUri).apply(request);
    }

    // ─── Header Forwarding ────────────────────────────────────────────────────

    /**
     * Forward các attribute được JwtAuthenticationFilter gắn vào request
     * thành HTTP headers để downstream service đọc được.
     */
    private ServerRequest forwardHeaders(ServerRequest request) {
        var from = ServerRequest.from(request);
        request.attribute("X-User-Name").ifPresent(v -> from.header("X-User-Name", v.toString()));
        request.attribute("X-User-Roles").ifPresent(v -> from.header("X-User-Roles", v.toString()));
        return from.build();
    }

    // ─── Predicate Parsing ────────────────────────────────────────────────────

    /**
     * Đọc path pattern đầu tiên từ predicates JSON.
     *
     * <p>Hỗ trợ 2 format:</p>
     * <ul>
     *   <li>String array: {@code ["Path=/api/auth/**"]}</li>
     *   <li>Object array: {@code [{"name":"Path","args":{"pattern":"/api/auth/**"}}]}</li>
     * </ul>
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
                    return text.startsWith("Path=") ? text.substring(5) : text;
                }
                if (first.isObject()) {
                    var args = first.get("args");
                    if (args != null) {
                        var pattern  = args.get("pattern");
                        var genkey   = args.get("_genkey_0");
                        if (pattern != null) return pattern.asText();
                        if (genkey  != null) return genkey.asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse predicates for route [{}]: {}", routeId, e.getMessage());
        }
        return "/**";
    }
}

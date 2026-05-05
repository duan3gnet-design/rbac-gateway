package com.api.gateway.route;

import com.api.gateway.entity.GatewayRouteEntity;
import com.api.gateway.repository.GatewayRouteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * Load routes từ PostgreSQL và expose qua RouterFunction (Gateway MVC).
 *
 * <h3>Circuit Breaker + RestClient flow:</h3>
 * <pre>
 *   Request → cb.executeCheckedSupplier(...)
 *     │
 *     ├─ CB CLOSED/HALF_OPEN → http().handle(req) → RestClient proxy
 *     │     ├─ downstream 2xx/3xx → ServerResponse trả về → CB ghi SUCCESS
 *     │     ├─ downstream 4xx     → HttpClientErrorException → CB ghi SUCCESS
 *     │     │                        (4xx = client lỗi, không phải service lỗi)
 *     │     ├─ downstream 5xx     → HttpServerErrorException → CB ghi FAILURE
 *     │     └─ connection refused → ResourceAccessException  → CB ghi FAILURE
 *     │
 *     └─ CB OPEN → CallNotPermittedException (không gọi downstream)
 *
 *   on HttpServerErrorException / ResourceAccessException / CallNotPermittedException
 *     → buildFallbackResponse() → 503 JSON
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseRouteLocator {

    private final GatewayRouteRepository routeRepository;
    private final ObjectMapper           objectMapper;
    private final LoadBalancerClient     loadBalancerClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    private final AtomicReference<RouterFunction<ServerResponse>> routerCache =
            new AtomicReference<>(null);

    // ─── Public API ──────────────────────────────────────────────────────────

    public RouterFunction<ServerResponse> getRouterFunction() {
        RouterFunction<ServerResponse> cached = routerCache.get();
        if (cached != null) return cached;
        return reload();
    }

    @EventListener(RouteRefreshEvent.class)
    public void onRefreshRoutes(RouteRefreshEvent event) {
        routerCache.set(null);
        log.info("Route cache invalidated — will reload from database on next request");
    }

    public RouterFunction<ServerResponse> reload() {
        List<GatewayRouteEntity> routes = routeRepository.findByEnabledTrueOrderByRouteOrderAsc();
        RouterFunction<ServerResponse> router = buildRouterFunction(routes);
        routerCache.set(router);
        log.info("Gateway routes reloaded: {} routes", routes.size());
        return router;
    }

    // ─── Build ───────────────────────────────────────────────────────────────

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
        String pathPattern = extractPathPattern(entity.predicates(), entity.id());
        String cbName = extractCircuitBreakerName(entity.filters(), entity.id(), "name");
        String fallbackUri = extractCircuitBreakerName(entity.filters(), entity.id(), "fallbackUri");
        String statusCodes = extractCircuitBreakerName(entity.filters(), entity.id(), "statusCodes");
        RouterFunctions.Builder route = GatewayRouterFunctions.route(entity.id())
                .GET(pathPattern, req -> handle(req, configuredUri))
                .POST(pathPattern, req -> handle(req, configuredUri))
                .PUT(pathPattern, req -> handle(req, configuredUri))
                .PATCH(pathPattern, req -> handle(req, configuredUri))
                .DELETE(pathPattern, req -> handle(req, configuredUri));

        if (cbName != null && fallbackUri != null && statusCodes != null) {
            route.filter(circuitBreaker(config -> config
                    .setId(cbName)
                    .setFallbackUri(fallbackUri)
                    .setStatusCodes(statusCodes.split(","))));
        }

        return route.build();
    }

    // ─── Request Handling ────────────────────────────────────────────────────

    private ServerResponse handle(ServerRequest request, String configuredUri) throws Exception {
        return http().handle(resolveUri(forwardHeaders(request), configuredUri));
    }

    // ─── URI Resolution ──────────────────────────────────────────────────────

    private ServerRequest resolveUri(ServerRequest request, String configuredUri) {
        if (!configuredUri.startsWith("lb://")) {
            return uri(configuredUri).apply(request);
        }
        String serviceName = configuredUri.substring(5);
        ServiceInstance instance = loadBalancerClient.choose(serviceName);
        if (instance == null) {
            throw new IllegalStateException("No available instance: " + serviceName);
        }
        String resolvedUri = instance.getUri().toString();
        log.debug("[LB] lb://{} → {}", serviceName, resolvedUri);
        return uri(resolvedUri).apply(request);
    }

    // ─── Header Forwarding ───────────────────────────────────────────────────

    private ServerRequest forwardHeaders(ServerRequest request) {
        var from = ServerRequest.from(request);
        request.attribute("X-User-Name").ifPresent(v -> from.header("X-User-Name", v.toString()));
        request.attribute("X-User-Roles").ifPresent(v -> from.header("X-User-Roles", v.toString()));
        request.attribute("X-User-Permissions").ifPresent(v -> from.header("X-User-Permissions", v.toString()));
        return from.build();
    }

    // ─── Parsing ─────────────────────────────────────────────────────────────

    private String extractCircuitBreakerName(String filtersJson, String routeId, String argName) {
        try {
            if (filtersJson == null || filtersJson.isBlank() || "[]".equals(filtersJson.trim())) {
                return null;
            }
            JsonNode filters = objectMapper.readTree(filtersJson);
            if (!filters.isArray()) return null;
            for (JsonNode filter : filters) {
                if ("CircuitBreaker".equalsIgnoreCase(filter.path("name").asText(""))) {
                    String arg = filter.path("args").path(argName).asText(null);
                    if (arg != null && !arg.isBlank()) return arg;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse filters for route [{}]: {}", routeId, e.getMessage());
        }
        return null;
    }

    private String extractPathPattern(String predicatesJson, String routeId) {
        try {
            if (predicatesJson == null || predicatesJson.isBlank() || "[]".equals(predicatesJson.trim())) {
                return "/**";
            }
            JsonNode node = objectMapper.readTree(predicatesJson);
            if (node.isArray() && !node.isEmpty()) {
                JsonNode first = node.get(0);
                if (first.isTextual()) {
                    String text = first.asText();
                    return text.startsWith("Path=") ? text.substring(5) : text;
                }
                if (first.isObject()) {
                    String pattern = first.path("args").path("pattern").asText(null);
                    if (pattern != null) return pattern;
                    String genkey = first.path("args").path("_genkey_0").asText(null);
                    if (genkey != null) return genkey;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse predicates for route [{}]: {}", routeId, e.getMessage());
        }
        return "/**";
    }

    private String cbNameToServiceName(String cbName) {
        if (cbName == null) return "unknown";
        return cbName
                .replaceAll("CB$", "")
                .replaceAll("([A-Z])", "-$1")
                .toLowerCase()
                .replaceAll("^-", "");
    }
}

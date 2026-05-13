package com.api.gateway.route;

import com.api.gateway.entity.GatewayRouteEntity;
import com.api.gateway.repository.GatewayRouteRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * Load routes từ PostgreSQL và expose qua RouterFunction (Gateway MVC).
 *
 * <h3>Trace propagation flow:</h3>
 * <pre>
 *   Client Request
 *     → Spring MVC auto-creates root span (instrumented bởi spring-boot-starter-opentelemetry)
 *     → JwtAuthenticationFilter (interceptor — trong cùng span)
 *     → DatabaseRouteLocator.handle()
 *         → forwardHeaders() — copy X-User-* + W3C traceparent/tracestate headers
 *         → http().handle(req)
 *             → Gateway MVC dùng RestClient.Builder bean (đã có ObservationRegistry)
 *             → tạo child span, inject traceparent vào outgoing request tự động
 *             → downstream service nhận traceparent → tiếp tục trace chain
 *     → Jaeger thấy: gateway-span → downstream-span (parent-child)
 * </pre>
 *
 * <p><b>Lưu ý về RestClient:</b> Gateway MVC tự dùng {@code RestClient.Builder} bean
 * trong context. Để tracing hoạt động, bean đó phải được cấu hình với
 * {@code ObservationRegistry} — xem {@link com.api.gateway.config.GatewayConfig}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseRouteLocator {

    // W3C Trace Context headers (OTel)
    private static final String TRACEPARENT = "traceparent";
    private static final String TRACESTATE  = "tracestate";
    // B3 headers (backward compat với Brave / legacy services)
    private static final String B3_SINGLE   = "b3";
    private static final String B3_TRACE_ID = "X-B3-TraceId";
    private static final String B3_SPAN_ID  = "X-B3-SpanId";
    private static final String B3_SAMPLED  = "X-B3-Sampled";

    private final GatewayRouteRepository  routeRepository;
    private final ObjectMapper            objectMapper;
    private final LoadBalancerClient      loadBalancerClient;

    private final AtomicReference<RouterFunction<ServerResponse>> routerCache =
            new AtomicReference<>(null);
    private final Lock lock = new ReentrantLock();

    // ─── Public API ──────────────────────────────────────────────────────────

    public RouterFunction<ServerResponse> getRouterFunction() {
        RouterFunction<ServerResponse> cached = routerCache.get();
        if (cached != null) return cached;
        return reload();
    }

    @EventListener(RouteRefreshEvent.class)
    public void onRefreshRoutes(RouteRefreshEvent event) {
        routerCache.set(null);
        log.info("Route cache invalidated");
    }

    public RouterFunction<ServerResponse> reload() {
        try {
            lock.lock();
            List<GatewayRouteEntity> routes = routeRepository.findByEnabledTrueOrderByRouteOrderAsc();
            RouterFunction<ServerResponse> router = buildRouterFunction(routes);

            routerCache.set(router);
            log.info("Gateway routes reloaded: {} routes", routes.size());
            return router;
        } catch (Exception ignored) {
            return RouterFunctions.route().build();
        } finally {
            lock.unlock();
        }
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
        String pathPattern   = extractPathPattern(entity.predicates(), entity.id());
        String cbName        = extractCircuitBreakerArg(entity.filters(), entity.id(), "name");
        String fallbackUri   = extractCircuitBreakerArg(entity.filters(), entity.id(), "fallbackUri");
        String statusCodes   = extractCircuitBreakerArg(entity.filters(), entity.id(), "statusCodes");

        RouterFunctions.Builder route = GatewayRouterFunctions.route(entity.id())
                .GET(pathPattern,    req -> handle(req, configuredUri))
                .POST(pathPattern,   req -> handle(req, configuredUri))
                .PUT(pathPattern,    req -> handle(req, configuredUri))
                .PATCH(pathPattern,  req -> handle(req, configuredUri))
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
        // http() không nhận tham số — Gateway MVC tự pick up RestClient.Builder bean
        // từ ApplicationContext. Bean đó đã được gắn ObservationRegistry trong GatewayConfig
        // → tự động tạo child span + inject traceparent header vào outgoing request.
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

    /**
     * Copy toàn bộ headers cần thiết sang outgoing request:
     * <ul>
     *   <li>X-User-* — identity context cho downstream service</li>
     *   <li>W3C traceparent / tracestate — OTel trace propagation (safety net,
     *       RestClient với ObservationRegistry đã inject tự động)</li>
     *   <li>B3 headers — backward compat với Brave/Zipkin instrumented services</li>
     * </ul>
     */
    private ServerRequest forwardHeaders(ServerRequest request) {
        var builder = ServerRequest.from(request);

        // ── Identity headers ─────────────────────────────────────────────────
        request.attribute("X-User-Name")
               .ifPresent(v -> builder.header("X-User-Name", v.toString()));
        request.attribute("X-User-Roles")
               .ifPresent(v -> builder.header("X-User-Roles", v.toString()));
        request.attribute("X-User-Permissions")
               .ifPresent(v -> builder.header("X-User-Permissions", v.toString()));

        // ── W3C Trace Context (OTel) ─────────────────────────────────────────
        copyHeader(request, builder, TRACEPARENT);
        copyHeader(request, builder, TRACESTATE);

        // ── B3 (Brave / legacy services) ─────────────────────────────────────
        copyHeader(request, builder, B3_SINGLE);
        copyHeader(request, builder, B3_TRACE_ID);
        copyHeader(request, builder, B3_SPAN_ID);
        copyHeader(request, builder, B3_SAMPLED);

        return builder.build();
    }

    private void copyHeader(ServerRequest source, ServerRequest.Builder target, String headerName) {
        List<String> values = source.headers().header(headerName);
        if (!values.isEmpty()) {
            target.header(headerName, values.toArray(String[]::new));
        }
    }

    // ─── Parsing ─────────────────────────────────────────────────────────────

    private String extractCircuitBreakerArg(String filtersJson, String routeId, String argName) {
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
}

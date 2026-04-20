package com.api.gateway.route;

import com.api.gateway.entity.GatewayRouteEntity;
import com.api.gateway.repository.GatewayRouteR2dbcRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

/**
 * Implement RouteDefinitionRepository để Spring Cloud Gateway load routes từ PostgreSQL
 * thay vì từ file application.yml.
 *
 * <p>Flow: Gateway gọi getRouteDefinitions() → query DB qua R2DBC → parse JSON → RouteDefinition.</p>
 *
 * <p>Để refresh route động (không restart), gọi actuator endpoint:
 * POST /actuator/gateway/refresh</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseRouteDefinitionRepository implements RouteDefinitionRepository {

    private final GatewayRouteR2dbcRepository routeRepository;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<PredicateDefinition>> PREDICATE_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<List<FilterDefinition>> FILTER_TYPE =
            new TypeReference<>() {};

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return routeRepository.findByEnabledTrueOrderByRouteOrderAsc()
                .flatMap(entity -> Mono.fromCallable(() -> toRouteDefinition(entity))
                        .doOnError(e -> log.error("Failed to parse route [{}]: {}", entity.id(), e.getMessage()))
                        .onErrorResume(e -> Mono.empty()))
                .doOnSubscribe(s -> log.debug("Loading gateway routes from database..."))
                .doOnComplete(() -> log.info("Gateway routes loaded from database"));
    }

    /**
     * Save không cần thiết cho use-case load-only.
     * Nếu muốn hỗ trợ tạo route động qua API thì implement tại đây.
     */
    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return Mono.error(new UnsupportedOperationException(
                "Dynamic route save not supported. Use database migration to add routes."));
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id ->
                routeRepository.findById(id)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Route not found: " + id)))
                        .flatMap(routeRepository::delete));
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private RouteDefinition toRouteDefinition(GatewayRouteEntity entity) throws Exception {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(entity.id());
        definition.setUri(URI.create(entity.uri()));
        definition.setOrder(entity.routeOrder());
        definition.setPredicates(parsePredicates(entity.predicates()));
        definition.setFilters(parseFilters(entity.filters()));
        return definition;
    }

    private List<PredicateDefinition> parsePredicates(String json) throws Exception {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        // Support cả 2 format:
        //   1. [{"name":"Path","args":{"pattern":"/api/**"}}]  — object style
        //   2. ["Path=/api/**"]                                — shortcut string style
        var node = objectMapper.readTree(json);
        if (node.isArray() && !node.isEmpty() && node.get(0).isTextual()) {
            // Shortcut format: Spring Gateway sẽ parse string "Path=/api/**" thành PredicateDefinition
            return objectMapper.convertValue(
                    node,
                    new TypeReference<List<String>>() {}
            ).stream()
                    .map(PredicateDefinition::new)
                    .toList();
        }
        return objectMapper.readValue(json, PREDICATE_TYPE);
    }

    private List<FilterDefinition> parseFilters(String json) throws Exception {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        var node = objectMapper.readTree(json);
        if (node.isArray() && !node.isEmpty() && node.get(0).isTextual()) {
            return objectMapper.convertValue(
                    node,
                    new TypeReference<List<String>>() {}
            ).stream()
                    .map(FilterDefinition::new)
                    .toList();
        }
        return objectMapper.readValue(json, FILTER_TYPE);
    }
}

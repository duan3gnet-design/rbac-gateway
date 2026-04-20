package com.api.gateway.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * R2DBC entity ánh xạ bảng gateway_routes.
 * predicates / filters lưu dạng JSON text, parse trong DatabaseRouteDefinitionRepository.
 */
@Table("gateway_routes")
public record GatewayRouteEntity(

        @Id
        String id,

        String uri,

        /* JSON array: [{"name":"Path","args":{"pattern":"/api/**"}}] */
        String predicates,

        /* JSON array: [{"name":"CircuitBreaker","args":{...}}] */
        String filters,

        @Column("route_order")
        int routeOrder,

        boolean enabled,

        @Column("created_at")
        OffsetDateTime createdAt,

        @Column("updated_at")
        OffsetDateTime updatedAt
) {}

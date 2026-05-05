package com.api.gateway.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * Spring Data JDBC entity ánh xạ bảng gateway_routes.
 * predicates / filters lưu dạng JSON text, parse trong DatabaseRouteLocator.
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
        OffsetDateTime updatedAt,

        /**
         * Flag đánh dấu entity mới cần INSERT.
         * {@code @Transient} để R2DBC không map field này vào bảng DB.
         * Mặc định {@code false} (existing) — set {@code true} khi tạo mới.
         */
        @Transient
        boolean isNew

) implements Persistable<String> {

        /** Constructor dùng cho load từ DB — isNew = false (existing). */
        @PersistenceCreator
        public GatewayRouteEntity(
                String id, String uri, String predicates, String filters,
                int routeOrder, boolean enabled,
                OffsetDateTime createdAt, OffsetDateTime updatedAt) {
                this(id, uri, predicates, filters, routeOrder, enabled,
                        createdAt, updatedAt, false);
        }

        @Override
        public String getId() {
                return id;
        }

        @Override
        public boolean isNew() {
                return isNew;
        }
}

package com.api.gateway.admin.ratelimit;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * Spring Data JDBC entity ánh xạ bảng rate_limit_config.
 */
@Table("rate_limit_config")
public record RateLimitConfigEntity(

        @Id
        Long id,

        /** NULL = global default; non-null = per-user override */
        String username,

        @Column("replenish_rate")
        int replenishRate,

        @Column("burst_capacity")
        int burstCapacity,

        boolean enabled,

        String description,

        @Column("created_at")
        OffsetDateTime createdAt,

        @Column("updated_at")
        OffsetDateTime updatedAt
) {}

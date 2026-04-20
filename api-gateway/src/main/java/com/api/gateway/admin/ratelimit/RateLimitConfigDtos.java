package com.api.gateway.admin.ratelimit;

import java.time.OffsetDateTime;

/**
 * DTOs cho Rate Limit Config Admin API.
 */
public final class RateLimitConfigDtos {

    private RateLimitConfigDtos() {}

    /** Request tạo per-user override mới */
    public record CreateRequest(
            String username,         // null = tạo/update global default
            int    replenishRate,
            int    burstCapacity,
            String description
    ) {}

    /** Request update (partial — chỉ các field không null mới được update) */
    public record UpdateRequest(
            Integer replenishRate,   // null = không đổi
            Integer burstCapacity,   // null = không đổi
            Boolean enabled,         // null = không đổi
            String  description      // null = không đổi
    ) {}

    /** Response trả về client */
    public record ConfigResponse(
            Long           id,
            String         username,       // null = global default
            int            replenishRate,
            int            burstCapacity,
            boolean        enabled,
            String         description,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}

package com.resource.service.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String username,
        String status,
        BigDecimal total,
        List<OrderItemResponse> items,
        OffsetDateTime createdAt
) {
    public record OrderItemResponse(
            Long productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice
    ) {}
}

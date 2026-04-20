package com.resource.service.dto;

import java.util.List;

public record PlaceOrderRequest(
        List<OrderItemRequest> items
) {
    public record OrderItemRequest(
            Long productId,
            Integer quantity
    ) {}
}

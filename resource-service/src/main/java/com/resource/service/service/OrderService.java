package com.resource.service.service;

import com.resource.service.dto.OrderResponse;
import com.resource.service.dto.OrderResponse.OrderItemResponse;
import com.resource.service.dto.PlaceOrderRequest;
import com.resource.service.entity.Order;
import com.resource.service.entity.OrderItem;
import com.resource.service.exception.ResourceNotFoundException;
import com.resource.service.repository.OrderRepository;
import com.resource.service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<OrderResponse> findByUsername(String username) {
        return orderRepository.findAllByUsername(username, PageRequest.of(0, 10)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return orderRepository.findAll(PageRequest.of(0, 10)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse placeOrder(String username, PlaceOrderRequest request) {
        Order order = new Order();
        order.setUsername(username);
        order.setStatus("CREATED");
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());

        BigDecimal total = BigDecimal.ZERO;

        for (PlaceOrderRequest.OrderItemRequest itemReq : request.items()) {
            var product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemReq.productId()));

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setQuantity(itemReq.quantity());
            item.setUnitPrice(product.getPrice());

            order.getItems().add(item);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(itemReq.quantity())));
        }

        order.setTotal(total);
        return toResponse(orderRepository.save(order));
    }

    // ── mapper ────────────────────────────────────────────────

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getQuantity(),
                        i.getUnitPrice()))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUsername(),
                order.getStatus(),
                order.getTotal(),
                items,
                order.getCreatedAt());
    }
}

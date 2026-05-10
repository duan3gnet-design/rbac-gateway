package com.api.gateway.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publish {@link RouteRefreshMessage} lên Redis Pub/Sub channel.
 *
 * <p>Được gọi từ {@link com.api.gateway.admin.route.AdminRouteService}
 * thay cho (hoặc kết hợp với) Spring {@code ApplicationEventPublisher}.
 * Tất cả nodes đang subscribe channel đều nhận được message,
 * kể cả node hiện tại.</p>
 *
 * <h3>Channel name</h3>
 * <p>Mặc định: {@code gateway:route-refresh}. Override qua property
 * {@code gateway.route-refresh.channel} nếu cần tách môi trường.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteRefreshPublisher {

    public static final String DEFAULT_CHANNEL = "gateway:route-refresh";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Value("${spring.application.instance-id:${random.uuid}}")
    private String nodeId;

    @Value("${gateway.route-refresh.channel:" + DEFAULT_CHANNEL + "}")
    private String channel;

    /**
     * Serialize message → JSON và publish lên Redis channel.
     *
     * <p>Nếu Redis không khả dụng, method log warning thay vì throw,
     * để không block admin operation (route đã được lưu vào DB rồi).</p>
     *
     * @param action  "create" | "update" | "delete" | "toggle" | "assign-permissions"
     * @param routeId ID của route bị thay đổi
     */
    public void publish(String action, String routeId) {
        try {
            RouteRefreshMessage message = RouteRefreshMessage.of(nodeId, action, routeId);
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json);
            log.info("[RouteRefreshPublisher] Published '{}' for route [{}] on channel '{}'",
                    action, routeId, channel);
        } catch (JsonProcessingException e) {
            log.error("[RouteRefreshPublisher] Failed to serialize message", e);
        } catch (Exception e) {
            // Redis unavailable — không nên crash admin API
            log.warn("[RouteRefreshPublisher] Failed to publish to Redis channel '{}': {}",
                    channel, e.getMessage());
        }
    }
}

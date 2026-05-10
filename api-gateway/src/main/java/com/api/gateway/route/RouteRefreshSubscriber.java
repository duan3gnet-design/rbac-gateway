package com.api.gateway.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Subscribe Redis Pub/Sub channel và kích hoạt invalidation local cache
 * khi nhận được {@link RouteRefreshMessage}.
 *
 * <p>Flow:</p>
 * <pre>
 *   [Any Node] AdminRouteService
 *       → RouteRefreshPublisher.publish()
 *           → Redis channel "gateway:route-refresh"
 *               → RouteRefreshSubscriber.onMessage()  ← chạy trên MỌI node
 *                   → ApplicationEventPublisher.publishEvent(RouteRefreshEvent)
 *                       → DatabaseRouteLocator.onRefreshRoutes()    (invalidate route cache)
 *                       → RbacPermissionChecker.onRefreshRoutes()   (invalidate rules cache)
 * </pre>
 *
 * <p>Subscriber dùng lại Spring {@link RouteRefreshEvent} để không phải
 * sửa {@code DatabaseRouteLocator} và {@code RbacPermissionChecker} —
 * hai class đó vẫn lắng nghe {@code @EventListener(RouteRefreshEvent.class)}
 * như cũ, hoàn toàn không biết nguồn event là local hay Redis.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteRefreshSubscriber implements MessageListener {

    private final ObjectMapper           objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Được gọi bởi Spring Redis Listener container khi có message mới.
     * Deserialize JSON → publish Spring event để invalidate local cache.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            RouteRefreshMessage msg = objectMapper.readValue(json, RouteRefreshMessage.class);

            log.info("[RouteRefreshSubscriber] Received refresh signal from node [{}]: action='{}', route='{}'",
                    msg.originNodeId(), msg.action(), msg.routeId());

            // Publish Spring ApplicationEvent → kích hoạt tất cả @EventListener local
            eventPublisher.publishEvent(new RouteRefreshEvent(this));

        } catch (Exception e) {
            log.error("[RouteRefreshSubscriber] Failed to process message: {}", e.getMessage(), e);
        }
    }
}

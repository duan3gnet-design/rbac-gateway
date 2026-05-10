package com.api.gateway.config;

import com.api.gateway.route.RouteRefreshPublisher;
import com.api.gateway.route.RouteRefreshSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Cấu hình Redis Pub/Sub cho distributed route cache invalidation.
 *
 * <h3>Tại sao không dùng {@code @EventListener} thuần?</h3>
 * <p>Spring {@code ApplicationEvent} chỉ hoạt động trong cùng JVM (in-process).
 * Khi scale horizontally (nhiều node), admin update route trên node A
 * → chỉ node A nhận event, node B/C vẫn giữ cache cũ → stale routes.</p>
 *
 * <h3>Giải pháp Redis Pub/Sub</h3>
 * <ul>
 *   <li>Admin gọi API → {@link com.api.gateway.admin.route.AdminRouteService}
 *       publish message lên Redis channel.</li>
 *   <li>Mọi node subscribe channel (kể cả node vừa publish) nhận message
 *       qua {@link RouteRefreshSubscriber}.</li>
 *   <li>Subscriber publish local Spring {@code RouteRefreshEvent} →
 *       {@code DatabaseRouteLocator} và {@code RbacPermissionChecker}
 *       invalidate cache như bình thường.</li>
 * </ul>
 *
 * <h3>Thread model</h3>
 * <p>{@code RedisMessageListenerContainer} dùng thread pool riêng (default 1 thread)
 * để nhận messages — không block request thread. Với Virtual Threads đã bật,
 * container tự dùng virtual thread executor nếu không config tường minh.</p>
 */
@Configuration
public class RedisRouteRefreshConfig {

    @Value("${gateway.route-refresh.channel:" + RouteRefreshPublisher.DEFAULT_CHANNEL + "}")
    private String channel;

    /**
     * Adapter wrap {@link RouteRefreshSubscriber#onMessage} để Spring
     * Redis listener container có thể gọi được.
     */
    @Bean
    public MessageListenerAdapter routeRefreshListenerAdapter(RouteRefreshSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    /**
     * Container quản lý kết nối Redis và dispatch message đến listener.
     * Tự động reconnect khi Redis khởi động lại.
     */
    @Bean
    public RedisMessageListenerContainer routeRefreshListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter routeRefreshListenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(routeRefreshListenerAdapter, new ChannelTopic(channel));
        return container;
    }
}

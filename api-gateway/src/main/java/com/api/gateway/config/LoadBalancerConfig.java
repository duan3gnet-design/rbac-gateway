package com.api.gateway.config;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.RandomLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Spring Cloud LoadBalancer — cấu hình global và per-service.
 *
 * <h3>Kiến trúc LoadBalancer trong project này:</h3>
 * <pre>
 *   DatabaseRouteLocator
 *     └─ LoadBalancerClient.choose(serviceName)           ← blocking API
 *           └─ BlockingLoadBalancerClient
 *                 └─ RoundRobinLoadBalancer               ← algorithm
 *                       └─ CachingServiceInstanceListSupplier  ← Caffeine cache TTL=10s
 *                             └─ DiscoveryClientServiceInstanceListSupplier
 *                                   └─ EurekaDiscoveryClient  ← fetch mỗi 10s
 * </pre>
 *
 * <h3>Algorithm được dùng:</h3>
 * <ul>
 *   <li><b>auth-service</b>: {@link RoundRobinLoadBalancer} — phân tán đều, phù hợp
 *       vì auth request có chi phí tương đương nhau</li>
 *   <li><b>resource-service</b>: {@link RoundRobinLoadBalancer} — default</li>
 *   <li>Để đổi sang Random: thay {@code roundRobinLoadBalancer()} bằng {@code randomLoadBalancer()}</li>
 * </ul>
 *
 * <p>Annotation {@link LoadBalancerClients} ở đây chỉ để document — cấu hình
 * thực tế (cache TTL, health-check interval) đặt trong application.yml
 * dưới {@code spring.cloud.loadbalancer}.</p>
 */
@LoadBalancerClients(defaultConfiguration = LoadBalancerConfig.DefaultConfig.class)
public class LoadBalancerConfig {

    /**
     * Default configuration áp dụng cho tất cả services.
     * Spring Cloud tạo một ApplicationContext con per-service,
     * bean này sẽ được load trong context đó.
     */
    public static class DefaultConfig {

        /**
         * RoundRobinLoadBalancer — phân phối request tuần tự qua các instance.
         *
         * <p>Dùng {@link RoundRobinLoadBalancer} thay vì {@link RandomLoadBalancer}
         * vì với số instances nhỏ (2–5), round-robin đảm bảo phân tán đều hơn
         * random trong khoảng ngắn.</p>
         *
         * <p>Nếu muốn đổi sang Random cho 1 service cụ thể, tạo class config riêng
         * và dùng {@code @LoadBalancerClient(name="...", configuration=...)}.</p>
         */
        @Bean
        public ReactorLoadBalancer<ServiceInstance> roundRobinLoadBalancer(
                Environment environment,
                LoadBalancerClientFactory loadBalancerClientFactory) {

            String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
            return new RoundRobinLoadBalancer(
                    loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
                    name
            );
        }
    }
}

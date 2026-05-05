package com.api.gateway.config;

import com.api.gateway.route.DatabaseRouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;

@Configuration
public class GatewayConfig {

    /**
     * RouterFunction delegate: mỗi request gọi {@code locator.getRouterFunction()}
     * để lấy instance hiện tại từ cache của {@link DatabaseRouteLocator}.
     *
     * <h3>Tại sao KHÔNG dùng @RefreshScope?</h3>
     * <p>{@code @RefreshScope} chỉ recreate bean khi {@code ContextRefresher.refresh()}
     * được gọi (Spring Cloud Config refresh). Nó không liên quan đến
     * {@link com.api.gateway.route.RouteRefreshEvent} — sau khi admin update route,
     * bean này vẫn giữ {@code RouterFunction} cũ đã được inject lúc khởi tạo,
     * bỏ qua hoàn toàn cache invalidation của {@code DatabaseRouteLocator}.</p>
     *
     * <h3>Giải pháp: RouterFunction delegate</h3>
     * <p>Thay vì inject {@code RouterFunction} một lần, bean này là một lambda
     * mỏng delegate sang {@code locator.getRouterFunction()} mỗi khi
     * {@code DispatcherServlet} gọi {@code route(request)}.
     * {@code DatabaseRouteLocator.getRouterFunction()} trả về cached instance
     * nếu còn hợp lệ, hoặc {@code reload()} từ DB nếu cache đã bị null
     * bởi {@code onRefreshRoutes()}.</p>
     *
     * <h3>Performance</h3>
     * <p>Overhead của delegate là một {@code AtomicReference.get()} — nanoseconds,
     * hoàn toàn chấp nhận được so với cost của DB query + HTTP proxy.</p>
     */
    @Bean
    public RouterFunction<ServerResponse> gatewayRouterFunctions(DatabaseRouteLocator locator) {
        // Lambda này được Spring đăng ký một lần, nhưng body của nó
        // gọi locator.getRouterFunction() tại thời điểm xử lý request —
        // không phải lúc khởi tạo bean. Đây là điểm mấu chốt.
        return request -> locator.getRouterFunction().route(request);
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .executor(Executors.newVirtualThreadPerTaskExecutor())
                                .build()));
    }
}

package com.api.gateway.config;

import com.api.gateway.route.DatabaseRouteLocator;
import io.micrometer.observation.ObservationRegistry;
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
     */
    @Bean
    public RouterFunction<ServerResponse> gatewayRouterFunctions(DatabaseRouteLocator locator) {
        return request -> locator.getRouterFunction().route(request);
    }

    /**
     * RestClient.Builder được share cho toàn bộ gateway proxy.
     *
     * <p><b>Quan trọng — tracing propagation:</b> {@code observationRegistry(...)} bắt buộc
     * để RestClient tự động:</p>
     * <ol>
     *   <li>Tạo child span cho mỗi outgoing HTTP call (gateway → downstream service)</li>
     *   <li>Inject {@code traceparent} / {@code tracestate} / {@code b3} header vào request
     *       → downstream service nhận được và tiếp tục trace chain</li>
     * </ol>
     * <p>Nếu thiếu {@code observationRegistry}, mỗi downstream call sẽ là một span riêng lẻ
     * không liên kết với trace gốc — Jaeger sẽ thấy các trace rời rạc, không có parent-child.</p>
     */
    @Bean
    public RestClient.Builder restClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .executor(Executors.newVirtualThreadPerTaskExecutor())
                                .build()))
                .observationRegistry(observationRegistry);
    }
}

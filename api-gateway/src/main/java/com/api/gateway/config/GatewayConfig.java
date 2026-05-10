package com.api.gateway.config;

import com.api.gateway.route.DatabaseRouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

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
}

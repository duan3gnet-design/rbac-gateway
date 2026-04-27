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

    @Bean
    public RouterFunction<ServerResponse> gatewayRouterFunctions(DatabaseRouteLocator databaseRouteLocator) {
        return databaseRouteLocator.getRouterFunction();
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

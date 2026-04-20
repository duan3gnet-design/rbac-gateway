package com.resource.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Đăng ký WebClient.Builder thủ công vì resource-service dùng servlet stack
     * (spring-boot-starter-web), không phải reactive stack (spring-boot-starter-webflux).
     *
     * Trong servlet stack, WebClientAutoConfiguration không chạy →
     * WebClient.Builder bean không được Spring Boot tự tạo →
     * phải khai báo tường minh ở đây.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}

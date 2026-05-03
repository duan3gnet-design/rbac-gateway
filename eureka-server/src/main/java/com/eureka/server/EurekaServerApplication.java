package com.eureka.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Service Registry.
 *
 * <p>Chạy độc lập trên port 8761. Tất cả microservices đăng ký tại đây
 * để Gateway có thể resolve service name → instance URL khi routing.</p>
 *
 * <p>Dashboard: http://localhost:8761</p>
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}

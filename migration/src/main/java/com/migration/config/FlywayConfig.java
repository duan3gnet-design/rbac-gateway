package com.migration.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flywayAuth(
            @Value("${auth.datasource.url}") String url,
            @Value("${auth.datasource.username}") String username,
            @Value("${auth.datasource.password}") String password) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/auth")
                .schemas("public")
                .baselineOnMigrate(true)
                .load();
    }

    @Bean(initMethod = "migrate")
    public Flyway flywayResource(
            @Value("${resource.datasource.url}") String url,
            @Value("${resource.datasource.username}") String username,
            @Value("${resource.datasource.password}") String password) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/resource")
                .schemas("public")
                .baselineOnMigrate(true)
                .load();
    }

    @Bean(initMethod = "migrate")
    public Flyway flywayGateway(
            @Value("${gateway.datasource.url}") String url,
            @Value("${gateway.datasource.username}") String username,
            @Value("${gateway.datasource.password}") String password) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/gateway")
                .schemas("public")
                .baselineOnMigrate(true)
                .load();
    }
}

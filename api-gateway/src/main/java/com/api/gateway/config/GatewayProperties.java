package com.api.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.service")
public record GatewayProperties(String url) {
}
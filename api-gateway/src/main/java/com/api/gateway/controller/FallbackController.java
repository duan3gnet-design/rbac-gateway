package com.api.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    // Dùng @RequestMapping thay vì @GetMapping để nhận mọi HTTP method
    // Circuit Breaker forward cùng method gốc (POST login → POST /fallback/auth)
    @RequestMapping("/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authFallback(ServerWebExchange exchange) {
        return buildFallbackResponse("auth-service", exchange);
    }

    @RequestMapping("/resource")
    public Mono<ResponseEntity<Map<String, Object>>> resourceFallback(ServerWebExchange exchange) {
        return buildFallbackResponse("resource-service", exchange);
    }

    private Mono<ResponseEntity<Map<String, Object>>> buildFallbackResponse(
            String serviceName, ServerWebExchange exchange) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 503);
        body.put("error", "Service Unavailable");
        body.put("message", "Service '" + serviceName + "' is currently unavailable. Please try again later.");
        body.put("timestamp", Instant.now().toString());
        body.put("path", exchange.getRequest().getPath().value());

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
    }
}

package com.api.gateway.controller;

import io.micrometer.core.instrument.Counter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private final Counter circuitBreakerFallbackCounter;

    public FallbackController(
            @Qualifier("circuitBreakerFallbackCounter") Counter circuitBreakerFallbackCounter) {
        this.circuitBreakerFallbackCounter = circuitBreakerFallbackCounter;
    }

    @RequestMapping("/auth")
    public ResponseEntity<Map<String, Object>> authFallback(HttpServletRequest request) {
        log.warn("[CircuitBreaker] Fallback triggered for auth-service: {}", request.getRequestURI());
        return buildFallbackResponse("auth-service", request.getRequestURI());
    }

    @RequestMapping("/resource")
    public ResponseEntity<Map<String, Object>> resourceFallback(HttpServletRequest request) {
        log.warn("[CircuitBreaker] Fallback triggered for resource-service: {}", request.getRequestURI());
        return buildFallbackResponse("resource-service", request.getRequestURI());
    }

    @RequestMapping("/service/{serviceName}")
    public ResponseEntity<Map<String, Object>> fallback(HttpServletRequest request, @PathVariable String serviceName) {
        log.warn("[CircuitBreaker] Fallback triggered for {}: {}", serviceName, request.getRequestURI());
        return buildFallbackResponse(serviceName, request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> buildFallbackResponse(String serviceName, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 503);
        body.put("error", "Service Unavailable");
        body.put("message", "Service '" + serviceName + "' is currently unavailable. Please try again later.");
        body.put("timestamp", Instant.now().toString());
        body.put("path", path);

        circuitBreakerFallbackCounter.increment();

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}

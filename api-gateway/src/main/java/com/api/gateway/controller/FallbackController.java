package com.api.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/auth")
    public ResponseEntity<Map<String, Object>> authFallback(HttpServletRequest request) {
        log.info("fallback auth: {}", request.getRequestURI());
        return buildFallbackResponse("auth-service", request.getRequestURI());
    }

    @RequestMapping("/resource")
    public ResponseEntity<Map<String, Object>> resourceFallback(HttpServletRequest request) {
        log.info("fallback resource: {}", request.getRequestURI());
        return buildFallbackResponse("resource-service", request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> buildFallbackResponse(String serviceName, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 503);
        body.put("error", "Service Unavailable");
        body.put("message", "Service '" + serviceName + "' is currently unavailable. Please try again later.");
        body.put("timestamp", Instant.now().toString());
        body.put("path", path);

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}

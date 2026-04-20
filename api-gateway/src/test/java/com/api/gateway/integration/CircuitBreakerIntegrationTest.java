package com.api.gateway.integration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * CB tests — dùng @DirtiesContext để reset Spring context (và CB state)
 * sau mỗi test method, đảm bảo isolation tuyệt đối.
 *
 * Testcontainers container KHÔNG restart (vì được khai báo static bean
 * và Testcontainers reuse container khi context restart trong cùng JVM).
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Circuit Breaker Integration Tests")
class CircuitBreakerIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "bXlfc3VwZXJfc2VjcmV0X2tleV9mb3JfcmJhY19nYXRld2F5XzIwMjQ=";

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private String jwt(String username, List<String> roles, List<String> permissions) {
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(secretKey())
                .compact();
    }

    private void drainCbWindow(Runnable request, int times) {
        for (int i = 0; i < times; i++) request.run();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AUTH SERVICE CB
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("auth-service trả 503 liên tục → CB OPEN → fallback 503")
    void authServiceDown_shouldReturnFallback503() {
        wireMock.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse().withStatus(503)));

        String body = "{\"email\":\"u@t.com\",\"password\":\"p\"}";

        drainCbWindow(() ->
                webClient.post().uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .exchange(), 5);

        webClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.error").isEqualTo("Service Unavailable")
                .jsonPath("$.message").value(msg ->
                        Assertions.assertTrue(msg.toString().contains("auth-service")));
    }

    @Test
    @Order(2)
    @DisplayName("auth-service 503 → CB OPEN → fallback body có timestamp, path, status")
    void authServiceDown_fallbackBodyHasRequiredFields() {
        wireMock.stubFor(post(urlEqualTo("/api/auth/register"))
                .willReturn(aResponse().withStatus(503)));

        drainCbWindow(() ->
                webClient.post().uri("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{}")
                        .exchange(), 5);

        webClient.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.path").exists();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RESOURCE SERVICE CB
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("resource-service 503 liên tục → CB OPEN → fallback 503")
    void resourceServiceDown_shouldReturnFallback503() {
        wireMock.stubFor(get(urlMatching("/api/resources/.*"))
                .willReturn(aResponse().withStatus(503)));

        String token = jwt("user@test.com", List.of("ROLE_USER"), List.of("products:READ"));

        drainCbWindow(() ->
                webClient.get().uri("/api/resources/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .exchange(), 4);

        webClient.get().uri("/api/resources/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.error").isEqualTo("Service Unavailable")
                .jsonPath("$.message").value(msg ->
                        Assertions.assertTrue(msg.toString().contains("resource-service")));
    }

    @Test
    @Order(4)
    @DisplayName("CB OPEN → wait → HALF_OPEN → request thành công → CLOSED")
    void circuitBreaker_halfOpenRecovery() throws InterruptedException {
        wireMock.stubFor(get(urlMatching("/api/resources/.*"))
                .willReturn(aResponse().withStatus(503)));

        String token = jwt("user@test.com", List.of("ROLE_USER"), List.of("products:READ"));

        drainCbWindow(() ->
                webClient.get().uri("/api/resources/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .exchange(), 5);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("resourceServiceCB");
        Assertions.assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        wireMock.resetAll();
        wireMock.stubFor(get(urlMatching("/api/resources/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        Thread.sleep(2500); // chờ wait-duration-in-open-state (2s) → HALF_OPEN

        webClient.get().uri("/api/resources/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }
}

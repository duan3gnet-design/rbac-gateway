package com.api.gateway.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Gateway Integration Tests")
class GatewayIntegrationTest extends AbstractIntegrationTest {

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

    private String expiredJwt(String username) {
        return Jwts.builder()
                .subject(username)
                .claim("roles", List.of("ROLE_USER"))
                .claim("permissions", List.of("products:READ"))
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(secretKey())
                .compact();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. PUBLIC ROUTES
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Public routes")
    class PublicRoutes {

        @Test
        @Order(1)
        @DisplayName("POST /api/auth/login → 200")
        void login_publicRoute_shouldForward200() {
            wireMock.stubFor(post(urlEqualTo("/api/auth/login"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"accessToken\":\"eyJ...\",\"refreshToken\":\"eyR...\"}")));

            webClient.post().uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"user@test.com\",\"password\":\"secret\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessToken").exists();
        }

        @Test
        @Order(2)
        @DisplayName("POST /api/auth/register → 201")
        void register_publicRoute_shouldForward201() {
            wireMock.stubFor(post(urlEqualTo("/api/auth/register"))
                    .willReturn(aResponse()
                            .withStatus(201)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"message\":\"User registered successfully\"}")));

            webClient.post().uri("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"new@test.com\",\"password\":\"pass\",\"name\":\"New User\"}")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @Order(3)
        @DisplayName("POST /api/auth/refresh → 200")
        void refresh_publicRoute_shouldForward200() {
            wireMock.stubFor(post(urlEqualTo("/api/auth/refresh"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"accessToken\":\"new.access.token\"}")));

            webClient.post().uri("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"refreshToken\":\"old.refresh.token\"}")
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. PROTECTED ROUTES
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Protected routes — JWT auth")
    class ProtectedRoutes {

        @Test
        @Order(10)
        @DisplayName("GET /api/resources/products không có token → 401")
        void getProducts_noToken_shouldReturn401() {
            webClient.get().uri("/api/resources/products")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @Order(11)
        @DisplayName("GET /api/resources/products với token hết hạn → 401")
        void getProducts_expiredToken_shouldReturn401() {
            webClient.get().uri("/api/resources/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredJwt("user@test.com"))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @Order(12)
        @DisplayName("GET /api/resources/products với token hợp lệ + products:READ → 200")
        void getProducts_validTokenWithPermission_shouldReturn200() {
            wireMock.stubFor(get(urlEqualTo("/api/resources/products"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[{\"id\":1,\"name\":\"Product A\"}]")));

            webClient.get().uri("/api/resources/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                            jwt("user@test.com", List.of("ROLE_USER"), List.of("products:READ")))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].name").isEqualTo("Product A");
        }

        @Test
        @Order(13)
        @DisplayName("GET /api/resources/products thiếu permission → 403")
        void getProducts_validTokenMissingPermission_shouldReturn403() {
            webClient.get().uri("/api/resources/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                            jwt("user@test.com", List.of("ROLE_USER"), List.of("orders:READ")))
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @Order(14)
        @DisplayName("DELETE /api/resources/admin/users/42 với users:DELETE → 204")
        void deleteAdminUser_withPermission_shouldReturn204() {
            wireMock.stubFor(delete(urlEqualTo("/api/resources/admin/users/42"))
                    .willReturn(aResponse().withStatus(204)));

            webClient.delete().uri("/api/resources/admin/users/42")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                            jwt("admin@test.com", List.of("ROLE_ADMIN"),
                                    List.of("users:READ", "users:CREATE", "users:UPDATE", "users:DELETE")))
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        @Order(15)
        @DisplayName("DELETE /api/resources/admin/users/42 không có users:DELETE → 403")
        void deleteAdminUser_withoutPermission_shouldReturn403() {
            webClient.delete().uri("/api/resources/admin/users/42")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                            jwt("user@test.com", List.of("ROLE_USER"), List.of("products:READ")))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. DOWNSTREAM HEADERS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Downstream headers injection")
    class DownstreamHeaders {

        @Test
        @Order(20)
        @DisplayName("Gateway inject X-User-Name header khi forward")
        void gateway_shouldInjectXUserNameHeader() {
            // Stub KHÔNG có withHeader condition — match mọi GET /api/resources/products
            // Header verification thực hiện bằng wireMock.verify() sau khi call thành công
            wireMock.stubFor(get(urlEqualTo("/api/resources/products"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")));

            webClient.get().uri("/api/resources/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                            jwt("phan@test.com", List.of("ROLE_USER"), List.of("products:READ")))
                    .exchange()
                    .expectStatus().isOk();

            // Verify Gateway đã inject đúng header vào request forward đến WireMock
            wireMock.verify(getRequestedFor(urlEqualTo("/api/resources/products"))
                    .withHeader("X-User-Name", equalTo("phan@test.com")));
        }

        @Test
        @Order(21)
        @DisplayName("Gateway inject X-User-Roles header khi forward")
        void gateway_shouldInjectXUserRolesHeader() {
            wireMock.stubFor(get(urlEqualTo("/api/resources/products"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")));

            webClient.get().uri("/api/resources/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                            jwt("user@test.com", List.of("ROLE_USER", "ROLE_VIEWER"), List.of("products:READ")))
                    .exchange()
                    .expectStatus().isOk();

            wireMock.verify(getRequestedFor(urlEqualTo("/api/resources/products"))
                    .withHeader("X-User-Roles", containing("ROLE_USER")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. FALLBACK CONTROLLER
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. FallbackController — direct endpoint calls")
    class FallbackControllerTest {

        @Test
        @Order(40)
        @DisplayName("GET /fallback/auth → 503 với body đúng format")
        void fallbackAuth_shouldReturn503WithBody() {
            webClient.get().uri("/fallback/auth")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(503)
                    .jsonPath("$.error").isEqualTo("Service Unavailable")
                    .jsonPath("$.message").value(msg ->
                            Assertions.assertTrue(msg.toString().contains("auth-service")))
                    .jsonPath("$.timestamp").exists()
                    .jsonPath("$.path").isEqualTo("/fallback/auth");
        }

        @Test
        @Order(41)
        @DisplayName("GET /fallback/resource → 503 với body đúng format")
        void fallbackResource_shouldReturn503WithBody() {
            webClient.get().uri("/fallback/resource")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(503)
                    .jsonPath("$.error").isEqualTo("Service Unavailable")
                    .jsonPath("$.message").value(msg ->
                            Assertions.assertTrue(msg.toString().contains("resource-service")));
        }
    }
}

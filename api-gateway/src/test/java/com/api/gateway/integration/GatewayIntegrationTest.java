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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    // 3. WHOAMI
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Whoami endpoint")
    class WhoamiEndpoint {

        @Test
        @Order(20)
        @DisplayName("GET /api/resources/profile/whoami với profile:READ → 200, body từ downstream")
        void whoami_validTokenWithProfileRead_shouldReturn200() {
            wireMock.stubFor(get(urlEqualTo("/api/resources/profile/whoami"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"username\":\"phan@test.com\",\"roles\":[\"ROLE_USER\"]}")));

            webClient.get().uri("/api/resources/profile/whoami")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                            jwt("phan@test.com", List.of("ROLE_USER"), List.of("profile:READ")))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo("phan@test.com")
                    .jsonPath("$.roles").isArray();
        }

        @Test
        @Order(21)
        @DisplayName("GET /api/resources/profile/whoami không có token → 401")
        void whoami_noToken_shouldReturn401() {
            webClient.get().uri("/api/resources/profile/whoami")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @Order(22)
        @DisplayName("GET /api/resources/profile/whoami thiếu profile:READ → 403")
        void whoami_missingPermission_shouldReturn403() {
            webClient.get().uri("/api/resources/profile/whoami")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                            jwt("user@test.com", List.of("ROLE_USER"), List.of("products:READ")))
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @Order(23)
        @DisplayName("GET /api/resources/profile/whoami với token hết hạn → 401")
        void whoami_expiredToken_shouldReturn401() {
            webClient.get().uri("/api/resources/profile/whoami")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredJwt("user@test.com"))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @Order(24)
        @DisplayName("Gateway inject X-User-Name đúng username vào request whoami downstream")
        void whoami_gatewayInjectsXUserNameHeader() {
            wireMock.stubFor(get(urlEqualTo("/api/resources/profile/whoami"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"username\":\"phan@test.com\"}")));

            webClient.get().uri("/api/resources/profile/whoami")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                            jwt("phan@test.com", List.of("ROLE_USER"), List.of("profile:READ")))
                    .exchange()
                    .expectStatus().isOk();

            wireMock.verify(getRequestedFor(urlEqualTo("/api/resources/profile/whoami"))
                    .withHeader("X-User-Name", equalTo("phan@test.com")));
        }

        @Test
        @Order(25)
        @DisplayName("ROLE_ADMIN cũng có thể gọi whoami (có profile:READ trong permissions)")
        void whoami_adminWithProfileRead_shouldReturn200() {
            wireMock.stubFor(get(urlEqualTo("/api/resources/profile/whoami"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"username\":\"admin@test.com\",\"roles\":[\"ROLE_ADMIN\"]}")));

            webClient.get().uri("/api/resources/profile/whoami")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                            jwt("admin@test.com", List.of("ROLE_ADMIN"),
                                    List.of("profile:READ", "users:READ", "users:DELETE")))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo("admin@test.com");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. DOWNSTREAM HEADERS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Downstream headers injection")
    class DownstreamHeaders {

        @Test
        @Order(30)
        @DisplayName("Gateway inject X-User-Name header khi forward")
        void gateway_shouldInjectXUserNameHeader() {
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

            wireMock.verify(getRequestedFor(urlEqualTo("/api/resources/products"))
                    .withHeader("X-User-Name", equalTo("phan@test.com")));
        }

        @Test
        @Order(31)
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

        @Test
        @Order(32)
        @DisplayName("Gateway inject X-User-Permissions header khi forward whoami")
        void gateway_shouldInjectXUserPermissionsForWhoami() {
            wireMock.stubFor(get(urlEqualTo("/api/resources/profile/whoami"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"username\":\"phan@test.com\"}")));

            webClient.get().uri("/api/resources/profile/whoami")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                            jwt("phan@test.com", List.of("ROLE_USER"),
                                    List.of("profile:READ", "profile:UPDATE")))
                    .exchange()
                    .expectStatus().isOk();

            wireMock.verify(getRequestedFor(urlEqualTo("/api/resources/profile/whoami"))
                    .withHeader("X-User-Permissions", containing("profile:READ")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. FALLBACK CONTROLLER
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. FallbackController — direct endpoint calls")
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
                            assertTrue(msg.toString().contains("auth-service")))
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
                            assertTrue(msg.toString().contains("resource-service")));
        }
    }
}

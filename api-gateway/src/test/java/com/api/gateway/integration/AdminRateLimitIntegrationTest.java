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

/**
 * Integration tests cho Admin Rate Limit Config API.
 *
 * <p>DB state được reset về seed trong {@code AbstractIntegrationTest.baseSetUp()}
 * trước mỗi test — không cần @DirtiesContext.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Admin Rate Limit Config Integration Tests")
class AdminRateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "bXlfc3VwZXJfc2VjcmV0X2tleV9mb3JfcmJhY19nYXRld2F5XzIwMjQ=";

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private String adminJwt() {
        return jwt("admin@test.com", List.of("ROLE_ADMIN"),
                List.of("users:READ", "admin:READ", "admin:CREATE", "admin:UPDATE", "admin:DELETE"));
    }

    private String userJwt() {
        return jwt("user@test.com", List.of("ROLE_USER"), List.of("products:READ"));
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

    // ════════════════════════════════════════════════════════════════════════
    // 1. AUTH GUARD
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Auth guard")
    class AuthGuard {

        @Test @Order(1)
        @DisplayName("GET /api/admin/rate-limits không có token → 401")
        void getAll_noToken_shouldReturn401() {
            webClient.get().uri("/api/admin/rate-limits")
                    .exchange().expectStatus().isUnauthorized();
        }

        @Test @Order(2)
        @DisplayName("GET /api/admin/rate-limits với ROLE_USER → 403")
        void getAll_roleUser_shouldReturn403() {
            webClient.get().uri("/api/admin/rate-limits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userJwt())
                    .exchange().expectStatus().isForbidden();
        }

        @Test @Order(3)
        @DisplayName("POST /api/admin/rate-limits không có token → 401")
        void create_noToken_shouldReturn401() {
            webClient.post().uri("/api/admin/rate-limits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"username\":\"x@t.com\",\"replenishRate\":10,\"burstCapacity\":10}")
                    .exchange().expectStatus().isUnauthorized();
        }

        @Test @Order(4)
        @DisplayName("DELETE /api/admin/rate-limits/999 với ROLE_USER → 403")
        void delete_roleUser_shouldReturn403() {
            webClient.delete().uri("/api/admin/rate-limits/999")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userJwt())
                    .exchange().expectStatus().isForbidden();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. GET QUERIES
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. GET queries")
    class GetQueries {

        @Test @Order(10)
        @DisplayName("GET /api/admin/rate-limits → 200, có ít nhất global default")
        void getAll_shouldReturnListWithDefault() {
            webClient.get().uri("/api/admin/rate-limits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").value(len ->
                            Assertions.assertTrue((Integer) len >= 1));
        }

        @Test @Order(11)
        @DisplayName("GET /api/admin/rate-limits/default → 200, trả về global default")
        void getDefault_shouldReturn200() {
            webClient.get().uri("/api/admin/rate-limits/default")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.replenishRate").isEqualTo(5)
                    .jsonPath("$.burstCapacity").isEqualTo(5)
                    .jsonPath("$.enabled").isEqualTo(true)
                    .jsonPath("$.id").exists();
        }

        @Test @Order(12)
        @DisplayName("GET /api/admin/rate-limits/{id} không tồn tại → 404")
        void getById_notFound_shouldReturn404() {
            webClient.get().uri("/api/admin/rate-limits/99999")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isNotFound();
        }

        @Test @Order(13)
        @DisplayName("GET /api/admin/rate-limits/user/{username} không tồn tại → 404")
        void getByUsername_notFound_shouldReturn404() {
            webClient.get().uri("/api/admin/rate-limits/user/nobody@test.com")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isNotFound();
        }

        @Test @Order(14)
        @DisplayName("GET /api/admin/rate-limits/user/{username} sau khi tạo override → 200")
        void getByUsername_afterCreate_shouldReturn200() {
            webClient.post().uri("/api/admin/rate-limits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"username\":\"lookup@test.com\",\"replenishRate\":20,\"burstCapacity\":30}")
                    .exchange().expectStatus().isEqualTo(HttpStatus.CREATED);

            webClient.get().uri("/api/admin/rate-limits/user/lookup@test.com")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo("lookup@test.com")
                    .jsonPath("$.replenishRate").isEqualTo(20)
                    .jsonPath("$.burstCapacity").isEqualTo(30);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. POST — CREATE
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. POST — tạo per-user override")
    class CreateOverride {

        @Test @Order(20)
        @DisplayName("POST với username mới → 201, trả về config đúng")
        void create_newUser_shouldReturn201() {
            webClient.post().uri("/api/admin/rate-limits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"username":"vip@test.com","replenishRate":50,"burstCapacity":100,
                             "description":"VIP override"}
                            """)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CREATED)
                    .expectBody()
                    .jsonPath("$.id").exists()
                    .jsonPath("$.username").isEqualTo("vip@test.com")
                    .jsonPath("$.replenishRate").isEqualTo(50)
                    .jsonPath("$.burstCapacity").isEqualTo(100)
                    .jsonPath("$.enabled").isEqualTo(true)
                    .jsonPath("$.description").isEqualTo("VIP override")
                    .jsonPath("$.createdAt").exists()
                    .jsonPath("$.updatedAt").exists();
        }

        @Test @Order(21)
        @DisplayName("POST với username đã tồn tại → 409 Conflict")
        void create_duplicateUsername_shouldReturn409() {
            String body = "{\"username\":\"dup@test.com\",\"replenishRate\":10,\"burstCapacity\":20}";

            webClient.post().uri("/api/admin/rate-limits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                    .exchange().expectStatus().isEqualTo(HttpStatus.CREATED);

            webClient.post().uri("/api/admin/rate-limits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                    .exchange().expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test @Order(22)
        @DisplayName("POST với username=null → update global default")
        void create_nullUsername_shouldUpdateGlobalDefault() {
            webClient.post().uri("/api/admin/rate-limits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"replenishRate\":99,\"burstCapacity\":200}")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CREATED)
                    .expectBody()
                    .jsonPath("$.replenishRate").isEqualTo(99)
                    .jsonPath("$.burstCapacity").isEqualTo(200);

            webClient.get().uri("/api/admin/rate-limits/default")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.replenishRate").isEqualTo(99)
                    .jsonPath("$.burstCapacity").isEqualTo(200);
        }

        @Test @Order(23)
        @DisplayName("POST với replenishRate=0 → 400")
        void create_zeroReplenishRate_shouldReturn400() {
            webClient.post().uri("/api/admin/rate-limits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"username\":\"bad@t.com\",\"replenishRate\":0,\"burstCapacity\":10}")
                    .exchange().expectStatus().isBadRequest();
        }

        @Test @Order(24)
        @DisplayName("POST với burstCapacity < replenishRate → 400")
        void create_burstLessThanRate_shouldReturn400() {
            webClient.post().uri("/api/admin/rate-limits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"username\":\"inv@t.com\",\"replenishRate\":20,\"burstCapacity\":5}")
                    .exchange().expectStatus().isBadRequest();
        }

        @Test @Order(25)
        @DisplayName("POST với replenishRate âm → 400")
        void create_negativeReplenishRate_shouldReturn400() {
            webClient.post().uri("/api/admin/rate-limits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"username\":\"neg@t.com\",\"replenishRate\":-5,\"burstCapacity\":10}")
                    .exchange().expectStatus().isBadRequest();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. PUT — UPDATE
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. PUT — update config")
    class UpdateConfig {

        @SuppressWarnings("unchecked")
        private Long createAndGetId(String username) {
            java.util.Map<String, Object> resp = (java.util.Map<String, Object>)
                    webClient.post().uri("/api/admin/rate-limits")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue("{\"username\":\"" + username + "\",\"replenishRate\":10,\"burstCapacity\":20}")
                            .exchange()
                            .expectStatus().isEqualTo(HttpStatus.CREATED)
                            .returnResult(java.util.Map.class)
                            .getResponseBody().blockFirst();
            return ((Number) resp.get("id")).longValue();
        }

        @Test @Order(30)
        @DisplayName("PUT — partial update (chỉ enabled=false)")
        void update_partialEnabled_shouldApplyOnly() {
            Long id = createAndGetId("partial@test.com");

            webClient.put().uri("/api/admin/rate-limits/" + id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"enabled\":false}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.enabled").isEqualTo(false)
                    .jsonPath("$.replenishRate").isEqualTo(10)
                    .jsonPath("$.burstCapacity").isEqualTo(20);
        }

        @Test @Order(31)
        @DisplayName("PUT — full update")
        void update_fullUpdate_shouldApplyAll() {
            Long id = createAndGetId("full@test.com");

            webClient.put().uri("/api/admin/rate-limits/" + id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"replenishRate\":30,\"burstCapacity\":60,\"enabled\":false,\"description\":\"Updated\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.replenishRate").isEqualTo(30)
                    .jsonPath("$.burstCapacity").isEqualTo(60)
                    .jsonPath("$.enabled").isEqualTo(false)
                    .jsonPath("$.description").isEqualTo("Updated");
        }

        @Test @Order(32)
        @DisplayName("PUT id không tồn tại → 404")
        void update_notFound_shouldReturn404() {
            webClient.put().uri("/api/admin/rate-limits/99999")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"replenishRate\":10,\"burstCapacity\":10}")
                    .exchange().expectStatus().isNotFound();
        }

        @Test @Order(33)
        @DisplayName("PUT với burstCapacity < replenishRate → 400")
        void update_invalidRates_shouldReturn400() {
            Long id = createAndGetId("invupd@test.com");

            webClient.put().uri("/api/admin/rate-limits/" + id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"replenishRate\":50,\"burstCapacity\":5}")
                    .exchange().expectStatus().isBadRequest();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. DELETE
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. DELETE")
    class DeleteConfig {

        @SuppressWarnings("unchecked")
        private Long createAndGetId(String username) {
            java.util.Map<String, Object> resp = (java.util.Map<String, Object>)
                    webClient.post().uri("/api/admin/rate-limits")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue("{\"username\":\"" + username + "\",\"replenishRate\":5,\"burstCapacity\":5}")
                            .exchange()
                            .expectStatus().isEqualTo(HttpStatus.CREATED)
                            .returnResult(java.util.Map.class)
                            .getResponseBody().blockFirst();
            return ((Number) resp.get("id")).longValue();
        }

        @Test @Order(40)
        @DisplayName("DELETE per-user override → 204, GET sau đó → 404")
        void delete_perUserOverride_shouldReturn204() {
            Long id = createAndGetId("todelete@test.com");

            webClient.delete().uri("/api/admin/rate-limits/" + id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isNoContent();

            webClient.get().uri("/api/admin/rate-limits/" + id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isNotFound();
        }

        @Test @Order(41)
        @DisplayName("DELETE global default → 400 Bad Request")
        void delete_globalDefault_shouldReturn400() {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> def = (java.util.Map<String, Object>)
                    webClient.get().uri("/api/admin/rate-limits/default")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                            .exchange().expectStatus().isOk()
                            .returnResult(java.util.Map.class)
                            .getResponseBody().blockFirst();

            Long id = ((Number) def.get("id")).longValue();

            webClient.delete().uri("/api/admin/rate-limits/" + id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isBadRequest();
        }

        @Test @Order(42)
        @DisplayName("DELETE id không tồn tại → 404")
        void delete_notFound_shouldReturn404() {
            webClient.delete().uri("/api/admin/rate-limits/99999")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isNotFound();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. RESPONSE STRUCTURE
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("6. Response structure")
    class ResponseStructure {

        @Test @Order(50)
        @DisplayName("GET /default — response có đủ tất cả các field")
        void getDefault_responseShouldHaveAllFields() {
            webClient.get().uri("/api/admin/rate-limits/default")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").exists()
                    .jsonPath("$.replenishRate").exists()
                    .jsonPath("$.burstCapacity").exists()
                    .jsonPath("$.enabled").exists()
                    .jsonPath("$.createdAt").exists()
                    .jsonPath("$.updatedAt").exists();
        }

        @Test @Order(51)
        @DisplayName("POST — createdAt và updatedAt được set tự động")
        void create_timestampsShouldBeSetAutomatically() {
            webClient.post().uri("/api/admin/rate-limits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"username\":\"ts@test.com\",\"replenishRate\":5,\"burstCapacity\":5}")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CREATED)
                    .expectBody()
                    .jsonPath("$.createdAt").exists()
                    .jsonPath("$.updatedAt").exists();
        }
    }
}

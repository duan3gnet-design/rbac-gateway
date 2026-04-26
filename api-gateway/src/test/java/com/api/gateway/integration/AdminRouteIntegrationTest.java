package com.api.gateway.integration;

import com.api.gateway.repository.GatewayRouteR2dbcRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests cho Admin Route Management API.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Admin Route Management Integration Tests")
@Slf4j
class AdminRouteIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private GatewayRouteR2dbcRepository routeRepo;
    private static final String SECRET = "bXlfc3VwZXJfc2VjcmV0X2tleV9mb3JfcmJhY19nYXRld2F5XzIwMjQ=";

    private static final String SAMPLE_ROUTE_BODY = """
            {
              "id": "test-route-new",
              "uri": "http://localhost:9999",
              "predicates": "[{\\"name\\":\\"Path\\",\\"args\\":{\\"pattern\\":\\"/api/test/**\\"}}]",
              "filters": "[]",
              "routeOrder": 99,
              "enabled": true
            }
            """;

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private String adminJwt() {
        return jwt("admin@test.com", List.of("ROLE_ADMIN"), List.of("users:READ"));
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

    /** Helper: tạo route mới qua API, trả về routeId */
    private String createTestRoute(String routeId) {
        webClient.post().uri("/api/admin/routes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "id": "%s",
                          "uri": "http://localhost:9999",
                          "predicates": "[{\\"name\\":\\"Path\\",\\"args\\":{\\"pattern\\":\\"/api/%s/**\\"}}]",
                          "filters": "[]",
                          "routeOrder": 99,
                          "enabled": true
                        }
                        """.formatted(routeId, routeId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody()
                .consumeWith(result -> System.out.println("Response: " + result));
        log.info(routeId);
        await().pollDelay(50, TimeUnit.MILLISECONDS).atMost(Duration.ofMillis(500))
                        .untilAsserted(() -> assertThat(routeRepo.findById(routeId).hasElement().block()).isEqualTo(true));

        return routeId;
    }

    /**
     * Helper: GET permissions, lấy id của phần tử đầu tiên.
     * Dùng cast rõ ràng sang Map<String,Object> để tránh raw-type compile error.
     */
    @SuppressWarnings("unchecked")
    private Long firstPermissionId() {
        Map<String, Object> first = (Map<String, Object>)
                webClient.get().uri("/api/admin/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                        .exchange()
                        .expectStatus().isOk()
                        .returnResult(Map.class)
                        .getResponseBody()
                        .blockFirst();
        return ((Number) first.get("id")).longValue();
    }

    /** Helper: GET permissions list (lấy N phần tử đầu), cast sang List<Map<String,Object>> */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> firstNPermissions(int n) {
        return (List<Map<String, Object>>) (List<?>)
                webClient.get().uri("/api/admin/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                        .exchange()
                        .expectStatus().isOk()
                        .returnResult(Map.class)
                        .getResponseBody()
                        .take(n)
                        .collectList()
                        .block();
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. AUTH GUARD
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Auth guard")
    class AuthGuard {

        @Test @Order(1)
        @DisplayName("GET /api/admin/routes không có token → 401")
        void getRoutes_noToken_shouldReturn401() {
            webClient.get().uri("/api/admin/routes").exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test @Order(2)
        @DisplayName("GET /api/admin/routes với ROLE_USER → 403")
        void getRoutes_roleUser_shouldReturn403() {
            webClient.get().uri("/api/admin/routes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userJwt())
                    .exchange().expectStatus().isForbidden();
        }

        @Test @Order(3)
        @DisplayName("POST /api/admin/routes không có token → 401")
        void createRoute_noToken_shouldReturn401() {
            webClient.post().uri("/api/admin/routes")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(SAMPLE_ROUTE_BODY)
                    .exchange().expectStatus().isUnauthorized();
        }

        @Test @Order(4)
        @DisplayName("DELETE /api/admin/routes/auth-login với ROLE_USER → 403")
        void deleteRoute_roleUser_shouldReturn403() {
            webClient.delete().uri("/api/admin/routes/auth-login")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userJwt())
                    .exchange().expectStatus().isForbidden();
        }

        @Test @Order(5)
        @DisplayName("GET /api/admin/permissions không có token → 401")
        void getPermissions_noToken_shouldReturn401() {
            webClient.get().uri("/api/admin/permissions")
                    .exchange().expectStatus().isUnauthorized();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. GET ALL ROUTES
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. GET all routes")
    class GetAllRoutes {

        @Test @Order(10)
        @DisplayName("GET /api/admin/routes → 200, trả về list chứa seeded routes")
        void getAllRoutes_shouldReturnSeededRoutes() {
            webClient.get().uri("/api/admin/routes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").value(len ->
                            Assertions.assertTrue((Integer) len >= 6));
        }

        @Test @Order(11)
        @DisplayName("GET /api/admin/routes — mỗi route có đủ các field bắt buộc")
        void getAllRoutes_eachRouteShouldHaveRequiredFields() {
            webClient.get().uri("/api/admin/routes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].id").exists()
                    .jsonPath("$[0].uri").exists()
                    .jsonPath("$[0].predicates").exists()
                    .jsonPath("$[0].filters").exists()
                    .jsonPath("$[0].routeOrder").exists()
                    .jsonPath("$[0].enabled").exists()
                    .jsonPath("$[0].permissionIds").exists()
                    .jsonPath("$[0].createdAt").exists()
                    .jsonPath("$[0].updatedAt").exists();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. GET SINGLE ROUTE
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. GET single route")
    class GetSingleRoute {

        @Test @Order(20)
        @DisplayName("GET /api/admin/routes/auth-login → 200 với đúng data")
        void getRoute_existingId_shouldReturn200() {
            webClient.get().uri("/api/admin/routes/auth-login")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo("auth-login")
                    .jsonPath("$.enabled").isEqualTo(true)
                    .jsonPath("$.permissionIds").isArray();
        }

        @Test @Order(21)
        @DisplayName("GET /api/admin/routes/non-existent → 404")
        void getRoute_notFound_shouldReturn404() {
            webClient.get().uri("/api/admin/routes/non-existent-route")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isNotFound();
        }

        @Test @Order(22)
        @DisplayName("GET resource-service route → permissionIds không rỗng (seeded)")
        void getRoute_resourceService_permissionIdsShouldBeSeeded() {
            webClient.get().uri("/api/admin/routes/resource-service")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo("resource-service")
                    .jsonPath("$.permissionIds").isArray()
                    .jsonPath("$.permissionIds.length()").value(len ->
                            Assertions.assertTrue((Integer) len > 0));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. POST — CREATE ROUTE
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. POST — tạo route mới")
    class CreateRoute {

        @Test @Order(30)
        @DisplayName("POST /api/admin/routes với data hợp lệ → 201")
        void createRoute_valid_shouldReturn201() {
            webClient.post().uri("/api/admin/routes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(SAMPLE_ROUTE_BODY)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CREATED)
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.id").isEqualTo("test-route-new")
                    .jsonPath("$.uri").isEqualTo("http://localhost:9999")
                    .jsonPath("$.routeOrder").isEqualTo(99)
                    .jsonPath("$.enabled").isEqualTo(true)
                    .jsonPath("$.permissionIds").isArray()
                    .jsonPath("$.createdAt").exists()
                    .jsonPath("$.updatedAt").exists();
        }

        @Test @Order(31)
        @DisplayName("POST route với id đã tồn tại → 409 Conflict")
        void createRoute_duplicateId_shouldReturn409() {
            webClient.post().uri("/api/admin/routes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"id":"auth-login","uri":"http://localhost:8081",
                             "predicates":"[]","filters":"[]","routeOrder":1,"enabled":true}
                            """)
                    .exchange().expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test @Order(32)
        @DisplayName("POST route mới có thể GET lại ngay sau đó")
        void createRoute_thenGet_shouldReturnSameData() {
            webClient.post().uri("/api/admin/routes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"id":"test-get-after-create","uri":"http://localhost:7777",
                             "predicates":"[]","filters":"[]","routeOrder":50,"enabled":false}
                            """)
                    .exchange().expectStatus().isEqualTo(HttpStatus.CREATED);

            webClient.get().uri("/api/admin/routes/test-get-after-create")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.uri").isEqualTo("http://localhost:7777")
                    .jsonPath("$.routeOrder").isEqualTo(50)
                    .jsonPath("$.enabled").isEqualTo(false);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. PUT — UPDATE ROUTE
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. PUT — cập nhật route")
    class UpdateRoute {

        @Test @Order(40)
        @DisplayName("PUT /api/admin/routes/{id} → 200 với data mới")
        void updateRoute_valid_shouldReturn200() {
            createTestRoute("to-update");

            webClient.put().uri("/api/admin/routes/to-update")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"id":"to-update","uri":"http://updated-host:8080",
                             "predicates":"[]","filters":"[]","routeOrder":10,"enabled":false}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.uri").isEqualTo("http://updated-host:8080")
                    .jsonPath("$.routeOrder").isEqualTo(10)
                    .jsonPath("$.enabled").isEqualTo(false);
        }

        @Test @Order(41)
        @DisplayName("PUT route không tồn tại → 404")
        void updateRoute_notFound_shouldReturn404() {
            webClient.put().uri("/api/admin/routes/does-not-exist")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"id":"does-not-exist","uri":"http://x:1",
                             "predicates":"[]","filters":"[]","routeOrder":1,"enabled":true}
                            """)
                    .exchange().expectStatus().isNotFound();
        }

        @Test @Order(42)
        @DisplayName("PUT — updatedAt thay đổi sau khi update")
        @SuppressWarnings("unchecked")
        void updateRoute_updatedAtShouldChange() {
            createTestRoute("ts-check");

            Map<String, Object> before = (Map<String, Object>)
                    webClient.get().uri("/api/admin/routes/ts-check")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                            .exchange()
                            .expectStatus().isOk()
                            .returnResult(Map.class)
                            .getResponseBody()
                            .blockFirst();

            String updatedAtBefore = (String) before.get("updatedAt");
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}

            webClient.put().uri("/api/admin/routes/ts-check")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"id":"ts-check","uri":"http://new-host:9",
                             "predicates":"[]","filters":"[]","routeOrder":1,"enabled":true}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.updatedAt").value(after ->
                            Assertions.assertNotEquals(updatedAtBefore, after.toString()));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. DELETE ROUTE
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("6. DELETE route")
    class DeleteRoute {

        @Test @Order(50)
        @DisplayName("DELETE route tồn tại → 204 No Content")
        void deleteRoute_existing_shouldReturn204() {
            createTestRoute("to-delete");

            webClient.delete().uri("/api/admin/routes/to-delete")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isNoContent();

            webClient.get().uri("/api/admin/routes/to-delete")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isNotFound();
        }

        @Test @Order(51)
        @DisplayName("DELETE route không tồn tại → 404")
        void deleteRoute_notFound_shouldReturn404() {
            webClient.delete().uri("/api/admin/routes/ghost-route")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isNotFound();
        }

        @Test @Order(52)
        @DisplayName("DELETE route có permissions → cascade xóa route_permissions")
        void deleteRoute_withPermissions_cascadeDeletePermissions() {
            createTestRoute("route-with-perms");

            Long permId = firstPermissionId();

            webClient.put().uri("/api/admin/routes/route-with-perms/permissions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"permissionIds\":[" + permId + "]}")
                    .exchange().expectStatus().isOk();

            webClient.delete().uri("/api/admin/routes/route-with-perms")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isNoContent();

            webClient.get().uri("/api/admin/routes/route-with-perms")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange().expectStatus().isNotFound();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. PATCH TOGGLE
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("7. PATCH toggle enabled")
    class ToggleRoute {

        @Test @Order(60)
        @DisplayName("PATCH toggle enabled=false → route bị disable")
        void toggleRoute_disable_shouldSetEnabledFalse() {
            createTestRoute("toggle-test");

            webClient.patch().uri("/api/admin/routes/toggle-test/toggle")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"enabled\": false}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo("toggle-test")
                    .jsonPath("$.enabled").isEqualTo(false);
        }

        @Test @Order(61)
        @DisplayName("PATCH toggle false rồi true → route được re-enable")
        void toggleRoute_disableThenEnable_shouldReEnable() {
            createTestRoute("toggle-re-enable");

            webClient.patch().uri("/api/admin/routes/toggle-re-enable/toggle")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"enabled\": false}")
                    .exchange().expectStatus().isOk();

            webClient.patch().uri("/api/admin/routes/toggle-re-enable/toggle")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"enabled\": true}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.enabled").isEqualTo(true);
        }

        @Test @Order(62)
        @DisplayName("PATCH toggle route không tồn tại → 404")
        void toggleRoute_notFound_shouldReturn404() {
            webClient.patch().uri("/api/admin/routes/ghost/toggle")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"enabled\": false}")
                    .exchange().expectStatus().isNotFound();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. ROUTE PERMISSIONS
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("8. Route permissions")
    class RoutePermissions {

        @Test @Order(70)
        @DisplayName("GET /routes/{id}/permissions — route mới → list rỗng")
        void getPermissions_newRoute_shouldReturnEmptyList() {
            createTestRoute("perm-empty-route");

            webClient.get().uri("/api/admin/routes/perm-empty-route/permissions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").isEqualTo(0);
        }

        @Test @Order(71)
        @DisplayName("PUT /routes/{id}/permissions → gán permissions thành công")
        void assignPermissions_shouldReturn200WithIds() {
            createTestRoute("perm-assign-route");

            Long permId = firstPermissionId();

            webClient.put().uri("/api/admin/routes/perm-assign-route/permissions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"permissionIds\":[" + permId + "]}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").isEqualTo(1)
                    .jsonPath("$[0]").isEqualTo(permId.intValue());
        }

        @Test @Order(72)
        @DisplayName("PUT permissions → replace strategy: gán mới thay thế cũ hoàn toàn")
        void assignPermissions_replaceStrategy_shouldReplaceOldPermissions() {
            createTestRoute("perm-replace-route");

            List<Map<String, Object>> perms = firstNPermissions(2);
            Assertions.assertTrue(perms.size() >= 2, "Test cần ít nhất 2 permissions");

            Long permId1 = ((Number) perms.get(0).get("id")).longValue();
            Long permId2 = ((Number) perms.get(1).get("id")).longValue();

            webClient.put().uri("/api/admin/routes/perm-replace-route/permissions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"permissionIds\":[" + permId1 + "]}")
                    .exchange().expectStatus().isOk();

            webClient.put().uri("/api/admin/routes/perm-replace-route/permissions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"permissionIds\":[" + permId2 + "]}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(1)
                    .jsonPath("$[0]").isEqualTo(permId2.intValue());
        }

        @Test @Order(73)
        @DisplayName("PUT permissions với list rỗng → xóa tất cả permissions của route")
        void assignPermissions_emptyList_shouldClearAll() {
            createTestRoute("perm-clear-route");

            Long permId = firstPermissionId();

            webClient.put().uri("/api/admin/routes/perm-clear-route/permissions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"permissionIds\":[" + permId + "]}")
                    .exchange().expectStatus().isOk();

            webClient.put().uri("/api/admin/routes/perm-clear-route/permissions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"permissionIds\":[]}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.length()").isEqualTo(0);
        }

        @Test @Order(74)
        @DisplayName("PUT permissions route không tồn tại → 404")
        void assignPermissions_routeNotFound_shouldReturn404() {
            webClient.put().uri("/api/admin/routes/ghost-perm-route/permissions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"permissionIds\":[]}")
                    .exchange().expectStatus().isNotFound();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. GET ALL PERMISSIONS
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("9. GET all permissions")
    class GetAllPermissions {

        @Test @Order(80)
        @DisplayName("GET /api/admin/permissions → 200, trả về list permissions từ DB")
        void getAllPermissions_shouldReturnSeededPermissions() {
            webClient.get().uri("/api/admin/permissions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").value(len ->
                            Assertions.assertTrue((Integer) len > 0));
        }

        @Test @Order(81)
        @DisplayName("GET /api/admin/permissions — mỗi permission có đủ field")
        void getAllPermissions_eachShouldHaveRequiredFields() {
            webClient.get().uri("/api/admin/permissions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].id").exists()
                    .jsonPath("$[0].role").exists()
                    .jsonPath("$[0].resource").exists()
                    .jsonPath("$[0].action").exists()
                    .jsonPath("$[0].code").exists();
        }

        @Test @Order(82)
        @DisplayName("GET /api/admin/permissions — code = 'resource:action'")
        void getAllPermissions_codeShouldBeResourceColonAction() {
            webClient.get().uri("/api/admin/permissions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].code").value(code ->
                            Assertions.assertTrue(code.toString().contains(":")));
        }

        @Test @Order(83)
        @DisplayName("GET /api/admin/permissions — có cả ROLE_ADMIN và ROLE_USER permissions")
        @SuppressWarnings("unchecked")
        void getAllPermissions_shouldContainBothRoles() {
            List<Map<String, Object>> perms = (List<Map<String, Object>>) (List<?>)
                    webClient.get().uri("/api/admin/permissions")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt())
                            .exchange()
                            .expectStatus().isOk()
                            .returnResult(Map.class)
                            .getResponseBody()
                            .collectList()
                            .block();

            Assertions.assertTrue(perms.stream().anyMatch(p -> "ROLE_ADMIN".equals(p.get("role"))));
            Assertions.assertTrue(perms.stream().anyMatch(p -> "ROLE_USER".equals(p.get("role"))));
        }

        @Test @Order(84)
        @DisplayName("GET /api/admin/permissions với ROLE_USER → 403")
        void getAllPermissions_roleUser_shouldReturn403() {
            webClient.get().uri("/api/admin/permissions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userJwt())
                    .exchange().expectStatus().isForbidden();
        }
    }
}

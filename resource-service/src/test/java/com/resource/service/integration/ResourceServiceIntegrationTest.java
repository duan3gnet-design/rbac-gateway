package com.resource.service.integration;

import com.resource.service.client.AuthServiceClient;
import com.resource.service.dto.UserSummary;
import com.resource.service.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = "/sql/reset.sql", executionPhase = BEFORE_TEST_METHOD)
@DisplayName("ResourceService Integration Tests")
class ResourceServiceIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthServiceClient authServiceClient;

    // ── Stub data dùng lại nhiều chỗ ──────────────────────────

    private static final UserSummary ALICE = new UserSummary(
            1L, "alice", "Alice Nguyen", null, Set.of("ROLE_USER"));

    private static final UserSummary ADMIN_USER = new UserSummary(
            2L, "adminUser", "Admin User", null, Set.of("ROLE_ADMIN"));

    // =========================================================
    // Unauthenticated
    // =========================================================

    @Nested
    @DisplayName("Unauthenticated requests")
    class Unauthenticated {

        @Test
        @DisplayName("GET /products không có header → 401/403")
        void products_noAuth() throws Exception {
            mockMvc.perform(get("/api/resources/products"))
                    .andExpect(status().is(anyOf(is(401), is(403))));
        }

        @Test
        @DisplayName("GET /admin/users không có header → 401/403")
        void adminUsers_noAuth() throws Exception {
            mockMvc.perform(get("/api/resources/admin/users"))
                    .andExpect(status().is(anyOf(is(401), is(403))));
        }

        @Test
        @DisplayName("POST /orders không có header → 401/403")
        void orders_noAuth() throws Exception {
            mockMvc.perform(post("/api/resources/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"items": [{"productId": 1, "quantity": 1}]}
                                    """))
                    .andExpect(status().is(anyOf(is(401), is(403))));
        }

        @Test
        @DisplayName("GET /orders không có header → 401/403")
        void getOrders_noAuth() throws Exception {
            mockMvc.perform(get("/api/resources/orders"))
                    .andExpect(status().is(anyOf(is(401), is(403))));
        }
    }

    // =========================================================
    // Products
    // =========================================================

    @Nested
    @DisplayName("GET /api/resources/products")
    class GetProducts {

        @Test
        @DisplayName("200 + trả về 3 sản phẩm đã seed — ROLE_USER")
        void getAll_asUser() throws Exception {
            mockMvc.perform(get("/api/resources/products")
                            .header("X-User-Name",  "alice")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].id",    is(1)))
                    .andExpect(jsonPath("$[0].name",  is("Product A")))
                    .andExpect(jsonPath("$[0].price", is(100.00)))
                    .andExpect(jsonPath("$[0].stock", is(50)))
                    .andExpect(jsonPath("$[1].name",  is("Product B")))
                    .andExpect(jsonPath("$[2].name",  is("Product C")));
        }

        @Test
        @DisplayName("200 + trả về 3 sản phẩm — ROLE_ADMIN")
        void getAll_asAdmin() throws Exception {
            mockMvc.perform(get("/api/resources/products")
                            .header("X-User-Name",  "adminUser")
                            .header("X-User-Roles", "ROLE_ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)));
        }
    }

    // =========================================================
    // Product by ID
    // =========================================================

    @Nested
    @DisplayName("GET /api/resources/products/{id}")
    class GetProductById {

        @Test
        @DisplayName("200 + đúng product khi id tồn tại")
        void getById_found() throws Exception {
            mockMvc.perform(get("/api/resources/products/1")
                            .header("X-User-Name",  "alice")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id",          is(1)))
                    .andExpect(jsonPath("$.name",        is("Product A")))
                    .andExpect(jsonPath("$.price",       is(100.00)))
                    .andExpect(jsonPath("$.description", is("Description for Product A")));
        }

        @Test
        @DisplayName("404 khi id không tồn tại")
        void getById_notFound() throws Exception {
            mockMvc.perform(get("/api/resources/products/999")
                            .header("X-User-Name",  "alice")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================
    // Orders — ROLE_USER full flow
    // =========================================================

    @Nested
    @DisplayName("ROLE_USER — order flow")
    class UserOrderFlow {

        @Test
        @DisplayName("POST /orders — đặt hàng thành công, total tính đúng")
        void placeOrder_success() throws Exception {
            mockMvc.perform(post("/api/resources/orders")
                            .header("X-User-Name",  "alice")
                            .header("X-User-Roles", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "items": [
                                            {"productId": 1, "quantity": 2},
                                            {"productId": 2, "quantity": 1}
                                        ]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username",             is("alice")))
                    .andExpect(jsonPath("$.status",               is("CREATED")))
                    .andExpect(jsonPath("$.total",                is(400.00)))
                    .andExpect(jsonPath("$.items",                hasSize(2)))
                    .andExpect(jsonPath("$.items[0].productId",   is(1)))
                    .andExpect(jsonPath("$.items[0].productName", is("Product A")))
                    .andExpect(jsonPath("$.items[0].quantity",    is(2)))
                    .andExpect(jsonPath("$.items[0].unitPrice",   is(100.00)))
                    .andExpect(jsonPath("$.id",                   notNullValue()))
                    .andExpect(jsonPath("$.createdAt",            notNullValue()));
        }

        @Test
        @DisplayName("POST /orders — productId không tồn tại → 404")
        void placeOrder_productNotFound() throws Exception {
            mockMvc.perform(post("/api/resources/orders")
                            .header("X-User-Name",  "alice")
                            .header("X-User-Roles", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"items": [{"productId": 999, "quantity": 1}]}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /orders — lấy đúng orders của chính mình")
        void getMyOrders_success() throws Exception {
            mockMvc.perform(post("/api/resources/orders")
                            .header("X-User-Name",  "alice")
                            .header("X-User-Roles", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"items": [{"productId": 1, "quantity": 1}]}
                                    """))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/resources/orders")
                            .header("X-User-Name",  "alice")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].username", is("alice")))
                    .andExpect(jsonPath("$[0].status",   is("CREATED")));
        }

        @Test
        @DisplayName("GET /orders — alice không thấy order của bob")
        void getMyOrders_isolation() throws Exception {
            mockMvc.perform(post("/api/resources/orders")
                            .header("X-User-Name",  "bob")
                            .header("X-User-Roles", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"items": [{"productId": 2, "quantity": 1}]}
                                    """))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/resources/orders")
                            .header("X-User-Name",  "alice")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("POST /orders — 401/403 khi không xác thực")
        void placeOrder_noAuth() throws Exception {
            mockMvc.perform(post("/api/resources/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"items": [{"productId": 1, "quantity": 1}]}
                                    """))
                    .andExpect(status().is(anyOf(is(401), is(403))));
        }
    }

    // =========================================================
    // Admin endpoints — AuthServiceClient được mock
    // =========================================================

    @Nested
    @DisplayName("ROLE_ADMIN — admin flow")
    class AdminFlow {

        @Test
        @DisplayName("GET /admin/users — 200 + danh sách từ auth-service")
        void getUsers_success() throws Exception {
            when(authServiceClient.getAllUsers())
                    .thenReturn(List.of(ALICE, ADMIN_USER));

            mockMvc.perform(get("/api/resources/admin/users")
                            .header("X-User-Name",  "adminUser")
                            .header("X-User-Roles", "ROLE_ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].username",
                            containsInAnyOrder("alice", "adminUser")));
        }

        @Test
        @DisplayName("GET /admin/users — 403 khi ROLE_USER")
        void getUsers_forbidden_forUser() throws Exception {
            mockMvc.perform(get("/api/resources/admin/users")
                            .header("X-User-Name",  "alice")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(authServiceClient);
        }

        @Test
        @DisplayName("DELETE /admin/users/{id} — 204 khi ADMIN")
        void deleteUser_success() throws Exception {
            doNothing().when(authServiceClient).deleteUser(1L);

            mockMvc.perform(delete("/api/resources/admin/users/1")
                            .header("X-User-Name",  "adminUser")
                            .header("X-User-Roles", "ROLE_ADMIN"))
                    .andExpect(status().isNoContent());

            verify(authServiceClient).deleteUser(1L);
        }

        @Test
        @DisplayName("DELETE /admin/users/{id} — 404 khi user không tồn tại ở auth-service")
        void deleteUser_notFound() throws Exception {
            doThrow(new ResourceNotFoundException("User not found: 999"))
                    .when(authServiceClient).deleteUser(999L);

            mockMvc.perform(delete("/api/resources/admin/users/999")
                            .header("X-User-Name",  "adminUser")
                            .header("X-User-Roles", "ROLE_ADMIN"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("DELETE /admin/users/{id} — 403 khi ROLE_USER")
        void deleteUser_forbidden_forUser() throws Exception {
            mockMvc.perform(delete("/api/resources/admin/users/1")
                            .header("X-User-Name",  "alice")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(authServiceClient);
        }
    }

    // =========================================================
    // Profile — AuthServiceClient được mock
    // =========================================================

    @Nested
    @DisplayName("GET /api/resources/profile/{username}")
    class ProfileFlow {

        @Test
        @DisplayName("200 khi USER xem profile của chính mình")
        void viewOwnProfile() throws Exception {
            when(authServiceClient.getUserByUsername("alice"))
                    .thenReturn(Optional.of(ALICE));

            mockMvc.perform(get("/api/resources/profile/alice")
                            .header("X-User-Name",  "alice")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username",  is("alice")))
                    .andExpect(jsonPath("$.fullName",  is("Alice Nguyen")))
                    .andExpect(jsonPath("$.fetchedAt", notNullValue()));
        }

        @Test
        @DisplayName("200 khi ADMIN xem profile người khác")
        void viewOtherProfile_asAdmin() throws Exception {
            when(authServiceClient.getUserByUsername("alice"))
                    .thenReturn(Optional.of(ALICE));

            mockMvc.perform(get("/api/resources/profile/alice")
                            .header("X-User-Name",  "adminUser")
                            .header("X-User-Roles", "ROLE_ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username", is("alice")));
        }

        @Test
        @DisplayName("404 khi user không tồn tại ở auth-service")
        void viewProfile_userNotFound() throws Exception {
            when(authServiceClient.getUserByUsername("ghost"))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/api/resources/profile/ghost")
                            .header("X-User-Name",  "ghost")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("403 khi USER xem profile người khác")
        void viewOtherProfile_asUser_forbidden() throws Exception {
            mockMvc.perform(get("/api/resources/profile/alice")
                            .header("X-User-Name",  "bob")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(authServiceClient);
        }

        @Test
        @DisplayName("401/403 khi không xác thực")
        void viewProfile_noAuth() throws Exception {
            mockMvc.perform(get("/api/resources/profile/alice"))
                    .andExpect(status().is(anyOf(is(401), is(403))));

            verifyNoInteractions(authServiceClient);
        }
    }

    // =========================================================
    // Multi-role
    // =========================================================

    @Nested
    @DisplayName("Multi-role trong cùng một header")
    class MultiRole {

        @Test
        @DisplayName("ROLE_USER,ROLE_ADMIN truy cập được /admin/users")
        void multiRole_adminEndpoint() throws Exception {
            when(authServiceClient.getAllUsers()).thenReturn(List.of());

            mockMvc.perform(get("/api/resources/admin/users")
                            .header("X-User-Name",  "superUser")
                            .header("X-User-Roles", "ROLE_USER,ROLE_ADMIN"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ROLE_USER,ROLE_ADMIN xem profile người khác được (nhờ ADMIN)")
        void multiRole_viewOtherProfile() throws Exception {
            when(authServiceClient.getUserByUsername("alice"))
                    .thenReturn(Optional.of(ALICE));

            mockMvc.perform(get("/api/resources/profile/alice")
                            .header("X-User-Name",  "superUser")
                            .header("X-User-Roles", "ROLE_USER,ROLE_ADMIN"))
                    .andExpect(status().isOk());
        }
    }
}

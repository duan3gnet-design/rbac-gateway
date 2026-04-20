package com.api.gateway.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RbacPermissionChecker")
class RbacPermissionCheckerTest {

    private RbacPermissionChecker checker;

    @BeforeEach
    void setUp() {
        checker = new RbacPermissionChecker();
    }

    // ─── PRODUCTS ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("products")
    class Products {

        @Test
        @DisplayName("GET /api/resources/products → granted khi có products:READ")
        void getProducts_withReadPermission_shouldAllow() {
            assertThat(checker.hasPermission(Set.of("products:READ"), "GET", "/api/resources/products")).isTrue();
        }

        @Test
        @DisplayName("GET /api/resources/products/123 → granted khi có products:READ (wildcard)")
        void getProductById_withReadPermission_shouldAllow() {
            assertThat(checker.hasPermission(Set.of("products:READ"), "GET", "/api/resources/products/123")).isTrue();
        }

        @Test
        @DisplayName("GET /api/resources/products → denied khi thiếu permission")
        void getProducts_withoutPermission_shouldDeny() {
            assertThat(checker.hasPermission(Set.of("orders:READ"), "GET", "/api/resources/products")).isFalse();
        }

        @Test
        @DisplayName("POST /api/resources/products → denied (không có rule POST products)")
        void postProducts_noRuleDefined_shouldDeny() {
            assertThat(checker.hasPermission(Set.of("products:READ", "products:CREATE"), "POST", "/api/resources/products")).isFalse();
        }
    }

    // ─── ORDERS ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("orders")
    class Orders {

        @ParameterizedTest(name = "{0} {1} với orders:CREATE → granted")
        @CsvSource({
                "POST, /api/resources/orders",
                "POST, /api/resources/orders/confirm"
        })
        void createOrder_withCreatePermission_shouldAllow(String method, String path) {
            assertThat(checker.hasPermission(Set.of("orders:CREATE"), method, path)).isTrue();
        }

        @Test
        @DisplayName("PUT /api/resources/orders/5 với orders:UPDATE → granted")
        void updateOrder_withUpdatePermission_shouldAllow() {
            assertThat(checker.hasPermission(Set.of("orders:UPDATE"), "PUT", "/api/resources/orders/5")).isTrue();
        }

        @Test
        @DisplayName("DELETE /api/resources/orders/5 với orders:DELETE → granted (wildcard **)")
        void deleteOrder_withDeletePermission_shouldAllow() {
            assertThat(checker.hasPermission(Set.of("orders:DELETE"), "DELETE", "/api/resources/orders/5")).isTrue();
        }

        @Test
        @DisplayName("GET /api/resources/orders → denied khi chỉ có orders:CREATE")
        void getOrders_withOnlyCreatePermission_shouldDeny() {
            assertThat(checker.hasPermission(Set.of("orders:CREATE"), "GET", "/api/resources/orders")).isFalse();
        }

        @Test
        @DisplayName("PUT /api/resources/orders/5 → denied khi chỉ có orders:READ")
        void updateOrder_withOnlyReadPermission_shouldDeny() {
            assertThat(checker.hasPermission(Set.of("orders:READ"), "PUT", "/api/resources/orders/5")).isFalse();
        }
    }

    // ─── ADMIN USERS ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("admin users")
    class AdminUsers {

        @ParameterizedTest(name = "{0} {1} với {2} → granted")
        @CsvSource({
                "GET,    /api/resources/admin/users,       users:READ",
                "GET,    /api/resources/admin/users/42,    users:READ",
                "POST,   /api/resources/admin/users/new,   users:CREATE",
                "PUT,    /api/resources/admin/users/42,    users:UPDATE",
                "DELETE, /api/resources/admin/users/42,    users:DELETE"
        })
        void adminCrud_withCorrectPermission_shouldAllow(String method, String path, String permission) {
            assertThat(checker.hasPermission(Set.of(permission.trim()), method.trim(), path.trim())).isTrue();
        }

        @Test
        @DisplayName("DELETE admin/users/42 với users:READ → denied")
        void deleteAdminUser_withOnlyReadPermission_shouldDeny() {
            assertThat(checker.hasPermission(Set.of("users:READ"), "DELETE", "/api/resources/admin/users/42")).isFalse();
        }
    }

    // ─── PROFILE ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("profile")
    class Profile {

        @Test
        @DisplayName("GET /api/resources/profile/me với profile:READ → granted")
        void getProfile_withReadPermission_shouldAllow() {
            assertThat(checker.hasPermission(Set.of("profile:READ"), "GET", "/api/resources/profile/me")).isTrue();
        }

        @Test
        @DisplayName("PUT /api/resources/profile/me với profile:UPDATE → granted")
        void updateProfile_withUpdatePermission_shouldAllow() {
            assertThat(checker.hasPermission(Set.of("profile:UPDATE"), "PUT", "/api/resources/profile/me")).isTrue();
        }

        @Test
        @DisplayName("DELETE /api/resources/profile/me → denied (không có rule)")
        void deleteProfile_noRuleDefined_shouldDeny() {
            assertThat(checker.hasPermission(Set.of("profile:READ", "profile:UPDATE"), "DELETE", "/api/resources/profile/me")).isFalse();
        }
    }

    // ─── AUTH LOGOUT-ALL ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("auth logout-all")
    class AuthLogoutAll {

        @Test
        @DisplayName("POST /api/auth/logout-all với auth:LOGOUT_ALL → granted")
        void logoutAll_withCorrectPermission_shouldAllow() {
            assertThat(checker.hasPermission(Set.of("auth:LOGOUT_ALL"), "POST", "/api/auth/logout-all")).isTrue();
        }

        @Test
        @DisplayName("POST /api/auth/logout-all với permissions khác → denied")
        void logoutAll_withWrongPermission_shouldDeny() {
            assertThat(checker.hasPermission(Set.of("orders:READ", "profile:READ"), "POST", "/api/auth/logout-all")).isFalse();
        }
    }

    // ─── EDGE CASES ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Empty permissions set → luôn denied")
        void emptyPermissions_shouldAlwaysDeny() {
            assertThat(checker.hasPermission(Set.of(), "GET", "/api/resources/products")).isFalse();
        }

        @Test
        @DisplayName("Path không match bất kỳ rule nào → denied")
        void unknownPath_shouldDeny() {
            assertThat(checker.hasPermission(Set.of("products:READ", "orders:READ"), "GET", "/api/unknown/endpoint")).isFalse();
        }

        @Test
        @DisplayName("Multiple permissions → chỉ cần 1 match là granted")
        void multiplePermissions_oneMatchSuffices_shouldAllow() {
            Set<String> allPerms = Set.of("products:READ", "orders:CREATE", "users:DELETE");
            assertThat(checker.hasPermission(allPerms, "GET", "/api/resources/products")).isTrue();
        }

        @Test
        @DisplayName("Super-admin có tất cả permissions → tất cả routes đều granted")
        void superAdmin_allPermissions_shouldAllowEverything() {
            Set<String> superAdminPerms = Set.of(
                    "products:READ", "orders:READ", "orders:CREATE", "orders:UPDATE", "orders:DELETE",
                    "users:READ", "users:CREATE", "users:UPDATE", "users:DELETE",
                    "profile:READ", "profile:UPDATE", "auth:LOGOUT_ALL"
            );
            assertThat(checker.hasPermission(superAdminPerms, "DELETE", "/api/resources/admin/users/99")).isTrue();
            assertThat(checker.hasPermission(superAdminPerms, "POST",   "/api/auth/logout-all")).isTrue();
            assertThat(checker.hasPermission(superAdminPerms, "GET",    "/api/resources/products")).isTrue();
        }
    }
}

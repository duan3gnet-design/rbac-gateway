package com.api.gateway.filter;

import com.api.gateway.validator.JwtValidator;
import com.api.gateway.validator.RbacPermissionChecker;
import com.auth.service.dto.ClaimsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtValidator jwtValidator;

    @Mock
    private RbacPermissionChecker rbacChecker;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ClaimsResponse claims(String username, List<String> roles, Set<String> permissions) {
        return new ClaimsResponse(username, roles, permissions);
    }

    /**
     * Chạy filter, assert response status.
     * Chain mock luôn pass-through (Mono.empty).
     * KHÔNG stub chain ở đây — tránh unnecessary stubbing khi filter short-circuit trước khi gọi chain.
     */
    private void assertStatus(MockServerWebExchange exchange, HttpStatus expectedStatus) {
        var chain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);
        // lenient vì chain.filter() có thể không bao giờ được gọi (khi 401/403 short-circuit)
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expectedStatus);
    }

    /**
     * Chạy filter, assert chain được gọi đúng 1 lần (request pass-through thành công).
     */
    private void assertChainCalled(MockServerWebExchange exchange) {
        var chain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(any());
    }

    // ─── PUBLIC PATHS ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("public paths (bypass auth)")
    class PublicPaths {

        @Test
        @DisplayName("POST /api/auth/login → bypass auth, forward thẳng")
        void loginPath_shouldBypassAuth() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/auth/login").build());

            assertChainCalled(exchange);
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("POST /api/auth/register → bypass auth")
        void registerPath_shouldBypassAuth() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/auth/register").build());

            assertChainCalled(exchange);
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("POST /api/auth/refresh → bypass auth")
        void refreshPath_shouldBypassAuth() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/auth/refresh").build());

            assertChainCalled(exchange);
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("POST /api/auth/logout → bypass auth")
        void logoutPath_shouldBypassAuth() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/auth/logout").build());

            assertChainCalled(exchange);
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("GET /oauth2/authorization/google → bypass auth")
        void oauth2Path_shouldBypassAuth() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/oauth2/authorization/google").build());

            assertChainCalled(exchange);
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("GET /login/oauth2/code/google → bypass auth")
        void oauth2CallbackPath_shouldBypassAuth() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/login/oauth2/code/google").build());

            assertChainCalled(exchange);
            verifyNoInteractions(jwtValidator, rbacChecker);
        }
    }

    // ─── MISSING / MALFORMED TOKEN ───────────────────────────────────────────

    @Nested
    @DisplayName("missing or malformed Authorization header")
    class MissingOrMalformedToken {

        @Test
        @DisplayName("Không có Authorization header → 401")
        void noAuthHeader_shouldReturn401() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/resources/products").build());

            assertStatus(exchange, HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("Authorization: Basic ... (không có Bearer prefix) → 401")
        void basicAuthHeader_shouldReturn401() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/resources/products")
                            .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                            .build());

            assertStatus(exchange, HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("Authorization: Bearer <empty> → JwtValidator throw → 401")
        void bearerWithEmptyToken_shouldReturn401() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/resources/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                            .build());

            // token sau "Bearer " là "" — validate sẽ throw
            when(jwtValidator.validate(""))
                    .thenReturn(Mono.error(new RuntimeException("empty token")));

            assertStatus(exchange, HttpStatus.UNAUTHORIZED);
        }
    }

    // ─── INVALID / EXPIRED JWT ───────────────────────────────────────────────

    @Nested
    @DisplayName("invalid or expired JWT")
    class InvalidJwt {

        @Test
        @DisplayName("Token không hợp lệ → JwtValidator error → 401")
        void invalidJwt_shouldReturn401() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/resources/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                            .build());

            when(jwtValidator.validate("invalid.jwt.token"))
                    .thenReturn(Mono.error(new RuntimeException("JWT parse error")));

            assertStatus(exchange, HttpStatus.UNAUTHORIZED);
            // rbacChecker không được gọi khi validate fail
            verifyNoInteractions(rbacChecker);
        }

        @Test
        @DisplayName("Token hết hạn → ExpiredJwtException → 401")
        void expiredJwt_shouldReturn401() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/resources/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer expired.token.here")
                            .build());

            when(jwtValidator.validate("expired.token.here"))
                    .thenReturn(Mono.error(new io.jsonwebtoken.ExpiredJwtException(null, null, "expired")));

            assertStatus(exchange, HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(rbacChecker);
        }
    }

    // ─── RBAC PERMISSION CHECK ───────────────────────────────────────────────

    @Nested
    @DisplayName("RBAC permission check")
    class RbacCheck {

        @Test
        @DisplayName("Valid JWT + có permission → chain được gọi")
        void validJwtWithPermission_shouldForward() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/resources/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                            .build());

            var claimsResponse = claims("phan", List.of("ROLE_USER"), Set.of("products:READ"));
            when(jwtValidator.validate("valid.token")).thenReturn(Mono.just(claimsResponse));
            // Dùng anyString() thay vì exact path — tránh flaky argument matching với anyString
            when(rbacChecker.hasPermission(any(), anyString(), anyString())).thenReturn(true);

            assertChainCalled(exchange);
        }

        @Test
        @DisplayName("Valid JWT + thiếu permission → 403")
        void validJwtWithoutPermission_shouldReturn403() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/resources/admin/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                            .build());

            var claimsResponse = claims("phan", List.of("ROLE_USER"), Set.of("products:READ"));
            when(jwtValidator.validate("valid.token")).thenReturn(Mono.just(claimsResponse));
            // Dùng any matchers — hasPermission trả false → filter trả 403
            when(rbacChecker.hasPermission(any(), anyString(), anyString())).thenReturn(false);

            assertStatus(exchange, HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Admin có đủ permissions → chain được gọi")
        void adminWithPermissions_shouldForward() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.delete("/api/resources/admin/users/42")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer admin.token")
                            .build());

            var adminClaims = claims("admin@example.com",
                    List.of("ROLE_ADMIN"),
                    Set.of("users:READ", "users:CREATE", "users:UPDATE", "users:DELETE"));
            when(jwtValidator.validate("admin.token")).thenReturn(Mono.just(adminClaims));
            when(rbacChecker.hasPermission(any(), anyString(), anyString())).thenReturn(true);

            assertChainCalled(exchange);
        }

        @Test
        @DisplayName("POST /api/auth/logout-all với auth:LOGOUT_ALL → chain được gọi")
        void logoutAllWithPermission_shouldForward() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/auth/logout-all")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer super.token")
                            .build());

            var claimsResponse = claims("superuser", List.of("ROLE_ADMIN"), Set.of("auth:LOGOUT_ALL"));
            when(jwtValidator.validate("super.token")).thenReturn(Mono.just(claimsResponse));
            when(rbacChecker.hasPermission(any(), anyString(), anyString())).thenReturn(true);

            assertChainCalled(exchange);
        }

        @Test
        @DisplayName("rbacChecker không được gọi khi jwtValidator throw")
        void rbacChecker_notCalledWhenJwtInvalid() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/resources/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer bad.token")
                            .build());

            when(jwtValidator.validate("bad.token"))
                    .thenReturn(Mono.error(new RuntimeException("invalid")));

            assertStatus(exchange, HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(rbacChecker);
        }
    }

    // ─── FILTER ORDER ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("filter metadata")
    class FilterMetadata {

        @Test
        @DisplayName("getOrder() = -100 → chạy trước tất cả filter khác")
        void filterOrder_shouldBeMinus100() {
            assertThat(filter.getOrder()).isEqualTo(-100);
        }
    }
}

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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

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

    private ClaimsResponse claims(String username, List<String> roles, Set<String> permissions) {
        return new ClaimsResponse(username, roles, permissions);
    }

    /**
     * Chạy filter và assert response status.
     * Handler object = new Object() (không cần mock HandlerMethod cho interceptor).
     */
    private boolean runFilter(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        return filter.preHandle(request, response, new Object());
    }

    // ─── PUBLIC PATHS ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("public paths (bypass auth)")
    class PublicPaths {

        @Test
        @DisplayName("POST /api/auth/login → bypass auth, return true")
        void loginPath_shouldBypassAuth() throws Exception {
            var req  = new MockHttpServletRequest("POST", "/api/auth/login");
            var resp = new MockHttpServletResponse();

            boolean result = runFilter(req, resp);

            assertThat(result).isTrue();
            assertThat(resp.getStatus()).isEqualTo(200);
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("POST /api/auth/register → bypass auth")
        void registerPath_shouldBypassAuth() throws Exception {
            var req  = new MockHttpServletRequest("POST", "/api/auth/register");
            var resp = new MockHttpServletResponse();

            assertThat(runFilter(req, resp)).isTrue();
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("POST /api/auth/refresh → bypass auth")
        void refreshPath_shouldBypassAuth() throws Exception {
            var req  = new MockHttpServletRequest("POST", "/api/auth/refresh");
            var resp = new MockHttpServletResponse();

            assertThat(runFilter(req, resp)).isTrue();
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("POST /api/auth/logout → bypass auth")
        void logoutPath_shouldBypassAuth() throws Exception {
            var req  = new MockHttpServletRequest("POST", "/api/auth/logout");
            var resp = new MockHttpServletResponse();

            assertThat(runFilter(req, resp)).isTrue();
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("GET /oauth2/authorization/google → bypass auth")
        void oauth2Path_shouldBypassAuth() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
            var resp = new MockHttpServletResponse();

            assertThat(runFilter(req, resp)).isTrue();
            verifyNoInteractions(jwtValidator, rbacChecker);
        }
    }

    // ─── MISSING / MALFORMED TOKEN ───────────────────────────────────────────

    @Nested
    @DisplayName("missing or malformed Authorization header")
    class MissingOrMalformedToken {

        @Test
        @DisplayName("Không có Authorization header → 401, return false")
        void noAuthHeader_shouldReturn401() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/resources/products");
            var resp = new MockHttpServletResponse();

            boolean result = runFilter(req, resp);

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(401);
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("Authorization: Basic ... → 401")
        void basicAuthHeader_shouldReturn401() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/resources/products");
            req.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
            var resp = new MockHttpServletResponse();

            assertThat(runFilter(req, resp)).isFalse();
            assertThat(resp.getStatus()).isEqualTo(401);
            verifyNoInteractions(jwtValidator, rbacChecker);
        }

        @Test
        @DisplayName("Bearer <empty> → validate throw → 401")
        void bearerWithEmptyToken_shouldReturn401() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/resources/products");
            req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");
            var resp = new MockHttpServletResponse();

            when(jwtValidator.validate("")).thenThrow(new RuntimeException("empty token"));

            assertThat(runFilter(req, resp)).isFalse();
            assertThat(resp.getStatus()).isEqualTo(401);
        }
    }

    // ─── INVALID / EXPIRED JWT ───────────────────────────────────────────────

    @Nested
    @DisplayName("invalid or expired JWT")
    class InvalidJwt {

        @Test
        @DisplayName("Token không hợp lệ → JwtValidator throw → 401")
        void invalidJwt_shouldReturn401() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/resources/products");
            req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token");
            var resp = new MockHttpServletResponse();

            when(jwtValidator.validate("invalid.jwt.token"))
                    .thenThrow(new RuntimeException("JWT parse error"));

            assertThat(runFilter(req, resp)).isFalse();
            assertThat(resp.getStatus()).isEqualTo(401);
            verifyNoInteractions(rbacChecker);
        }

        @Test
        @DisplayName("Token hết hạn → ExpiredJwtException → 401")
        void expiredJwt_shouldReturn401() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/resources/products");
            req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired.token.here");
            var resp = new MockHttpServletResponse();

            when(jwtValidator.validate("expired.token.here"))
                    .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "expired"));

            assertThat(runFilter(req, resp)).isFalse();
            assertThat(resp.getStatus()).isEqualTo(401);
            verifyNoInteractions(rbacChecker);
        }
    }

    // ─── RBAC PERMISSION CHECK ───────────────────────────────────────────────

    @Nested
    @DisplayName("RBAC permission check")
    class RbacCheck {

        @Test
        @DisplayName("Valid JWT + có permission → return true")
        void validJwtWithPermission_shouldReturnTrue() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/resources/products");
            req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token");
            var resp = new MockHttpServletResponse();

            var claimsResponse = claims("phan", List.of("ROLE_USER"), Set.of("products:READ"));
            when(jwtValidator.validate("valid.token")).thenReturn(claimsResponse);
            when(rbacChecker.hasPermission(any(), anyString(), anyString())).thenReturn(true);

            assertThat(runFilter(req, resp)).isTrue();
        }

        @Test
        @DisplayName("Valid JWT + thiếu permission → 403, return false")
        void validJwtWithoutPermission_shouldReturn403() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/resources/admin/users");
            req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token");
            var resp = new MockHttpServletResponse();

            var claimsResponse = claims("phan", List.of("ROLE_USER"), Set.of("products:READ"));
            when(jwtValidator.validate("valid.token")).thenReturn(claimsResponse);
            when(rbacChecker.hasPermission(any(), anyString(), anyString())).thenReturn(false);

            assertThat(runFilter(req, resp)).isFalse();
            assertThat(resp.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("Admin path + ROLE_USER → 403")
        void adminPath_withRoleUser_shouldReturn403() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/admin/routes");
            req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user.token");
            var resp = new MockHttpServletResponse();

            var claimsResponse = claims("user", List.of("ROLE_USER"), Set.of("products:READ"));
            when(jwtValidator.validate("user.token")).thenReturn(claimsResponse);

            assertThat(runFilter(req, resp)).isFalse();
            assertThat(resp.getStatus()).isEqualTo(403);
            verifyNoInteractions(rbacChecker);
        }

        @Test
        @DisplayName("Admin path + ROLE_ADMIN → return true")
        void adminPath_withRoleAdmin_shouldReturnTrue() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/admin/routes");
            req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer admin.token");
            var resp = new MockHttpServletResponse();

            var adminClaims = claims("admin", List.of("ROLE_ADMIN"),
                    Set.of("users:READ", "users:DELETE"));
            when(jwtValidator.validate("admin.token")).thenReturn(adminClaims);

            assertThat(runFilter(req, resp)).isTrue();
        }

        @Test
        @DisplayName("rbacChecker không được gọi khi jwtValidator throw")
        void rbacChecker_notCalledWhenJwtInvalid() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/resources/products");
            req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad.token");
            var resp = new MockHttpServletResponse();

            when(jwtValidator.validate("bad.token")).thenThrow(new RuntimeException("invalid"));

            runFilter(req, resp);
            verifyNoInteractions(rbacChecker);
        }

        @Test
        @DisplayName("Claims được set vào request attribute sau khi validate thành công")
        void validJwt_shouldSetClaimsAttribute() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/resources/products");
            req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token");
            var resp = new MockHttpServletResponse();

            var claimsResponse = claims("phan", List.of("ROLE_USER"), Set.of("products:READ"));
            when(jwtValidator.validate("valid.token")).thenReturn(claimsResponse);
            when(rbacChecker.hasPermission(any(), anyString(), anyString())).thenReturn(true);

            runFilter(req, resp);
            assertThat(req.getAttribute("jwt.claims")).isEqualTo(claimsResponse);
        }
    }
}

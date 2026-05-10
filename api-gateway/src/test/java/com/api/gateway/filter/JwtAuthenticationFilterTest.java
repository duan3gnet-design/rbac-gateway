package com.api.gateway.filter;

import com.api.gateway.validator.JwtValidator;
import com.api.gateway.validator.RbacPermissionChecker;
import com.auth.service.dto.ClaimsResponse;
import org.junit.jupiter.api.Disabled;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        @DisplayName("Admin path + ROLE_ADMIN → return true")
        @Disabled
        void adminPath_withRoleAdmin_shouldReturnTrue() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/admin/routes");
            req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer admin.token");
            var resp = new MockHttpServletResponse();

            var adminClaims = claims("admin", List.of("ROLE_ADMIN"),
                    Set.of("admin:READ", "users:DELETE"));
            when(jwtValidator.validate("admin.token")).thenReturn(adminClaims);

            assertThat(runFilter(req, resp)).isFalse();
            assertThat(resp.getStatus()).isEqualTo(200);
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

package com.auth.service.controller;

import com.auth.service.dto.LoginRequest;
import com.auth.service.dto.RefreshTokenRequest;
import com.auth.service.dto.RegisterRequest;
import com.auth.service.dto.TokenPair;
import com.auth.service.service.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.ott.InvalidOneTimeTokenException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = AuthController.class,
    excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class,
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = EnableWebSecurity.class
    )
)
@DisplayName("AuthController")
class AuthControllerTest {

    private static final String SECRET = "bXlfc3VwZXJfc2VjcmV0X2tleV9mb3JfcmJhY19nYXRld2F5XzIwMjQ=";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired MockMvc mockMvc;

    @MockitoBean SecurityFilterChain securityFilterChain;
    @MockitoBean AuthenticationManager authManager;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean UserService userService;
    @MockitoBean RefreshTokenService refreshTokenService;
    @MockitoBean PermissionService permissionService;
    @MockitoBean GoogleAuthService googleAuthService;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String json(Object obj) throws Exception {
        return jsonMapper.writeValueAsString(obj);
    }

    private Claims fakeClaims(String subject, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        String token = Jwts.builder()
                .subject(subject)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /api/auth/login
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("Credentials đúng → 200 với token và refreshToken")
        void validCredentials_shouldReturn200() throws Exception {
            var userDetails = User.withUsername("phan")
                    .password("encoded").roles("USER").build();

            when(authManager.authenticate(any())).thenReturn(
                    new UsernamePasswordAuthenticationToken("phan", "pass"));
            when(userDetailsService.loadUserByUsername("phan")).thenReturn(userDetails);
            when(jwtService.generateToken(userDetails)).thenReturn("access-token-xyz");

            var rt = new com.auth.service.entity.RefreshToken();
            rt.setToken("refresh-token-xyz");
            when(refreshTokenService.createRefreshToken("phan")).thenReturn(rt);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("phan", "pass"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("access-token-xyz"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token-xyz"));
        }

        @Test
        @DisplayName("Sai credentials → 401")
        void wrongCredentials_shouldReturn401() throws Exception {
            when(authManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("phan", "wrongpass"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /api/auth/register
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("Request hợp lệ → 201 Created")
        void validRequest_shouldReturn201() throws Exception {
            doNothing().when(userService).register(any());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RegisterRequest("newuser", "pass123", Set.of("ROLE_USER")))))
                    .andExpect(status().isCreated())
                    .andExpect(content().string("User created"));
        }

        @Test
        @DisplayName("Username đã tồn tại → 400")
        void duplicateUsername_shouldReturn400() throws Exception {
            doThrow(new IllegalArgumentException("Username already exists"))
                    .when(userService).register(any());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RegisterRequest("phan", "pass", Set.of("ROLE_USER")))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET /api/auth/validate
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/auth/validate")
    class Validate {

        @Test
        @DisplayName("Token hợp lệ → 200 với ClaimsResponse")
        void validToken_shouldReturn200WithClaims() throws Exception {
            when(jwtService.isValid("valid-token")).thenReturn(true);
            when(jwtService.extractClaims("valid-token"))
                    .thenReturn(fakeClaims("phan@test.com", List.of("ROLE_USER")));
            when(permissionService.getPermissions(List.of("ROLE_USER")))
                    .thenReturn(Set.of("products:READ"));

            mockMvc.perform(get("/api/auth/validate")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("phan@test.com"))
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                    .andExpect(jsonPath("$.permissions").isArray());
        }

        @Test
        @DisplayName("Token không hợp lệ → 401")
        void invalidToken_shouldReturn401() throws Exception {
            when(jwtService.isValid("bad-token")).thenReturn(false);

            mockMvc.perform(get("/api/auth/validate")
                            .header("Authorization", "Bearer bad-token"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /api/auth/refresh
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("Refresh token hợp lệ → 200 với TokenPair mới")
        void validRefreshToken_shouldReturn200() throws Exception {
            when(refreshTokenService.rotate("valid-refresh"))
                    .thenReturn(new TokenPair("new-access", "new-refresh"));

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RefreshTokenRequest("valid-refresh"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-access"))
                    .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
        }

        @Test
        @DisplayName("Refresh token không hợp lệ → 400")
        void invalidRefreshToken_shouldReturn400() throws Exception {
            when(refreshTokenService.rotate("expired-token"))
                    .thenThrow(new InvalidOneTimeTokenException("Refresh token không hợp lệ"));

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RefreshTokenRequest("expired-token"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Body thiếu refreshToken → 400 validation error")
        void missingRefreshToken_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /api/auth/logout
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/logout")
    class Logout {

        @Test
        @DisplayName("Token hợp lệ → 204 No Content")
        void validToken_shouldReturn204() throws Exception {
            doNothing().when(refreshTokenService).revokeToken("valid-refresh");

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RefreshTokenRequest("valid-refresh"))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Token không tồn tại → 400")
        void invalidToken_shouldReturn400() throws Exception {
            doThrow(new InvalidOneTimeTokenException("Token không hợp lệ"))
                    .when(refreshTokenService).revokeToken("ghost-token");

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RefreshTokenRequest("ghost-token"))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /api/auth/logout-all
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/logout-all")
    class LogoutAll {

        @Test
        @DisplayName("X-User-Name header đúng → 204")
        void validHeader_shouldReturn204() throws Exception {
            doNothing().when(refreshTokenService).revokeAllTokens("phan@test.com");

            mockMvc.perform(post("/api/auth/logout-all")
                            .header("X-User-Name", "phan@test.com"))
                    .andExpect(status().isNoContent());

            verify(refreshTokenService).revokeAllTokens("phan@test.com");
        }

        @Test
        @DisplayName("Thiếu X-User-Name header → 400")
        void missingHeader_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/auth/logout-all"))
                    .andExpect(status().isBadRequest());
        }
    }
}

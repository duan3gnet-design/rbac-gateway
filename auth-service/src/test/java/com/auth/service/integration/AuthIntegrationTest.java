package com.auth.service.integration;

import com.auth.service.AbstractIntegrationTest;
import com.auth.service.dto.LoginRequest;
import com.auth.service.dto.RefreshTokenRequest;
import com.auth.service.dto.RegisterRequest;
import com.auth.service.entity.RefreshToken;
import com.auth.service.entity.User;
import com.auth.service.repository.RefreshTokenRepository;
import com.auth.service.repository.RoleRepository;
import com.auth.service.repository.UserRepository;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("Auth Service Integration Tests")
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    // ─── Helper ──────────────────────────────────────────────────────────────

    private String json(Object obj) throws Exception {
        return jsonMapper.writeValueAsString(obj);
    }

    private void createUser(String username, String rawPassword, String roleName) {
        var role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRoles(Set.of(role));
        userRepository.save(user);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REGISTER
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Register flow")
    class RegisterFlow {

        @Test
        @DisplayName("Register thành công → user được lưu vào DB với password hashed")
        void register_shouldPersistUserWithHashedPassword() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RegisterRequest("newuser@test.com", "secret123", Set.of("ROLE_USER")))))
                    .andExpect(status().isCreated());

            User saved = userRepository.findByUsername("newuser@test.com").orElseThrow();
            assertThat(saved.getPassword()).isNotEqualTo("secret123");
            assertThat(passwordEncoder.matches("secret123", saved.getPassword())).isTrue();
            assertThat(saved.getRoles()).extracting("name").containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("Register duplicate username → 400")
        void registerDuplicate_shouldReturn400() throws Exception {
            createUser("duplicate@test.com", "pass", "ROLE_USER");

            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RegisterRequest("duplicate@test.com", "pass2", Set.of("ROLE_USER")))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOGIN
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Login flow")
    class LoginFlow {

        @Test
        @DisplayName("Login đúng credentials → 200 với JWT và refreshToken")
        void login_validCredentials_shouldReturnTokens() throws Exception {
            createUser("login@test.com", "mypassword", "ROLE_USER");

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("login@test.com", "mypassword"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andReturn();

            String refreshToken = jsonMapper
                    .readTree(result.getResponse().getContentAsString())
                    .get("refreshToken").asText();
            assertThat(refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)).isPresent();
        }

        @Test
        @DisplayName("Login sai password → 401")
        void login_wrongPassword_shouldReturn401() throws Exception {
            createUser("user2@test.com", "correctpass", "ROLE_USER");

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("user2@test.com", "wrongpass"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Login user không tồn tại → 401")
        void login_unknownUser_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("ghost@test.com", "pass"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("JWT chứa đúng roles và permissions")
        void login_jwtShouldContainRolesAndPermissions() throws Exception {
            createUser("rolecheck@test.com", "pass", "ROLE_USER");

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("rolecheck@test.com", "pass"))))
                    .andExpect(status().isOk())
                    .andReturn();

            String token = jsonMapper
                    .readTree(result.getResponse().getContentAsString())
                    .get("token").asText();

            mockMvc.perform(get("/api/auth/validate")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("rolecheck@test.com"))
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                    .andExpect(jsonPath("$.permissions").isArray());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REFRESH TOKEN
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Refresh token flow")
    class RefreshFlow {

        @Test
        @DisplayName("Refresh hợp lệ → token cũ bị revoke, token mới được tạo")
        void refresh_validToken_shouldRotate() throws Exception {
            createUser("refresh@test.com", "pass", "ROLE_USER");

            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("refresh@test.com", "pass"))))
                    .andExpect(status().isOk())
                    .andReturn();

            String oldToken = jsonMapper
                    .readTree(loginResult.getResponse().getContentAsString())
                    .get("refreshToken").asText();

            MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RefreshTokenRequest(oldToken))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andReturn();

            assertThat(refreshTokenRepository.findByTokenAndRevokedFalse(oldToken)).isEmpty();

            String newToken = jsonMapper
                    .readTree(refreshResult.getResponse().getContentAsString())
                    .get("refreshToken").asText();
            assertThat(refreshTokenRepository.findByTokenAndRevokedFalse(newToken)).isPresent();
        }

        @Test
        @DisplayName("Dùng lại refresh token đã revoked → 400")
        void refresh_revokedToken_shouldReturn400() throws Exception {
            createUser("reuse@test.com", "pass", "ROLE_USER");

            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("reuse@test.com", "pass"))))
                    .andExpect(status().isOk())
                    .andReturn();

            String refreshToken = jsonMapper
                    .readTree(loginResult.getResponse().getContentAsString())
                    .get("refreshToken").asText();

            // Lần 1 — thành công
            mockMvc.perform(post("/api/auth/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RefreshTokenRequest(refreshToken))))
                    .andExpect(status().isOk());

            // Lần 2 — đã revoked → 400
            mockMvc.perform(post("/api/auth/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RefreshTokenRequest(refreshToken))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Refresh token hết hạn → 400")
        void refresh_expiredToken_shouldReturn400() throws Exception {
            createUser("expired@test.com", "pass", "ROLE_USER");

            RefreshToken expiredRt = new RefreshToken();
            expiredRt.setToken("already-expired-token");
            expiredRt.setUsername("expired@test.com");
            expiredRt.setExpiresAt(Instant.now().minusSeconds(3600));
            expiredRt.setRevoked(false);
            refreshTokenRepository.save(expiredRt);

            mockMvc.perform(post("/api/auth/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RefreshTokenRequest("already-expired-token"))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOGOUT
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Logout flow")
    class LogoutFlow {

        @Test
        @DisplayName("Logout → refreshToken bị revoke, không thể dùng lại")
        void logout_shouldRevokeToken() throws Exception {
            createUser("logout@test.com", "pass", "ROLE_USER");

            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("logout@test.com", "pass"))))
                    .andExpect(status().isOk())
                    .andReturn();

            String refreshToken = jsonMapper
                    .readTree(loginResult.getResponse().getContentAsString())
                    .get("refreshToken").asText();

            mockMvc.perform(post("/api/auth/logout")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RefreshTokenRequest(refreshToken))))
                    .andExpect(status().isNoContent());

            assertThat(refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)).isEmpty();

            mockMvc.perform(post("/api/auth/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RefreshTokenRequest(refreshToken))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Logout-all → tất cả refreshTokens của user bị revoke")
        void logoutAll_shouldRevokeAllTokens() throws Exception {
            createUser("logoutall@test.com", "pass", "ROLE_USER");

            for (int i = 0; i < 2; i++) {
                mockMvc.perform(post("/api/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(new LoginRequest("logoutall@test.com", "pass"))))
                        .andExpect(status().isOk());
            }

            assertThat(refreshTokenRepository.findAll().stream()
                    .filter(t -> t.getUsername().equals("logoutall@test.com") && !t.isRevoked())
                    .count()).isEqualTo(2);

            mockMvc.perform(post("/api/auth/logout-all")
                            .with(csrf())
                            .header("X-User-Name", "logoutall@test.com"))
                    .andExpect(status().isNoContent());

            assertThat(refreshTokenRepository.findAll().stream()
                    .filter(t -> t.getUsername().equals("logoutall@test.com") && !t.isRevoked())
                    .count()).isZero();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VALIDATE
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validate flow")
    class ValidateFlow {

        @Test
        @DisplayName("Token hợp lệ → 200 với đúng username, roles, permissions")
        void validate_validToken_shouldReturnFullClaims() throws Exception {
            createUser("validate@test.com", "pass", "ROLE_USER");

            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("validate@test.com", "pass"))))
                    .andExpect(status().isOk())
                    .andReturn();

            String token = jsonMapper
                    .readTree(loginResult.getResponse().getContentAsString())
                    .get("token").asText();

            mockMvc.perform(get("/api/auth/validate")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("validate@test.com"))
                    .andExpect(jsonPath("$.roles").isArray())
                    .andExpect(jsonPath("$.permissions").isArray());
        }

        @Test
        @DisplayName("Token rác → 401")
        void validate_invalidToken_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/auth/validate")
                            .header("Authorization", "Bearer garbage.token.here"))
                    .andExpect(status().isUnauthorized());
        }
    }
}

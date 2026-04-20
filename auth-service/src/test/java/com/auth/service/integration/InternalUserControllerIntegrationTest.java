package com.auth.service.integration;

import com.auth.service.AbstractIntegrationTest;
import com.auth.service.entity.Role;
import com.auth.service.entity.User;
import com.auth.service.repository.RoleRepository;
import com.auth.service.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("InternalUserController Integration Tests")
class InternalUserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Value("${app.internal.secret}")
    private String internalSecret;

    // ── Helper ────────────────────────────────────────────────

    private User createUser(String username, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Full Name of " + username);
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }

    // =========================================================
    // GET /internal/users
    // =========================================================

    @Nested
    @DisplayName("GET /internal/users")
    class GetAllUsers {

        @Test
        @DisplayName("200 + danh sách users khi secret đúng")
        void getAll_withValidSecret_returns200() throws Exception {
            createUser("alice@test.com", "ROLE_USER");
            createUser("bob@test.com", "ROLE_ADMIN");

            mockMvc.perform(get("/internal/users")
                            .header("X-Internal-Secret", internalSecret))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].username",
                            containsInAnyOrder("alice@test.com", "bob@test.com")))
                    .andExpect(jsonPath("$[0].id",       notNullValue()))
                    .andExpect(jsonPath("$[0].username", notNullValue()))
                    .andExpect(jsonPath("$[0].roles",    notNullValue()));
        }

        @Test
        @DisplayName("200 + empty list khi không có user nào")
        void getAll_noUsers_returnsEmptyList() throws Exception {
            mockMvc.perform(get("/internal/users")
                            .header("X-Internal-Secret", internalSecret))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Response chứa đúng roles của từng user")
        void getAll_rolesAreCorrect() throws Exception {
            createUser("admin@test.com", "ROLE_ADMIN");

            mockMvc.perform(get("/internal/users")
                            .header("X-Internal-Secret", internalSecret))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].roles", hasItem("ROLE_ADMIN")));
        }

        @Test
        @DisplayName("401 khi không có header X-Internal-Secret")
        void getAll_missingSecret_returns401() throws Exception {
            mockMvc.perform(get("/internal/users"))
                    .andExpect(status().is(anyOf(is(401), is(403))));
        }

        @Test
        @DisplayName("401/403 khi secret sai")
        void getAll_wrongSecret_returns401() throws Exception {
            mockMvc.perform(get("/internal/users")
                            .header("X-Internal-Secret", "wrong-secret"))
                    .andExpect(status().is(anyOf(is(401), is(403))));
        }

        @Test
        @DisplayName("403 khi dùng user header thay vì internal secret")
        void getAll_userHeaderInsteadOfSecret_returns403() throws Exception {
            // ROLE_ADMIN user cũng không được phép — endpoint chỉ cho ROLE_INTERNAL
            mockMvc.perform(get("/internal/users")
                            .header("X-User-Name", "someAdmin")
                            .header("X-User-Roles", "ROLE_ADMIN"))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================
    // GET /internal/users/{username}
    // =========================================================

    @Nested
    @DisplayName("GET /internal/users/{username}")
    class GetByUsername {

        @Test
        @DisplayName("200 + đúng user khi username tồn tại")
        void getByUsername_found_returns200() throws Exception {
            createUser("charlie@test.com", "ROLE_USER");

            mockMvc.perform(get("/internal/users/charlie@test.com")
                            .header("X-Internal-Secret", internalSecret))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username", is("charlie@test.com")))
                    .andExpect(jsonPath("$.fullName", is("Full Name of charlie@test.com")))
                    .andExpect(jsonPath("$.roles",    hasItem("ROLE_USER")));
        }

        @Test
        @DisplayName("404 khi username không tồn tại")
        void getByUsername_notFound_returns404() throws Exception {
            mockMvc.perform(get("/internal/users/ghost@test.com")
                            .header("X-Internal-Secret", internalSecret))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("401/403 khi không có header X-Internal-Secret")
        void getByUsername_missingSecret_returns401() throws Exception {
            mockMvc.perform(get("/internal/users/charlie@test.com"))
                    .andExpect(status().is(anyOf(is(401), is(403))));
        }

        @Test
        @DisplayName("401/403 khi secret sai")
        void getByUsername_wrongSecret_returns401() throws Exception {
            mockMvc.perform(get("/internal/users/charlie@test.com")
                            .header("X-Internal-Secret", "wrong-secret"))
                    .andExpect(status().is(anyOf(is(401), is(403))));
        }
    }

    // =========================================================
    // DELETE /internal/users/{id}
    // =========================================================

    @Nested
    @DisplayName("DELETE /internal/users/{id}")
    class DeleteUser {

        @Test
        @DisplayName("204 khi xóa user tồn tại")
        void delete_existingUser_returns204() throws Exception {
            User user = createUser("delete-me@test.com", "ROLE_USER");

            mockMvc.perform(delete("/internal/users/" + user.getId())
                            .header("X-Internal-Secret", internalSecret))
                    .andExpect(status().isNoContent());

            // Verify user đã bị xóa khỏi DB
            mockMvc.perform(get("/internal/users/delete-me@test.com")
                            .header("X-Internal-Secret", internalSecret))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("404 khi xóa user không tồn tại")
        void delete_nonExistentUser_returns404() throws Exception {
            mockMvc.perform(delete("/internal/users/99999")
                            .header("X-Internal-Secret", internalSecret))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("401/403 khi không có header X-Internal-Secret")
        void delete_missingSecret_returns401() throws Exception {
            User user = createUser("protected@test.com", "ROLE_USER");

            mockMvc.perform(delete("/internal/users/" + user.getId()))
                    .andExpect(status().is(anyOf(is(401), is(403))));

            // Đảm bảo user không bị xóa
            mockMvc.perform(get("/internal/users/protected@test.com")
                            .header("X-Internal-Secret", internalSecret))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("401/403 khi secret sai — user không bị xóa")
        void delete_wrongSecret_returns401_andUserNotDeleted() throws Exception {
            User user = createUser("safe@test.com", "ROLE_USER");

            mockMvc.perform(delete("/internal/users/" + user.getId())
                            .header("X-Internal-Secret", "wrong-secret"))
                    .andExpect(status().is(anyOf(is(401), is(403))));

            mockMvc.perform(get("/internal/users/safe@test.com")
                            .header("X-Internal-Secret", internalSecret))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("403 khi dùng ROLE_ADMIN header thay vì internal secret")
        void delete_adminUserHeader_returns403() throws Exception {
            User user = createUser("another@test.com", "ROLE_USER");

            mockMvc.perform(delete("/internal/users/" + user.getId())
                            .header("X-User-Name",  "adminUser")
                            .header("X-User-Roles", "ROLE_ADMIN"))
                    .andExpect(status().isForbidden());
        }
    }
}

package com.auth.service.service;

import com.auth.service.dto.RegisterRequest;
import com.auth.service.entity.Role;
import com.auth.service.entity.User;
import com.auth.service.repository.RoleRepository;
import com.auth.service.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // ─── Helper ──────────────────────────────────────────────────────────────

    private Role role(String name) {
        Role r = new Role();
        r.setName(name);
        return r;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // register
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("Username chưa tồn tại → save user với password encoded và đúng roles")
        void newUser_shouldSaveWithEncodedPasswordAndRoles() {
            when(userRepository.existsByUsername("phan")).thenReturn(false);
            when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role("ROLE_USER")));
            when(passwordEncoder.encode("secret")).thenReturn("$hashed$");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.register(new RegisterRequest("phan", "secret", Set.of("ROLE_USER")));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User saved = captor.getValue();
            assertThat(saved.getUsername()).isEqualTo("phan");
            assertThat(saved.getPassword()).isEqualTo("$hashed$");
            assertThat(saved.getRoles()).extracting("name").containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("Username đã tồn tại → throw IllegalArgumentException")
        void duplicateUsername_shouldThrow() {
            when(userRepository.existsByUsername("phan")).thenReturn(true);

            assertThatThrownBy(() ->
                    userService.register(new RegisterRequest("phan", "pass", Set.of("ROLE_USER"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Role không tồn tại trong DB → throw IllegalArgumentException")
        void unknownRole_shouldThrow() {
            when(userRepository.existsByUsername("phan")).thenReturn(false);
            when(roleRepository.findByName("ROLE_UNKNOWN")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    userService.register(new RegisterRequest("phan", "pass", Set.of("ROLE_UNKNOWN"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Role not found");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Multiple roles → tất cả được assign")
        void multipleRoles_shouldAssignAll() {
            when(userRepository.existsByUsername("admin")).thenReturn(false);
            when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(role("ROLE_ADMIN")));
            when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role("ROLE_USER")));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.register(new RegisterRequest("admin", "pass", Set.of("ROLE_ADMIN", "ROLE_USER")));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRoles()).hasSize(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // findOrCreateOAuth2User
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findOrCreateOAuth2User")
    class FindOrCreateOAuth2User {

        @Test
        @DisplayName("User đã tồn tại → trả UserDetails mà không tạo mới")
        void existingUser_shouldReturnWithoutCreating() {
            User existing = new User();
            existing.setUsername("phan@gmail.com");
            existing.setPassword("");
            existing.setRoles(Set.of(role("ROLE_USER")));

            when(userRepository.findByUsername("phan@gmail.com")).thenReturn(Optional.of(existing));

            var result = userService.findOrCreateOAuth2User("phan@gmail.com", "Phan");

            assertThat(result.getUsername()).isEqualTo("phan@gmail.com");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("User chưa tồn tại → tạo mới với ROLE_USER và provider=GOOGLE")
        void newOAuth2User_shouldCreateWithGoogleProvider() {
            when(userRepository.findByUsername("new@gmail.com")).thenReturn(Optional.empty());
            when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role("ROLE_USER")));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = userService.findOrCreateOAuth2User("new@gmail.com", "New User");

            assertThat(result.getUsername()).isEqualTo("new@gmail.com");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getProvider()).isEqualTo("GOOGLE");
            assertThat(captor.getValue().getFullName()).isEqualTo("New User");
        }

        @Test
        @DisplayName("ROLE_USER không tồn tại trong DB → throw RuntimeException")
        void missingRoleUser_shouldThrow() {
            when(userRepository.findByUsername("x@gmail.com")).thenReturn(Optional.empty());
            when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    userService.findOrCreateOAuth2User("x@gmail.com", "X"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ROLE_USER");
        }
    }
}

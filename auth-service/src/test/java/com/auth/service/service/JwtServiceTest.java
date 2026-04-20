package com.auth.service.service;

import com.auth.service.entity.Action;
import com.auth.service.entity.Permission;
import com.auth.service.entity.Resource;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SECRET = "bXlfc3VwZXJfc2VjcmV0X2tleV9mb3JfcmJhY19nYXRld2F5XzIwMjQ=";

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", 86400000L);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UserDetails user(String username, String... roles) {
        return User.withUsername(username)
                .password("encoded-password")
                .authorities(Arrays.stream(roles)
                        .map(SimpleGrantedAuthority::new)
                        .toList())
                .build();
    }

    private Permission permission(String resource, String action) {
        Permission p = new Permission();
        Resource r = new Resource(); r.setName(resource);
        Action a = new Action(); a.setName(action);
        p.setResource(r);
        p.setAction(a);
        return p;
    }

    @SuppressWarnings("unchecked")
    private List<String> claimAsList(Claims claims, String key) {
        return (List<String>) claims.get(key, List.class);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // generateToken
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateToken")
    class GenerateToken {

        @Test
        @DisplayName("Token chứa đúng subject (username)")
        void shouldEmbedSubject() {
            when(permissionService.getPermissions(anyList())).thenReturn(Set.of());

            String token = jwtService.generateToken(user("phan@test.com", "ROLE_USER"));
            Claims claims = jwtService.extractClaims(token);

            assertThat(claims.getSubject()).isEqualTo("phan@test.com");
        }

        @Test
        @DisplayName("Token chứa đúng roles claim")
        void shouldEmbedRoles() {
            when(permissionService.getPermissions(anyList())).thenReturn(Set.of());

            String token = jwtService.generateToken(user("user", "ROLE_USER", "ROLE_ADMIN"));
            Claims claims = jwtService.extractClaims(token);
            List<String> roles = claimAsList(claims, "roles");

            assertThat(roles).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        }

        @Test
        @DisplayName("Token chứa permissions từ PermissionService")
        void shouldEmbedPermissions() {
            when(permissionService.getPermissions(anyList()))
                    .thenReturn(Set.of("products:READ", "orders:CREATE"));

            String token = jwtService.generateToken(user("user", "ROLE_USER"));
            Claims claims = jwtService.extractClaims(token);

            List<String> permissions = claimAsList(claims, "permissions");
            assertThat(permissions).containsExactlyInAnyOrder("products:READ", "orders:CREATE");
        }

        @Test
        @DisplayName("Token có expiration trong tương lai")
        void shouldHaveFutureExpiration() {
            when(permissionService.getPermissions(anyList())).thenReturn(Set.of());

            String token = jwtService.generateToken(user("user", "ROLE_USER"));
            Claims claims = jwtService.extractClaims(token);

            assertThat(claims.getExpiration()).isAfter(new Date());
        }

        @Test
        @DisplayName("User không có role → permissions rỗng")
        void noRoles_shouldHaveEmptyPermissions() {
            when(permissionService.getPermissions(List.of())).thenReturn(Set.of());

            UserDetails noRoleUser = User.withUsername("guest")
                    .password("pass")
                    .authorities(List.of())
                    .build();

            String token = jwtService.generateToken(noRoleUser);
            List<String> permissions = claimAsList(jwtService.extractClaims(token), "permissions");

            assertThat(permissions).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // isValid
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @Test
        @DisplayName("Token hợp lệ → true")
        void validToken_shouldReturnTrue() {
            when(permissionService.getPermissions(anyList())).thenReturn(Set.of());
            String token = jwtService.generateToken(user("user", "ROLE_USER"));

            assertThat(jwtService.isValid(token)).isTrue();
        }

        @Test
        @DisplayName("Token rác → false")
        void malformedToken_shouldReturnFalse() {
            assertThat(jwtService.isValid("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("Token ký bằng key khác → false")
        void wrongKeyToken_shouldReturnFalse() {
            String wrongSecret = "d3JvbmdfX19rZXlfX190aGlzX2lzX25vdF92YWxpZF9rZXlfYXRfYWxs";
            JwtService otherService = new JwtService(permissionService);
            ReflectionTestUtils.setField(otherService, "secret", wrongSecret);
            ReflectionTestUtils.setField(otherService, "expiration", 86400000L);
            when(permissionService.getPermissions(anyList())).thenReturn(Set.of());

            String tokenWithWrongKey = otherService.generateToken(user("user", "ROLE_USER"));

            assertThat(jwtService.isValid(tokenWithWrongKey)).isFalse();
        }

        @Test
        @DisplayName("Token hết hạn → false")
        void expiredToken_shouldReturnFalse() {
            JwtService expiredService = new JwtService(permissionService);
            ReflectionTestUtils.setField(expiredService, "secret", SECRET);
            ReflectionTestUtils.setField(expiredService, "expiration", -1000L);
            when(permissionService.getPermissions(anyList())).thenReturn(Set.of());

            String expiredToken = expiredService.generateToken(user("user", "ROLE_USER"));

            assertThat(jwtService.isValid(expiredToken)).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // extractClaims
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("extractClaims")
    class ExtractClaims {

        @Test
        @DisplayName("Extract đúng subject")
        void shouldExtractSubject() {
            when(permissionService.getPermissions(anyList())).thenReturn(Set.of());
            String token = jwtService.generateToken(user("admin@test.com", "ROLE_ADMIN"));

            assertThat(jwtService.extractClaims(token).getSubject()).isEqualTo("admin@test.com");
        }

        @Test
        @DisplayName("Token không hợp lệ → throw JwtException")
        void invalidToken_shouldThrow() {
            assertThatThrownBy(() -> jwtService.extractClaims("garbage"))
                    .isInstanceOf(io.jsonwebtoken.JwtException.class);
        }
    }
}

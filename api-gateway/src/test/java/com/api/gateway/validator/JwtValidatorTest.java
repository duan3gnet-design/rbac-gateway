package com.api.gateway.validator;

import com.auth.service.dto.ClaimsResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtValidator")
class JwtValidatorTest {

    private static final String SECRET = "bXlfc3VwZXJfc2VjcmV0X2tleV9mb3JfcmJhY19nYXRld2F5XzIwMjQ=";

    private JwtValidator jwtValidator;

    @BeforeEach
    void setUp() {
        jwtValidator = new JwtValidator();
        ReflectionTestUtils.setField(jwtValidator, "secret", SECRET);
        jwtValidator.init();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private String buildToken(String subject, List<String> roles, List<String> permissions, long expiryMs) {
        return Jwts.builder()
                .subject(subject)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(secretKey())
                .compact();
    }

    private String buildExpiredToken(String subject) {
        return Jwts.builder()
                .subject(subject)
                .claim("roles", List.of("ROLE_USER"))
                .claim("permissions", List.of("products:READ"))
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(secretKey())
                .compact();
    }

    private String buildTokenWithWrongKey(String subject) {
        String wrongSecret = "d3JvbmdfX19rZXlfX190aGlzX2lzX25vdF92YWxpZF9rZXlfYXRfYWxs";
        SecretKey wrongKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(wrongSecret));
        return Jwts.builder()
                .subject(subject)
                .claim("roles", List.of("ROLE_USER"))
                .claim("permissions", List.of("products:READ"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(wrongKey)
                .compact();
    }

    // ─── VALID TOKEN ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("valid token")
    class ValidToken {

        @Test
        @DisplayName("Parse đúng username từ subject")
        void shouldParseUsername() {
            String token = buildToken("phan@example.com",
                    List.of("ROLE_USER"), List.of("products:READ"), 60_000);

            ClaimsResponse claims = jwtValidator.validate(token);
            assertThat(claims.username()).isEqualTo("phan@example.com");
        }

        @Test
        @DisplayName("Parse đúng roles list")
        void shouldParseRoles() {
            String token = buildToken("user1",
                    List.of("ROLE_ADMIN", "ROLE_USER"), List.of("users:READ"), 60_000);

            ClaimsResponse claims = jwtValidator.validate(token);
            assertThat(claims.roles()).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        }

        @Test
        @DisplayName("Parse đúng permissions set")
        void shouldParsePermissions() {
            String token = buildToken("user1",
                    List.of("ROLE_USER"),
                    List.of("products:READ", "orders:READ", "orders:CREATE"),
                    60_000);

            ClaimsResponse claims = jwtValidator.validate(token);
            assertThat(claims.permissions())
                    .containsExactlyInAnyOrder("products:READ", "orders:READ", "orders:CREATE");
        }

        @Test
        @DisplayName("Token không có permissions claim → trả về empty set (không throw)")
        void tokenWithoutPermissions_shouldReturnEmptySet() {
            String token = Jwts.builder()
                    .subject("user-no-perms")
                    .claim("roles", List.of("ROLE_GUEST"))
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(secretKey())
                    .compact();

            ClaimsResponse claims = jwtValidator.validate(token);
            assertThat(claims.permissions()).isEmpty();
            assertThat(claims.username()).isEqualTo("user-no-perms");
        }

        @Test
        @DisplayName("ClaimsResponse là record đúng kiểu")
        void shouldReturnClaimsResponseRecord() {
            String token = buildToken("admin", List.of("ROLE_ADMIN"), List.of("users:DELETE"), 60_000);
            assertThat(jwtValidator.validate(token)).isInstanceOf(ClaimsResponse.class);
        }
    }

    // ─── INVALID TOKEN ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("invalid token")
    class InvalidToken {

        @Test
        @DisplayName("Token hết hạn → throw exception")
        void expiredToken_shouldThrow() {
            assertThatThrownBy(() -> jwtValidator.validate(buildExpiredToken("user1")))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Token ký bằng wrong key → throw exception")
        void wrongKeyToken_shouldThrow() {
            assertThatThrownBy(() -> jwtValidator.validate(buildTokenWithWrongKey("attacker")))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Token rác (random string) → throw exception")
        void malformedToken_shouldThrow() {
            assertThatThrownBy(() -> jwtValidator.validate("this.is.not.a.jwt"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Token rỗng → throw exception")
        void emptyToken_shouldThrow() {
            assertThatThrownBy(() -> jwtValidator.validate(""))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Token chỉ có 2 phần (thiếu signature) → throw exception")
        void tokenMissingSignature_shouldThrow() {
            assertThatThrownBy(() -> jwtValidator.validate("header.payload"))
                    .isInstanceOf(Exception.class);
        }
    }
}

package com.api.gateway.validator;

import com.api.gateway.config.OidcGatewayProperties;
import com.auth.service.dto.ClaimsResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.JwtException;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link JwtValidator} – RS256 / JWKS mode.
 *
 * A WireMock server stands in for auth-service's /oauth2/jwks endpoint so
 * the tests run completely offline with no real auth-service needed.
 *
 * RSA key pair is generated once per test class (@BeforeAll) and shared
 * across all test methods for speed.
 */
@DisplayName("JwtValidator (RS256 / JWKS)")
class JwtValidatorTest {

    // ── shared test infrastructure ────────────────────────────────────────────

    private static WireMockServer wireMock;
    private static KeyPair        rsaKeyPair;
    private static String         jwksJson;
    private static JwtValidator   validator;

    private static final String ISSUER = "http://test-issuer";

    @BeforeAll
    static void startInfrastructure() throws Exception {
        // 1. Generate RSA key pair (2048-bit)
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, new SecureRandom());
        rsaKeyPair = gen.generateKeyPair();

        // 2. Build JWKS JSON from public key
        jwksJson = buildJwksJson((RSAPublicKey) rsaKeyPair.getPublic(), "test-kid-01");

        // 3. Start WireMock and stub /oauth2/jwks
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        stubJwks();

        // 4. Wire up JwtValidator pointing to WireMock
        OidcGatewayProperties props = new OidcGatewayProperties();
        props.setJwksUri("http://localhost:" + wireMock.port() + "/oauth2/jwks");
        props.setIssuerUri(ISSUER);
        props.setJwksCacheTtlSeconds(60);

        validator = new JwtValidator(props);
    }

    @AfterAll
    static void stopInfrastructure() {
        if (wireMock != null) wireMock.stop();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Sign a JWT with the test RSA private key. */
    private String buildToken(String subject,
                              List<String> roles,
                              List<String> permissions,
                              long expiryMs) {
        Date now = new Date();
        var builder = Jwts.builder()
                .header().keyId("test-kid-01").and()
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .signWith(rsaKeyPair.getPrivate(), Jwts.SIG.RS256);

        if (roles != null)       builder.claim("roles", roles);
        if (permissions != null) builder.claim("permissions", permissions);

        return builder.compact();
    }

    private String buildExpiredToken(String subject) {
        Date past = new Date(System.currentTimeMillis() - 100_000);
        return Jwts.builder()
                .header().keyId("test-kid-01").and()
                .issuer(ISSUER)
                .subject(subject)
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(new Date(System.currentTimeMillis() - 20_000))
                .expiration(past)
                .signWith(rsaKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String buildTokenWithWrongKey(String subject) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair wrongPair = gen.generateKeyPair();
        return Jwts.builder()
                .header().keyId("test-kid-01").and()
                .issuer(ISSUER)
                .subject(subject)
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(wrongPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String buildTokenWrongIssuer(String subject) {
        return Jwts.builder()
                .header().keyId("test-kid-01").and()
                .issuer("https://evil.com")
                .subject(subject)
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(rsaKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static void stubJwks() {
        wireMock.stubFor(get(urlEqualTo("/oauth2/jwks"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwksJson)));
    }

    /** Build a minimal JWKS JSON string from an RSA public key. */
    private static String buildJwksJson(RSAPublicKey pub, String kid) {
        String n = base64url(pub.getModulus());
        String e = base64url(pub.getPublicExponent());
        return """
                {"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"%s","n":"%s","e":"%s"}]}
                """.formatted(kid, n, e).strip();
    }

    private static String base64url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ── VALID TOKEN ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("valid token")
    class ValidToken {

        @Test
        @DisplayName("Parse đúng username từ subject")
        void shouldParseUsername() {
            String token = buildToken("phan@example.com", List.of("ROLE_USER"),
                    List.of("products:READ"), 60_000);

            ClaimsResponse claims = validator.validate(token);
            assertThat(claims.username()).isEqualTo("phan@example.com");
        }

        @Test
        @DisplayName("Parse đúng roles list")
        void shouldParseRoles() {
            String token = buildToken("user1", List.of("ROLE_ADMIN", "ROLE_USER"),
                    List.of("users:READ"), 60_000);

            ClaimsResponse claims = validator.validate(token);
            assertThat(claims.roles()).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        }

        @Test
        @DisplayName("Parse đúng permissions set")
        void shouldParsePermissions() {
            String token = buildToken("user1", List.of("ROLE_USER"),
                    List.of("products:READ", "orders:READ", "orders:CREATE"), 60_000);

            ClaimsResponse claims = validator.validate(token);
            assertThat(claims.permissions())
                    .containsExactlyInAnyOrder("products:READ", "orders:READ", "orders:CREATE");
        }

        @Test
        @DisplayName("Token không có permissions claim → trả về empty set (không throw)")
        void tokenWithoutPermissions_shouldReturnEmptySet() {
            String token = buildToken("user-no-perms", List.of("ROLE_GUEST"), null, 60_000);

            ClaimsResponse claims = validator.validate(token);
            assertThat(claims.permissions()).isEmpty();
            assertThat(claims.username()).isEqualTo("user-no-perms");
        }

        @Test
        @DisplayName("ClaimsResponse đúng kiểu")
        void shouldReturnClaimsResponseRecord() {
            String token = buildToken("admin", List.of("ROLE_ADMIN"), List.of("users:DELETE"), 60_000);
            assertThat(validator.validate(token)).isInstanceOf(ClaimsResponse.class);
        }
    }

    // ── INVALID TOKEN ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("invalid token")
    class InvalidToken {

        @Test
        @DisplayName("Token hết hạn → throw JwtException")
        void expiredToken_shouldThrow() {
            assertThatThrownBy(() -> validator.validate(buildExpiredToken("user1")))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Token ký bằng wrong key → throw JwtException")
        void wrongKeyToken_shouldThrow() {
            assertThatThrownBy(() -> validator.validate(buildTokenWithWrongKey("attacker")))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Issuer sai → throw JwtException")
        void wrongIssuer_shouldThrow() {
            assertThatThrownBy(() -> validator.validate(buildTokenWrongIssuer("user1")))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Token rác (random string) → throw exception")
        void malformedToken_shouldThrow() {
            assertThatThrownBy(() -> validator.validate("this.is.not.a.jwt"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Token rỗng → throw exception")
        void emptyToken_shouldThrow() {
            assertThatThrownBy(() -> validator.validate(""))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Token chỉ có 2 phần (thiếu signature) → throw exception")
        void tokenMissingSignature_shouldThrow() {
            assertThatThrownBy(() -> validator.validate("header.payload"))
                    .isInstanceOf(Exception.class);
        }
    }
}

package com.api.gateway.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Test utility – generates a disposable RSA key pair and provides helpers to:
 *   1. Stub a WireMock server to serve the matching JWKS.
 *   2. Mint RS256 signed JWTs that the gateway's {@code JwtValidator} will accept.
 *
 * Usage in integration tests:
 * <pre>{@code
 *   // In @BeforeAll / static setup:
 *   RsaKeyTestUtil rsa = RsaKeyTestUtil.generate();
 *   rsa.stubJwks(wireMock, "http://test-issuer");   // stubs /oauth2/jwks
 *   System.setProperty("TEST_JWKS_URI",   "http://localhost:" + wireMock.port() + "/oauth2/jwks");
 *   System.setProperty("TEST_ISSUER_URI", "http://test-issuer");
 *
 *   // In test methods:
 *   String token = rsa.mintToken("user@example.com",
 *                                List.of("ROLE_USER"),
 *                                List.of("products:READ"),
 *                                "http://test-issuer",
 *                                Duration.ofMinutes(5));
 * }</pre>
 */
@Slf4j
public class RsaKeyTestUtil {

    private static final String KID = "test-kid";

    private final KeyPair  keyPair;
    private final String   jwksJson;

    private RsaKeyTestUtil(KeyPair keyPair) {
        this.keyPair  = keyPair;
        this.jwksJson = buildJwks((RSAPublicKey) keyPair.getPublic(), KID);
    }

    /** Generate a fresh 2048-bit RSA key pair. */
    public static RsaKeyTestUtil generate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, new SecureRandom());
            return new RsaKeyTestUtil(gen.generateKeyPair());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    // ── JWKS stub ────────────────────────────────────────────────────────────

    /**
     * Registers a WireMock stub that serves the JWKS at {@code GET /oauth2/jwks}.
     * Call this before the Spring context starts so JwtValidator can fetch the key.
     */
    public void stubJwks(WireMockServer server) {
        log.info("stubJwks http://localhost:{}", server.port());
        log.info("jwksJson: {}", jwksJson);
        server.stubFor(get(urlEqualTo("/oauth2/jwks"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwksJson)));
    }

    // ── Token minting ─────────────────────────────────────────────────────────

    /**
     * Mints a valid RS256 JWT signed by this key pair.
     *
     * @param subject     JWT subject (username / email)
     * @param roles       List of role names, e.g. ["ROLE_USER"]
     * @param permissions List of permission codes, e.g. ["products:READ"]
     * @param issuer      Value for 'iss' claim – must match oidc.issuer-uri in test config
     * @param expiryMs    Token lifetime in milliseconds from now
     */
    public String mintToken(String subject,
                            List<String> roles,
                            List<String> permissions,
                            String issuer,
                            long expiryMs) {
        Date now = new Date();
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer(issuer)
                .subject(subject)
                .claim("roles",       roles)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /** Convenience overload with 5-minute expiry. */
    public String mintToken(String subject,
                            List<String> roles,
                            List<String> permissions,
                            String issuer) {
        return mintToken(subject, roles, permissions, issuer, 5 * 60 * 1_000L);
    }

    /** Mints an already-expired token (useful for 401 test cases). */
    public String mintExpiredToken(String subject, String issuer) {
        Date past = new Date(System.currentTimeMillis() - 50_000);
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer(issuer)
                .subject(subject)
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(new Date(System.currentTimeMillis() - 15_000))
                .expiration(past)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String buildJwks(RSAPublicKey pub, String kid) {
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
}

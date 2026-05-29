package com.auth.service.oidc;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Exposes the JSON Web Key Set (JWKS) containing the RSA public key.
 * <p>
 * GET /oauth2/jwks
 * <p>
 * Kubernetes API server uses this endpoint to fetch the public key and
 * verify service-account tokens (or OIDC user tokens) without calling
 * back to the auth-service on every request.
 * <p>
 * The response is intentionally cache-friendly (no sensitive data).
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final RsaKeyConfig rsaKeyConfig;

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        var key = rsaKeyConfig.getPublicKey();

        // Encode modulus (n) and exponent (e) as Base64url without padding
        String n = base64url(key.getModulus());
        String e = base64url(key.getPublicExponent());

        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", rsaKeyConfig.getKeyId(),
                "n",   n,
                "e",   e
        );

        return Map.of("keys", List.of(jwk));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String base64url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // Remove leading zero byte that BigInteger may add for sign
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

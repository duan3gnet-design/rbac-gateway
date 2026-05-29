package com.auth.service.oidc;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Loads or generates an RSA key-pair used to sign JWT tokens (RS256).
 * <p>
 * Priority:
 *   1. Load from PEM file if oidc.rsa-key-path is set and the file exists.
 *   2. Generate a new 2048-bit RSA key-pair in memory (dev/test mode).
 * <p>
 * The public key is exposed via /oauth2/jwks so that k8s API server
 * and other relying parties can verify tokens without calling back to
 * the auth-service on every request.
 */
@Slf4j
@Component
@Getter
public class RsaKeyConfig {

    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;

    /** Stable key-ID embedded in every JWT header ("kid" claim).
     *  k8s caches JWKs by kid, so keeping it stable avoids cache misses. */
    private final String keyId;

    public RsaKeyConfig(OidcProperties props) throws Exception {
        KeyPair pair = loadOrGenerate(props.getRsaKeyPath());
        this.privateKey = pair.getPrivate();
        this.publicKey  = (RSAPublicKey) pair.getPublic();
        this.keyId      = UUID.randomUUID().toString().substring(0, 8); // short but unique per boot
        log.info("[OIDC] RSA key loaded. kid={}", keyId);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private KeyPair loadOrGenerate(String rsaKeyPath) throws Exception {
        if (rsaKeyPath != null && !rsaKeyPath.isBlank()) {
            Path p = Path.of(rsaKeyPath);
            if (Files.exists(p)) {
                log.info("[OIDC] Loading RSA private key from {}", p);
                return loadFromPem(Files.readString(p));
            }
            log.warn("[OIDC] RSA key file not found at '{}', generating ephemeral key", rsaKeyPath);
        } else {
            log.info("[OIDC] No RSA key path configured – generating ephemeral key (fine for dev)");
        }
        return generate();
    }

    private KeyPair loadFromPem(String pem) throws Exception {
        String stripped = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(stripped);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(decoded));

        // Derive the public key from the private key
        java.security.interfaces.RSAPrivateCrtKey crt =
                (java.security.interfaces.RSAPrivateCrtKey) privateKey;
        java.security.spec.RSAPublicKeySpec pub =
                new java.security.spec.RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent());
        PublicKey publicKey = kf.generatePublic(pub);
        return new KeyPair(publicKey, privateKey);
    }

    private KeyPair generate() throws NoSuchAlgorithmException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, new SecureRandom());
        return gen.generateKeyPair();
    }
}

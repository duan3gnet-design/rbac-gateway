package com.auth.service.oidc;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OIDC / OAuth2 issuer properties.
 * <p>
 * yaml:
 *   oidc:
 *     issuer-uri: <a href="https://auth.example.com">...</a>          # public URL (used in discovery doc)
 *     rsa-key-path: /etc/auth/keys/rsa_private.pem  # optional, falls back to generated key
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "oidc")
public class OidcProperties {

    /** Issuer URI published in /.well-known/openid-configuration */
    private String issuerUri = "http://localhost:8081";

    /**
     * Path to a PEM-encoded PKCS#8 RSA private key file.
     * If blank, a new key-pair is generated in memory on startup.
     */
    private String rsaKeyPath = "";
}

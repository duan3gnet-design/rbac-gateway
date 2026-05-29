package com.api.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OIDC / JWKS configuration for the API Gateway.
 *
 * yaml:
 *   oidc:
 *     jwks-uri:   http://auth-service:8081/oauth2/jwks   # where to fetch the public key
 *     issuer-uri: http://auth-service:8081               # validated against JWT 'iss' claim
 *
 * The gateway NEVER shares a secret with auth-service.
 * It only needs the *public* key, which is fetched from jwks-uri at startup
 * and automatically refreshed when a new kid (key-ID) is encountered.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "oidc")
public class OidcGatewayProperties {

    /**
     * JWKS endpoint of the auth-service.
     * NimbusJwtDecoder uses this to download and cache the RSA public key(s).
     */
    private String jwksUri = "http://localhost:8081/oauth2/jwks";

    /**
     * Expected issuer – validated against the 'iss' claim in every JWT.
     * Must match oidc.issuer-uri set in auth-service.
     */
    private String issuerUri = "http://localhost:8081";

    /**
     * How long (seconds) to cache the JWKS locally before re-fetching.
     * Setting too low → extra HTTP calls; too high → slow key rotation.
     * Default 300 s = 5 min.
     */
    private long jwksCacheTtlSeconds = 300;
}

package com.api.gateway.validator;

import com.api.gateway.config.OidcGatewayProperties;
import com.auth.service.dto.ClaimsResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JWT validator – upgraded from HS256 (shared-secret) to RS256 (public-key).
 * <p>
 * Key design decisions
 * ────────────────────
 * 1. Uses Spring's {@link NimbusJwtDecoder} which downloads the JWKS from
 *    auth-service's {@code /oauth2/jwks} endpoint at first use and caches it.
 * <p>
 * 2. Key rotation is handled transparently: when a token arrives with an
 *    unknown {@code kid}, Nimbus re-fetches the JWKS automatically.
 *    No restart required after key rotation in auth-service.
 * <p>
 * 3. Issuer ({@code iss} claim) is validated against {@code oidc.issuer-uri}
 *    from application.yml – rejects tokens from other issuers.
 * <p>
 * 4. The gateway never holds the private key or any shared secret.
 *    Only the RSA public key (via JWKS) is needed.
 * <p>
 * 5. Thread-safe: NimbusJwtDecoder is stateless after construction;
 *    the single instance is reused across all virtual threads.
 */
@Slf4j
@Component
public class JwtValidator {

    private final JwtDecoder jwtDecoder;

    public JwtValidator(OidcGatewayProperties props) {
        // 2. Wrap it in Spring's CaffeineCache
        // 1. Create the underlying cache with your desired Duration
        // Set your Duration here
        Cache<Object, Object> caffeineCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(props.getJwksCacheTtlSeconds())) // Set your Duration here
                .build();
        org.springframework.cache.Cache springCache = new CaffeineCache("jwks", caffeineCache);
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(props.getJwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                // Cache JWKS for configured TTL; re-fetch automatically on unknown kid
                .cache(springCache)
                .build();

        // Validate 'iss' claim so we reject tokens signed by a different issuer
        JwtIssuerValidator issuerValidator = new JwtIssuerValidator(props.getIssuerUri());

        // Standard exp / nbf / iat validation
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();

        decoder.setJwtValidator(token -> {
            OAuth2TokenValidatorResult issuerResult    = issuerValidator.validate(token);
            OAuth2TokenValidatorResult timestampResult = timestampValidator.validate(token);

            if (issuerResult.hasErrors())    return issuerResult;
            if (timestampResult.hasErrors()) return timestampResult;
            return OAuth2TokenValidatorResult.success();
        });

        this.jwtDecoder = decoder;
        log.info("[JwtValidator] RS256/JWKS mode – jwks-uri={}, issuer={}",
                props.getJwksUri(), props.getIssuerUri());
    }

    // ── public API (unchanged contract, used by JwtAuthenticationFilter) ────

    /**
     * Validates the token and extracts claims.
     * Throws {@link JwtException} (→ 401) on any validation failure.
     */
    public ClaimsResponse validate(String token) {
        Jwt jwt = jwtDecoder.decode(token);   // throws JwtException if invalid

        List<String> roles = jwt.getClaimAsStringList("roles");

        List<String> rawPermissions = jwt.getClaimAsStringList("permissions");
        Set<String> permissions = rawPermissions != null
                ? new HashSet<>(rawPermissions)
                : Set.of();

        return new ClaimsResponse(
                jwt.getSubject(),
                roles  != null ? roles : List.of(),
                permissions
        );
    }
}

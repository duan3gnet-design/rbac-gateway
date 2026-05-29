package com.auth.service.service;

import com.auth.service.oidc.OidcProperties;
import com.auth.service.oidc.RsaKeyConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * JWT service – upgraded to RS256 (asymmetric signing).
 * <p>
 * Why RS256 for k8s OIDC?
 *  • k8s API server only needs the *public* key (from /oauth2/jwks) to verify tokens.
 *  • HMAC (HS256) would require sharing the secret with k8s, which is a security risk.
 *  • RS256 is the OIDC standard; all compliant IdP validators accept it.
 * <p>
 * Tokens are still 100% backward-compatible with api-gateway (JwtValidator reads
 * the same Claims regardless of signing algorithm).
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final PermissionService permissionService;
    private final RsaKeyConfig      rsaKeyConfig;
    private final OidcProperties    oidcProperties;

    @Value("${jwt.expiration:86400000}")
    private long expirationMs;

    // ── Access Token ─────────────────────────────────────────────────────────

    /**
     * Generates a signed RS256 JWT access token.
     * Claims: sub, iss, iat, exp, roles, permissions, kid (header)
     */
    public String generateToken(UserDetails user) {
        List<String> roles       = extractRoles(user);
        Set<String>  permissions = permissionService.getPermissions(roles);
        Date         now         = new Date();

        return Jwts.builder()
                .header()
                    .keyId(rsaKeyConfig.getKeyId())  // kid – k8s uses this to select the right JWK
                    .and()
                .issuer(oidcProperties.getIssuerUri())
                .subject(user.getUsername())
                .claim("roles",       roles)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(rsaKeyConfig.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    // ── ID Token (OIDC) ──────────────────────────────────────────────────────

    /**
     * Generates an OIDC ID Token.
     * Follows https://openid.net/specs/openid-connect-core-1_0.html#IDToken
     * <p>
     * Claims: iss, sub, aud (issuer), exp, iat, email, name, roles
     * <p>
     * The ID Token is shorter than the access token – it carries identity
     * info for the client, not the full permission set.
     */
    public String generateIdToken(UserDetails user) {
        List<String> roles = extractRoles(user);
        Date         now   = new Date();

        return Jwts.builder()
                .header()
                    .keyId(rsaKeyConfig.getKeyId())
                    .and()
                .issuer(oidcProperties.getIssuerUri())
                .subject(user.getUsername())
                .audience().add(oidcProperties.getIssuerUri()).and()
                .claim("email",  user.getUsername())   // username == email for OAuth2 users
                .claim("roles",  roles)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(rsaKeyConfig.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    // ── Verification ─────────────────────────────────────────────────────────

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(rsaKeyConfig.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns token expiration in *seconds* (used by OAuth2 token response). */
    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    private List<String> extractRoles(UserDetails user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}

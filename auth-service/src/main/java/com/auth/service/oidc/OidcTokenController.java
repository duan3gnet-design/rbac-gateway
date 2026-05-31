package com.auth.service.oidc;

import com.auth.service.dto.TokenPair;
import com.auth.service.service.JwtService;
import com.auth.service.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OAuth2 Token Endpoint – POST /oauth2/token
 * <p>
 * Supports grant types:
 *   • password           – username + password → access_token + refresh_token + id_token
 *   • refresh_token      – rotate refresh token
 *   • client_credentials – service-to-service (returns access_token only, no id_token)
 * <p>
 * Request: application/x-www-form-urlencoded  (standard OAuth2)
 * Response: application/json
 * <p>
 * The returned access_token is a signed RS256 JWT that Kubernetes (and other
 * relying parties) can verify using the public key exposed at /oauth2/jwks.
 * <p>
 * id_token (for OIDC flows) is also a signed RS256 JWT that includes
 * standard OIDC claims (iss, sub, aud, exp, iat, email, name).
 */
@Slf4j
@RestController
@RequestMapping("/oauth2/token")
@RequiredArgsConstructor
public class OidcTokenController {

    private final AuthenticationManager   authenticationManager;
    private final UserDetailsService      userDetailsService;
    private final JwtService              jwtService;
    private final RefreshTokenService     refreshTokenService;

    // ── grant: password ──────────────────────────────────────────────────────

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> token(
            @RequestParam("grant_type")                    String grantType,
            @RequestParam(value = "username",      required = false) String username,
            @RequestParam(value = "password",      required = false) String password,
            @RequestParam(value = "refresh_token", required = false) String rawRefreshToken,
            @RequestParam(value = "client_id",     required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "scope",         required = false) String scope
    ) {
        return switch (grantType) {
            case "password"            -> handlePassword(username, password, clientId);
            case "refresh_token"       -> handleRefreshToken(rawRefreshToken);
            case "client_credentials"  -> handleClientCredentials(clientId, clientSecret);
            default -> throw new IllegalArgumentException("Unsupported grant_type: " + grantType);
        };
    }

    // ── handlers ─────────────────────────────────────────────────────────────

    private Map<String, Object> handlePassword(String username, String password, String clientId) {
        if (username == null || password == null) {
            throw new IllegalArgumentException("username and password are required for password grant");
        }
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        UserDetails user = userDetailsService.loadUserByUsername(username);

        String accessToken  = jwtService.generateToken(user, clientId);
        String idToken      = jwtService.generateIdToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(username).getToken();
        long   expiresIn    = jwtService.getExpirationSeconds();

        log.debug("[OIDC] password grant issued tokens for user={}", username);
        return buildTokenResponse(accessToken, refreshToken, idToken, expiresIn);
    }

    private Map<String, Object> handleRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            throw new IllegalArgumentException("refresh_token is required");
        }
        TokenPair pair = refreshTokenService.rotate(rawRefreshToken);

        UserDetails user = userDetailsService.loadUserByUsername(
                jwtService.extractUsername(pair.accessToken()));

        String idToken   = jwtService.generateIdToken(user);
        long   expiresIn = jwtService.getExpirationSeconds();

        log.debug("[OIDC] refresh_token grant rotated tokens");
        return buildTokenResponse(pair.accessToken(), pair.refreshToken(), idToken, expiresIn);
    }

    private Map<String, Object> handleClientCredentials(String clientId, String clientSecret) {
        if (clientId == null || clientSecret == null) {
            throw new IllegalArgumentException("client_id and client_secret are required");
        }
        // Delegate credential verification to AuthenticationManager using the
        // client-as-user pattern: clientId acts as the username.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(clientId, clientSecret));

        UserDetails client = userDetailsService.loadUserByUsername(clientId);
        String accessToken = jwtService.generateToken(client);
        long   expiresIn   = jwtService.getExpirationSeconds();

        log.debug("[OIDC] client_credentials grant issued token for client={}", clientId);
        // No refresh_token or id_token for client_credentials
        return Map.of(
                "access_token", accessToken,
                "token_type",   "Bearer",
                "expires_in",   expiresIn
        );
    }

    // ── response builder ─────────────────────────────────────────────────────

    private Map<String, Object> buildTokenResponse(
            String accessToken, String refreshToken, String idToken, long expiresIn) {
        return Map.of(
                "access_token",  accessToken,
                "token_type",    "Bearer",
                "expires_in",    expiresIn,
                "refresh_token", refreshToken,
                "id_token",      idToken
        );
    }
}

package com.auth.service.controller;

import com.auth.service.dto.*;
import com.auth.service.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Auth endpoints — now with MFA support.
 *
 * <p>Login flow:
 * <pre>
 * Case A — MFA disabled:
 *   POST /api/auth/login → 200 { accessToken, refreshToken }
 *
 * Case B — MFA enabled:
 *   POST /api/auth/login → 202 { mfaSessionToken, message }
 *   POST /api/auth/mfa/verify { mfaSessionToken, code } → 200 { accessToken, refreshToken }
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService            jwtService;
    private final UserDetailsService    userDetailsService;
    private final UserService           userService;
    private final RefreshTokenService   refreshTokenService;
    private final GoogleAuthService     googleAuthService;
    private final MfaService            mfaService;

    // ── Login / Register ────────────────────────────────────────────────────

    /**
     * Standard username/password login.
     * Returns 200 with tokens if MFA is not enabled.
     * Returns 202 with mfaSessionToken if MFA is required.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Validated LoginRequest req) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password())
        );

        // Check if user has MFA enabled
        if (userService.isMfaEnabled(req.username())) {
            String mfaSessionToken = mfaService.createMfaSession(req.username());
            return ResponseEntity.accepted()
                    .body(new MfaRequiredResponse(mfaSessionToken,
                            "Vui lòng nhập mã xác thực MFA để tiếp tục"));
        }

        var userDetails = userDetailsService.loadUserByUsername(req.username());
        String accessToken  = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(req.username()).getToken();
        return ResponseEntity.ok(new TokenResponse(accessToken, refreshToken));
    }

    /**
     * MFA verification step — completes login after MFA check.
     * Accepts either a 6-digit TOTP code or an 8-char backup code.
     */
    @PostMapping("/mfa/verify")
    public ResponseEntity<TokenResponse> verifyMfa(
            @RequestBody @Valid MfaVerifyRequest req) {
        // Consume the MFA session (validates + deletes from Redis)
        String username = mfaService.consumeMfaSession(req.mfaSessionToken());

        if (!mfaService.verifyMfaCode(username, req.code())) {
            return ResponseEntity.status(401).build();
        }

        var userDetails = userDetailsService.loadUserByUsername(username);
        String accessToken  = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(username).getToken();
        return ResponseEntity.ok(new TokenResponse(accessToken, refreshToken));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody @Validated RegisterRequest req) {
        userService.register(req);
        return ResponseEntity.status(201).body("User created");
    }

    // ── Token management ────────────────────────────────────────────────────

    @GetMapping("/validate")
    public ResponseEntity<ClaimsResponse> validate(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(new ClaimsResponse(
                "Test performance",
                List.of(), Set.of()

        ));
//        String token = authHeader.replace("Bearer ", "");
//        if (!jwtService.isValid(token)) {
//            return ResponseEntity.status(401).build();
//        }
//
//        Claims claims = jwtService.extractClaims(token);
//        List<String> roles = claims.get("roles", List.class);
//
//        Set<String> permissions = permissionService.getPermissions(roles);
//
//        return ResponseEntity.ok(new ClaimsResponse(
//                claims.getSubject(),
//                roles,
//                permissions
//        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPair> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(refreshTokenService.rotate(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.revokeToken(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @RequestHeader("X-User-Name") String username) {
        refreshTokenService.revokeAllTokens(username);
        return ResponseEntity.noContent().build();
    }

    // ── Google OAuth2 ───────────────────────────────────────────────────────

    @PostMapping("/google")
    public ResponseEntity<TokenResponse> googleLogin(
            @Valid @RequestBody GoogleTokenRequest request) {
        return ResponseEntity.ok(googleAuthService.authenticate(request.idToken()));
    }
}

package com.auth.service.controller;

import com.auth.service.dto.MfaBackupCodesResponse;
import com.auth.service.dto.MfaSetupResponse;
import com.auth.service.service.MfaService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * MFA management endpoints — require authenticated user (valid JWT via GatewayAuthFilter).
 */
@RestController
@RequestMapping("/api/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;

    /**
     * Step 1: Start MFA setup — generates QR code + secret.
     * The returned QR code should be scanned in Google Authenticator / Authy.
     */
    @PostMapping("/setup")
    public ResponseEntity<MfaSetupResponse> setup(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(mfaService.initSetup(userDetails.getUsername()));
    }

    /**
     * Step 2: Enable MFA by confirming with the first TOTP code.
     * Returns one-time backup codes — show to user ONCE and store safely.
     */
    @PostMapping("/enable")
    public ResponseEntity<MfaBackupCodesResponse> enable(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @NotBlank @Size(min = 6, max = 6) String code) {
        return ResponseEntity.ok(mfaService.enableMfa(userDetails.getUsername(), code));
    }

    /**
     * Disable MFA. Requires current TOTP code for confirmation.
     */
    @PostMapping("/disable")
    public ResponseEntity<Void> disable(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @NotBlank @Size(min = 6, max = 6) String code) {
        mfaService.disableMfa(userDetails.getUsername(), code);
        return ResponseEntity.noContent().build();
    }

    /**
     * Regenerate backup codes. Invalidates all existing unused codes.
     * Requires current TOTP code for confirmation.
     */
    @PostMapping("/backup-codes/regenerate")
    public ResponseEntity<MfaBackupCodesResponse> regenerateBackupCodes(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @NotBlank @Size(min = 6, max = 6) String code) {
        return ResponseEntity.ok(
                mfaService.regenerateBackupCodes(userDetails.getUsername(), code));
    }
}

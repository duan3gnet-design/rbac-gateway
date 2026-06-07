package com.auth.service.dto;

/**
 * Returned when login succeeds but MFA verification is required.
 * The client must POST this session token + TOTP code to /api/auth/mfa/verify.
 */
public record MfaRequiredResponse(
        String mfaSessionToken,    // short-lived token stored in Redis
        String message
) {}

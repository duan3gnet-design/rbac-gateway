package com.auth.service.dto;

/** Returned when MFA setup begins: contains the QR URI and raw secret for manual entry. */
public record MfaSetupResponse(
        String secret,          // Base32 secret for manual entry
        String otpauthUri,      // otpauth://totp/... URI for QR code generation
        String qrCodeDataUrl    // data:image/png;base64,... ready for <img> tag
) {}

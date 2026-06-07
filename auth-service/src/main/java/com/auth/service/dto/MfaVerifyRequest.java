package com.auth.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaVerifyRequest(
        @NotBlank String mfaSessionToken,
        @NotBlank @Size(min = 6, max = 8) String code   // 6-digit TOTP or 8-char backup code
) {}

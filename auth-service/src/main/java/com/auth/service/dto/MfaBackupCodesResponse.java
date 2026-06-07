package com.auth.service.dto;

import java.util.List;

/** Returned after successful MFA enable: contains one-time backup codes. */
public record MfaBackupCodesResponse(
        List<String> backupCodes,    // plain-text codes shown ONCE — must be saved by user
        String message
) {}

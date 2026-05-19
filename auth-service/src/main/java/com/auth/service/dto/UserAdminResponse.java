package com.auth.service.dto;

import java.util.Set;

/**
 * Response DTO cho User — trả về admin UI.
 */
public record UserAdminResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String provider,
        boolean enabled,
        Set<String> roles
) {}

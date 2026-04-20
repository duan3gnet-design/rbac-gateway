package com.resource.service.dto;

import java.util.Set;

/**
 * Mirror của UserSummary bên auth-service.
 * Không import trực tiếp auth-service để tránh coupling giữa modules.
 */
public record UserSummary(
        Long id,
        String username,
        String fullName,
        String provider,
        Set<String> roles
) {}

package com.auth.service.dto;

import java.util.Set;

public record UserSummary(
        Long id,
        String username,
        String fullName,
        String provider,
        Set<String> roles
) {}

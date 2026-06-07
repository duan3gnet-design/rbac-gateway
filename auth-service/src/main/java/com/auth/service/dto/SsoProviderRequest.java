package com.auth.service.dto;

import jakarta.validation.constraints.NotBlank;

public record SsoProviderRequest(
        @NotBlank String providerId,
        @NotBlank String displayName,
        String type,
        @NotBlank String issuerUri,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        String scopes,
        String defaultRoles,
        boolean enabled
) {}

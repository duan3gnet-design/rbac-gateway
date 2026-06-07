package com.auth.service.dto;

/** SSO provider info returned to frontend for building login buttons. */
public record SsoProviderResponse(
        String providerId,
        String displayName,
        String type,
        String loginUrl    // e.g. /oauth2/authorization/{providerId}
) {}

package com.resource.service.dto;

import java.time.OffsetDateTime;

public record UserProfileResponse(
        String username,
        String fullName,
        String provider,
        OffsetDateTime fetchedAt
) {}

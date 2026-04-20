package com.auth.service.dto;

import java.util.List;
import java.util.Set;

public record ClaimsResponse(
        String username,
        List<String> roles,
        Set<String> permissions
) {
}

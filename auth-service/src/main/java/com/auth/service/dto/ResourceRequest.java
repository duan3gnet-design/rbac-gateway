package com.auth.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO để tạo hoặc đổi tên một Resource.
 */
public record ResourceRequest(
        @NotBlank(message = "name is required")
        @Pattern(regexp = "^[a-z0-9_-]+$",
                 message = "name must be lowercase alphanumeric (a-z, 0-9, _, -)")
        String name
) {}

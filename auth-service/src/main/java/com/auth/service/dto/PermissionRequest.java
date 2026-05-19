package com.auth.service.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO để tạo hoặc cập nhật Permission.
 */
public record PermissionRequest(
        @NotBlank(message = "role is required")
        String role,

        @NotBlank(message = "resource is required")
        String resource,

        @NotBlank(message = "action is required")
        String action,

        String description
) {}

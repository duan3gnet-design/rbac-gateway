package com.auth.service.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO để tạo hoặc cập nhật Permission.
 * Không còn field role — role gán qua role_permissions.
 */
public record PermissionRequest(
        @NotBlank(message = "resource is required")
        String resource,

        @NotBlank(message = "action is required")
        String action
) {}

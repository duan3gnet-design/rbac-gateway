package com.auth.service.dto;

/**
 * Response DTO cho Permission.
 * Không còn field role — role được quản lý qua Role entity / role_permissions.
 */
public record PermissionResponse(
        Long id,
        String resource,
        String action,
        String code         // resource:ACTION — nhúng vào JWT
) {}

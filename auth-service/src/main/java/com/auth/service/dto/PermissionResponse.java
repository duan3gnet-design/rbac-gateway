package com.auth.service.dto;

/**
 * Response DTO cho Permission — trả về frontend.
 * code = resource:ACTION (dùng cho JWT claims và gateway RBAC check)
 */
public record PermissionResponse(
        Long id,
        String role,
        String resource,
        String action,
        String code,
        String description
) {}

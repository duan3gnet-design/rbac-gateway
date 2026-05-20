package com.auth.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

/**
 * Request DTO để tạo hoặc cập nhật Role.
 * permissionIds: set ID của permissions muốn gán (có thể rỗng).
 */
public record RoleRequest(
        @NotBlank(message = "name is required")
        @Pattern(regexp = "^ROLE_[A-Z0-9_]+$",
                 message = "name must follow pattern ROLE_XXX (uppercase, e.g. ROLE_ADMIN)")
        String name,

        Set<Long> permissionIds  // nullable / empty = không gán permission
) {}

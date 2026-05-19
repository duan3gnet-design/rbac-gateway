package com.auth.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/**
 * Request DTO để tạo hoặc cập nhật User từ admin UI.
 * password là Optional khi update (null = giữ nguyên).
 */
public record UserAdminRequest(
        @NotBlank(message = "username is required")
        String username,

        @Email(message = "email must be valid")
        String email,

        String fullName,

        String password,   // null khi update mà không đổi password

        @NotEmpty(message = "at least one role is required")
        Set<String> roles,

        boolean enabled
) {}

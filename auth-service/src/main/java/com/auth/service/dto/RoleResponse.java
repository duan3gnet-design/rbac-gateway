package com.auth.service.dto;

import java.util.List;

/**
 * Response DTO cho Role — kèm danh sách permissions đang được gán
 * và số users đang có role này.
 */
public record RoleResponse(
        Long id,
        String name,
        List<PermissionResponse> permissions,
        int userCount
) {}

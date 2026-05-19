package com.auth.service.dto;

import java.util.List;

/**
 * Response DTO cho Resource — kèm danh sách actions đang được gán.
 */
public record ResourceResponse(
        Long id,
        String name,
        List<ActionDto> actions,
        int permissionCount
) {
    public record ActionDto(Long id, String name) {}
}

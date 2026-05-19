package com.auth.service.controller;

import com.auth.service.dto.PermissionRequest;
import com.auth.service.dto.PermissionResponse;
import com.auth.service.service.AdminPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Admin API — quản lý Permissions (RBAC).
 * Chỉ ROLE_ADMIN (đến từ X-User-Roles header của api-gateway) mới được truy cập.
 */
@RestController
@RequestMapping("/api/auth/admin/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPermissionController {

    private final AdminPermissionService adminPermissionService;

    /** GET /api/admin/permissions — lấy toàn bộ danh sách */
    @GetMapping
    public ResponseEntity<List<PermissionResponse>> getAll() {
        return ResponseEntity.ok(adminPermissionService.findAll());
    }

    /** GET /api/admin/permissions/:id */
    @GetMapping("/{id}")
    public ResponseEntity<PermissionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(adminPermissionService.findById(id));
    }

    /** POST /api/admin/permissions — tạo mới */
    @PostMapping
    public ResponseEntity<PermissionResponse> create(@Valid @RequestBody PermissionRequest req) {
        PermissionResponse created = adminPermissionService.create(req);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** PUT /api/admin/permissions/:id — cập nhật */
    @PutMapping("/{id}")
    public ResponseEntity<PermissionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PermissionRequest req) {
        return ResponseEntity.ok(adminPermissionService.update(id, req));
    }

    /** DELETE /api/admin/permissions/:id — xóa */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminPermissionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

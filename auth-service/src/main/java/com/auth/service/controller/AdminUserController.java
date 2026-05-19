package com.auth.service.controller;

import com.auth.service.dto.UserAdminRequest;
import com.auth.service.dto.UserAdminResponse;
import com.auth.service.dto.UserToggleRequest;
import com.auth.service.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Admin API — quản lý Users.
 * Chỉ ROLE_ADMIN mới được truy cập.
 */
@RestController
@RequestMapping("/api/auth/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** GET /api/admin/users — lấy toàn bộ danh sách */
    @GetMapping
    public ResponseEntity<List<UserAdminResponse>> getAll() {
        return ResponseEntity.ok(adminUserService.findAll());
    }

    /** GET /api/admin/users/:id */
    @GetMapping("/{id}")
    public ResponseEntity<UserAdminResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.findById(id));
    }

    /** POST /api/admin/users — tạo user mới */
    @PostMapping
    public ResponseEntity<UserAdminResponse> create(@Valid @RequestBody UserAdminRequest req) {
        UserAdminResponse created = adminUserService.create(req);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** PUT /api/admin/users/:id — cập nhật thông tin */
    @PutMapping("/{id}")
    public ResponseEntity<UserAdminResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UserAdminRequest req) {
        return ResponseEntity.ok(adminUserService.update(id, req));
    }

    /** PATCH /api/admin/users/:id/toggle — bật/tắt tài khoản */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<UserAdminResponse> toggle(
            @PathVariable Long id,
            @RequestBody UserToggleRequest req) {
        return ResponseEntity.ok(adminUserService.toggleEnabled(id, req.enabled()));
    }

    /** DELETE /api/admin/users/:id — xóa user */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminUserService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

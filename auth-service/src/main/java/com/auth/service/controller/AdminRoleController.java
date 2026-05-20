package com.auth.service.controller;

import com.auth.service.dto.RoleRequest;
import com.auth.service.dto.RoleResponse;
import com.auth.service.service.AdminRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    @GetMapping
    public ResponseEntity<List<RoleResponse>> getAll() {
        return ResponseEntity.ok(adminRoleService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(adminRoleService.findById(id));
    }

    @PostMapping
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest req) {
        RoleResponse created = adminRoleService.create(req);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody RoleRequest req) {
        return ResponseEntity.ok(adminRoleService.update(id, req));
    }

    /**
     * PATCH /api/admin/roles/:id/permissions
     * Body: { "permissionIds": [1, 2, 3] }
     * Dùng để gán/cập nhật permissions cho role mà không cần gửi toàn bộ RoleRequest.
     */
    @PatchMapping("/{id}/permissions")
    public ResponseEntity<RoleResponse> assignPermissions(
            @PathVariable Long id,
            @RequestBody Map<String, Set<Long>> body) {
        Set<Long> permissionIds = body.getOrDefault("permissionIds", Set.of());
        return ResponseEntity.ok(adminRoleService.assignPermissions(id, permissionIds));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminRoleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

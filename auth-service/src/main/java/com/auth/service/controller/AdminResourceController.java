package com.auth.service.controller;

import com.auth.service.dto.ResourceRequest;
import com.auth.service.dto.ResourceResponse;
import com.auth.service.service.AdminResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/admin/resources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminResourceController {

    private final AdminResourceService adminResourceService;

    // ─── Resources ────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<ResourceResponse>> getAllResources() {
        return ResponseEntity.ok(adminResourceService.findAllResources());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResourceResponse> getResourceById(@PathVariable Long id) {
        return ResponseEntity.ok(adminResourceService.findResourceById(id));
    }

    @PostMapping
    public ResponseEntity<ResourceResponse> createResource(
            @Valid @RequestBody ResourceRequest req) {
        ResourceResponse created = adminResourceService.createResource(req);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResourceResponse> updateResource(
            @PathVariable Long id,
            @Valid @RequestBody ResourceRequest req) {
        return ResponseEntity.ok(adminResourceService.updateResource(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResource(@PathVariable Long id) {
        adminResourceService.deleteResource(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Actions (sub-resource) ────────────────────────────────────────────────
    // Gộp actions vào cùng controller — admin quản lý cả hai từ trang Resources

    @GetMapping("/actions")
    public ResponseEntity<List<ResourceResponse.ActionDto>> getAllActions() {
        return ResponseEntity.ok(adminResourceService.findAllActions());
    }

    @PostMapping("/actions")
    public ResponseEntity<ResourceResponse.ActionDto> createAction(
            @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        ResourceResponse.ActionDto created = adminResourceService.createAction(name);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/admin/resources/actions/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/actions/{id}")
    public ResponseEntity<ResourceResponse.ActionDto> updateAction(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        return ResponseEntity.ok(adminResourceService.updateAction(id, name));
    }

    @DeleteMapping("/actions/{id}")
    public ResponseEntity<Void> deleteAction(@PathVariable Long id) {
        adminResourceService.deleteAction(id);
        return ResponseEntity.noContent().build();
    }
}

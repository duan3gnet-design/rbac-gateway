package com.auth.service.controller;

import com.auth.service.dto.UserSummary;
import com.auth.service.service.InternalUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal API — chỉ dành cho service-to-service communication.
 * Xác thực được xử lý bởi GatewayAuthFilter:
 *   X-Internal-Secret đúng → principal "system" với ROLE_INTERNAL.
 * Không expose ra ngoài internet (giới hạn ở Docker internal network).
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
public class InternalUserController {

    private final InternalUserService internalUserService;

    @GetMapping
    public ResponseEntity<List<UserSummary>> getAll() {
        return ResponseEntity.ok(internalUserService.findAll());
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserSummary> getByUsername(@PathVariable String username) {
        return internalUserService.findByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            internalUserService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

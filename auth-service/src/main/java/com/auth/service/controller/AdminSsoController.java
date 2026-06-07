package com.auth.service.controller;

import com.auth.service.dto.SsoProviderRequest;
import com.auth.service.dto.SsoProviderResponse;
import com.auth.service.service.SsoProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin endpoints for managing SSO providers.
 * Requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/auth/admin/sso")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminSsoController {

    private final SsoProviderService ssoProviderService;

    @GetMapping
    public ResponseEntity<List<SsoProviderResponse>> listProviders() {
        return ResponseEntity.ok(ssoProviderService.listEnabledProviders());
    }

    @PostMapping
    public ResponseEntity<SsoProviderResponse> createProvider(
            @Valid @RequestBody SsoProviderRequest req) {
        return ResponseEntity.status(201).body(ssoProviderService.createProvider(req));
    }

    @PutMapping("/{providerId}")
    public ResponseEntity<SsoProviderResponse> updateProvider(
            @PathVariable String providerId,
            @Valid @RequestBody SsoProviderRequest req) {
        return ResponseEntity.ok(ssoProviderService.updateProvider(providerId, req));
    }

    @DeleteMapping("/{providerId}")
    public ResponseEntity<Void> deleteProvider(@PathVariable String providerId) {
        ssoProviderService.deleteProvider(providerId);
        return ResponseEntity.noContent().build();
    }
}

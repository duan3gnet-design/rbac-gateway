package com.auth.service.controller;

import com.auth.service.dto.SsoProviderResponse;
import com.auth.service.service.SsoProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public endpoint — returns list of enabled SSO providers so the frontend
 * can render "Login with Okta / Azure AD / ..." buttons dynamically.
 */
@RestController
@RequestMapping("/api/auth/sso")
@RequiredArgsConstructor
public class SsoController {

    private final SsoProviderService ssoProviderService;

    @GetMapping("/providers")
    public ResponseEntity<List<SsoProviderResponse>> getProviders() {
        return ResponseEntity.ok(ssoProviderService.listEnabledProviders());
    }
}

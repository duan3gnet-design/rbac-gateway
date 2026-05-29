package com.auth.service.oidc;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the OIDC / OAuth2 discovery document.
 * <p>
 * Standard path:  GET /.well-known/openid-configuration
 * OAuth2 variant: GET /.well-known/oauth-authorization-server
 * <p>
 * Kubernetes API server (--oidc-issuer-url) fetches this endpoint at startup
 * to discover the JWKS URI and other provider metadata.
 */
@RestController
@RequiredArgsConstructor
public class OidcDiscoveryController {

    private final OidcProperties oidcProperties;

    @GetMapping("/.well-known/openid-configuration")
    public OidcDiscoveryDocument openidConfiguration() {
        return OidcDiscoveryDocument.of(oidcProperties.getIssuerUri());
    }

    /** Alias required by some OAuth2 clients and the k8s TokenReview webhook. */
    @GetMapping("/.well-known/oauth-authorization-server")
    public OidcDiscoveryDocument oauthAuthorizationServer() {
        return openidConfiguration();
    }
}

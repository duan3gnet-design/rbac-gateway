package com.auth.service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SSO Identity Provider configuration.
 * Supports OIDC-based external IdP (Okta, Azure AD, Keycloak, etc.)
 * that redirect back to our auth-service for user provisioning.
 */
@Entity
@Table(name = "sso_providers")
@Getter
@Setter
@NoArgsConstructor
public class SsoProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique identifier, e.g. "okta", "azure-ad", "keycloak" */
    @Column(name = "provider_id", nullable = false, unique = true)
    private String providerId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** OIDC: "oidc". Extendable to "saml" in future. */
    @Column(name = "type", nullable = false)
    private String type = "oidc";

    /** OIDC Issuer URI used to fetch .well-known/openid-configuration */
    @Column(name = "issuer_uri")
    private String issuerUri;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_secret")
    private String clientSecret;

    /** Comma-separated scopes, e.g. "openid,profile,email" */
    @Column(name = "scopes")
    private String scopes = "openid,profile,email";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * Roles to auto-assign when a new user is provisioned via this IdP.
     * Format: comma-separated role names, e.g. "ROLE_USER"
     */
    @Column(name = "default_roles")
    private String defaultRoles = "ROLE_USER";
}

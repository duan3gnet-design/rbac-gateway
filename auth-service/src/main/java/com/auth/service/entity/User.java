package com.auth.service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column
    private String email;

    @Column
    private String fullName;

    @Column
    private String provider;

    /** For SSO users: the provider_id from sso_providers table */
    @Column(name = "sso_provider_id")
    private String ssoProviderId;

    /** Subject identifier from external IdP (used to match returning SSO users) */
    @Column(name = "sso_subject")
    private String ssoSubject;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Whether MFA has been set up and enabled for this user */
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();
}

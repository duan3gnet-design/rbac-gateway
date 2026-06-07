package com.auth.service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Stores the TOTP secret for a user's MFA configuration.
 * One-to-one relationship with User.
 */
@Entity
@Table(name = "mfa_secrets")
@Getter
@Setter
@NoArgsConstructor
public class MfaSecret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Base32-encoded TOTP secret (stored encrypted in production via @ColumnTransformer
     * or application-level encryption — add pgcrypto if needed).
     */
    @Column(name = "secret", nullable = false)
    private String secret;

    /** Whether the user has confirmed their first TOTP code (i.e., setup complete). */
    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    /** Whether MFA is currently active for this user. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}

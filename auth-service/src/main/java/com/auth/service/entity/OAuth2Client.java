package com.auth.service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * OAuth2 machine client – used for client_credentials grant.
 *
 * Stored in the oauth2_clients table, separate from human users.
 * client_id is used as the "username" in Spring Security's
 * UserDetailsService to reuse the existing DaoAuthenticationProvider.
 */
@Entity
@Table(name = "oauth2_clients")
@Data
@NoArgsConstructor
public class OAuth2Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", unique = true, nullable = false)
    private String clientId;

    @Column(name = "client_secret", nullable = false)
    private String clientSecret;   // BCrypt hash

    @Column(nullable = false)
    private String scopes = "openid";

    @Column(name = "granted_types", nullable = false)
    private String grantedTypes = "client_credentials";

    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    private Instant createdAt;
}

package com.auth.service.service;

import com.auth.service.dto.RegisterRequest;
import com.auth.service.entity.Role;
import com.auth.service.entity.SsoProvider;
import com.auth.service.entity.User;
import com.auth.service.repository.RoleRepository;
import com.auth.service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository  userRepository;
    private final RoleRepository  roleRepository;
    private final PasswordEncoder passwordEncoder;

    public void register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Username already exists");
        }

        Set<Role> roles = req.roles().stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName)))
                .collect(Collectors.toSet());

        User user = new User();
        user.setUsername(req.username());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRoles(roles);

        userRepository.save(user);
    }

    /** Used by Google OAuth2 (redirect flow) and SSO providers. */
    @Transactional
    public UserDetails findOrCreateOAuth2User(String email, String name) {
        return findOrCreateOAuth2User(email, name, "GOOGLE", null, null);
    }

    /**
     * Finds or creates a user originating from an external SSO/OAuth2 provider.
     *
     * @param email       user's email (used as username)
     * @param name        full name
     * @param providerTag e.g. "GOOGLE", "OKTA", "AZURE"
     * @param ssoProvider optional SsoProvider entity (for dynamic SSO configs)
     * @param subject     the IdP's subject identifier (for stable matching)
     */
    @Transactional
    public UserDetails findOrCreateOAuth2User(
            String email,
            String name,
            String providerTag,
            SsoProvider ssoProvider,
            String subject) {

        // Try to find by SSO subject first (more reliable than email across providers)
        User user = null;
        if (subject != null && ssoProvider != null) {
            user = userRepository
                    .findBySsoProviderIdAndSsoSubject(ssoProvider.getProviderId(), subject)
                    .orElse(null);
        }

        // Fallback to email lookup
        if (user == null) {
            user = userRepository.findByUsername(email).orElse(null);
        }

        // Provision new user
        if (user == null) {
            user = new User();
            user.setUsername(email);
            user.setPassword("");
            user.setFullName(name);
            user.setProvider(providerTag);
            user.setEmail(email);

            if (ssoProvider != null) {
                user.setSsoProviderId(ssoProvider.getProviderId());
                user.setSsoSubject(subject);
                Set<Role> roles = resolveRoles(ssoProvider.getDefaultRoles());
                user.setRoles(roles);
            } else {
                Role userRole = roleRepository.findByName("ROLE_USER")
                        .orElseThrow(() -> new RuntimeException("ROLE_USER không tồn tại"));
                user.setRoles(Set.of(userRole));
            }

            user = userRepository.save(user);
        } else {
            // Update subject if not set yet (migration for existing users)
            if (subject != null && user.getSsoSubject() == null) {
                user.setSsoSubject(subject);
                user.setSsoProviderId(ssoProvider != null ? ssoProvider.getProviderId() : providerTag);
                userRepository.save(user);
            }
        }

        final User finalUser = user;
        return org.springframework.security.core.userdetails.User
                .withUsername(finalUser.getUsername())
                .password(finalUser.getPassword())
                .authorities(finalUser.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority(role.getName()))
                        .collect(Collectors.toSet()))
                .build();
    }

    /** Returns true if the user exists and has MFA enabled. */
    public boolean isMfaEnabled(String username) {
        return userRepository.findByUsername(username)
                .map(User::isMfaEnabled)
                .orElse(false);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Set<Role> resolveRoles(String defaultRoles) {
        if (defaultRoles == null || defaultRoles.isBlank()) {
            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new RuntimeException("ROLE_USER không tồn tại"));
            return Set.of(userRole);
        }
        return Arrays.stream(defaultRoles.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(name -> roleRepository.findByName(name)
                        .orElseGet(() -> {
                            Role r = new Role();
                            r.setName(name);
                            return roleRepository.save(r);
                        }))
                .collect(Collectors.toSet());
    }
}

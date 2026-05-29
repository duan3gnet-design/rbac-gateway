package com.auth.service.service;

import com.auth.service.repository.OAuth2ClientRepository;
import com.auth.service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * UserDetailsService that resolves both human users (users table)
 * and OAuth2 machine clients (oauth2_clients table).
 *
 * Lookup order:
 *   1. users table        – human users, Google OAuth2 users
 *   2. oauth2_clients     – machine clients for client_credentials grant
 *
 * This allows DaoAuthenticationProvider to authenticate both kinds of
 * principal without any changes to the authentication pipeline.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository          userRepository;
    private final OAuth2ClientRepository  clientRepository;

    @Override
    public UserDetails loadUserByUsername(@NonNull String username)
            throws UsernameNotFoundException {

        // ── 1. Try human user ────────────────────────────────────────────────
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            var user = userOpt.get();
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPassword())
                    .authorities(user.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority(role.getName()))
                            .collect(Collectors.toSet()))
                    .disabled(!user.isEnabled())
                    .build();
        }

        // ── 2. Try OAuth2 machine client ─────────────────────────────────────
        var clientOpt = clientRepository.findByClientId(username);
        if (clientOpt.isPresent()) {
            var client = clientOpt.get();
            return org.springframework.security.core.userdetails.User
                    .withUsername(client.getClientId())
                    .password(client.getClientSecret())
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_SERVICE")))
                    .disabled(!client.isEnabled())
                    .build();
        }

        throw new UsernameNotFoundException("Principal not found: " + username);
    }
}

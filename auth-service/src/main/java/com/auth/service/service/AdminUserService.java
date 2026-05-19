package com.auth.service.service;

import com.auth.service.dto.UserAdminRequest;
import com.auth.service.dto.UserAdminResponse;
import com.auth.service.entity.Role;
import com.auth.service.entity.User;
import com.auth.service.repository.RefreshTokenRepository;
import com.auth.service.repository.RoleRepository;
import com.auth.service.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository         userRepository;
    private final RoleRepository         roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder        passwordEncoder;

    // ─── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserAdminResponse> findAll() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserAdminResponse findById(Long id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public UserAdminResponse create(UserAdminRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Username already exists: " + req.username());
        }
        if (!StringUtils.hasText(req.password())) {
            throw new IllegalArgumentException("Password is required when creating a user");
        }

        Set<Role> roles = resolveRoles(req.roles());

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setFullName(req.fullName());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRoles(roles);
        user.setEnabled(req.enabled());
        user.setProvider("LOCAL");

        User saved = userRepository.save(user);
        log.info("[Admin] Created user id={} username={}", saved.getId(), saved.getUsername());
        return toResponse(saved);
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public UserAdminResponse update(Long id, UserAdminRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));

        // Username change — check duplicate (excluding self)
        if (!user.getUsername().equals(req.username())
                && userRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Username already taken: " + req.username());
        }

        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setFullName(req.fullName());
        user.setEnabled(req.enabled());
        user.setRoles(resolveRoles(req.roles()));

        // Chỉ đổi password khi client gửi giá trị mới
        if (StringUtils.hasText(req.password())) {
            user.setPassword(passwordEncoder.encode(req.password()));
        }

        User saved = userRepository.save(user);
        log.info("[Admin] Updated user id={}", id);
        return toResponse(saved);
    }

    // ─── Toggle enabled ───────────────────────────────────────────────────────

    @Transactional
    public UserAdminResponse toggleEnabled(Long id, boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));

        user.setEnabled(enabled);
        User saved = userRepository.save(user);

        // Vô hiệu hóa → revoke tất cả refresh tokens để force logout
        if (!enabled) {
            int revoked = refreshTokenRepository.revokeAllByUsername(user.getUsername());
            log.info("[Admin] Disabled user id={} — revoked {} refresh tokens", id, revoked);
        }

        log.info("[Admin] Toggled user id={} enabled={}", id, enabled);
        return toResponse(saved);
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));

        // Xóa refresh tokens trước để không vi phạm FK (nếu có)
        refreshTokenRepository.deleteAllByUsername(user.getUsername());
        userRepository.delete(user);
        log.info("[Admin] Deleted user id={} username={}", id, user.getUsername());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Set<Role> resolveRoles(Set<String> roleNames) {
        return roleNames.stream()
                .map(name -> {
                    // Normalize: ADMIN → ROLE_ADMIN
                    String normalized = name.toUpperCase().startsWith("ROLE_")
                            ? name.toUpperCase() : "ROLE_" + name.toUpperCase();
                    return roleRepository.findByName(normalized)
                            .orElseGet(() -> {
                                Role r = new Role();
                                r.setName(normalized);
                                log.info("[Admin] Auto-created role '{}'", normalized);
                                return roleRepository.save(r);
                            });
                })
                .collect(Collectors.toSet());
    }

    private UserAdminResponse toResponse(User u) {
        Set<String> roles = u.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return new UserAdminResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getFullName(),
                u.getProvider(),
                u.isEnabled(),
                roles
        );
    }
}

package com.auth.service.service;

import com.auth.service.dto.UserSummary;
import com.auth.service.entity.Role;
import com.auth.service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InternalUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserSummary> findAll() {
        return userRepository.findAll(PageRequest.of(0, 10)).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserSummary> findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::toSummary);
    }

    @Transactional
    public void deleteById(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }

    // ── mapper ────────────────────────────────────────────────

    private UserSummary toSummary(com.auth.service.entity.User user) {
        var roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getProvider(),
                roles);
    }
}

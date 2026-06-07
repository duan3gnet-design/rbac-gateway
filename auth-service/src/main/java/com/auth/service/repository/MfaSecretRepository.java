package com.auth.service.repository;

import com.auth.service.entity.MfaSecret;
import com.auth.service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MfaSecretRepository extends JpaRepository<MfaSecret, Long> {
    Optional<MfaSecret> findByUser(User user);
    Optional<MfaSecret> findByUserUsername(String username);
    boolean existsByUserAndEnabledTrue(User user);
}

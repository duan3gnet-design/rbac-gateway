package com.auth.service.repository;

import com.auth.service.entity.SsoProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SsoProviderRepository extends JpaRepository<SsoProvider, Long> {
    Optional<SsoProvider> findByProviderIdAndEnabledTrue(String providerId);
    List<SsoProvider> findAllByEnabledTrue();
}

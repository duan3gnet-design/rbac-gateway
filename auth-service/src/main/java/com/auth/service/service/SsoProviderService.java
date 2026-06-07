package com.auth.service.service;

import com.auth.service.dto.SsoProviderRequest;
import com.auth.service.dto.SsoProviderResponse;
import com.auth.service.entity.SsoProvider;
import com.auth.service.repository.SsoProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SsoProviderService {

    private final SsoProviderRepository ssoProviderRepository;

    public List<SsoProviderResponse> listEnabledProviders() {
        return ssoProviderRepository.findAllByEnabledTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    public SsoProvider getProvider(String providerId) {
        return ssoProviderRepository.findByProviderIdAndEnabledTrue(providerId)
                .orElseThrow(() -> new IllegalArgumentException("SSO provider không tìm thấy: " + providerId));
    }

    @Transactional
    public SsoProviderResponse createProvider(SsoProviderRequest req) {
        if (ssoProviderRepository.findByProviderIdAndEnabledTrue(req.providerId()).isPresent()) {
            throw new IllegalArgumentException("Provider đã tồn tại: " + req.providerId());
        }

        SsoProvider provider = new SsoProvider();
        mapRequest(req, provider);
        ssoProviderRepository.save(provider);
        log.info("SSO provider created: {}", req.providerId());
        return toResponse(provider);
    }

    @Transactional
    public SsoProviderResponse updateProvider(String providerId, SsoProviderRequest req) {
        SsoProvider provider = ssoProviderRepository.findByProviderIdAndEnabledTrue(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider không tìm thấy: " + providerId));
        mapRequest(req, provider);
        ssoProviderRepository.save(provider);
        return toResponse(provider);
    }

    @Transactional
    public void deleteProvider(String providerId) {
        SsoProvider provider = ssoProviderRepository.findByProviderIdAndEnabledTrue(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider không tìm thấy: " + providerId));
        provider.setEnabled(false);
        ssoProviderRepository.save(provider);
        log.info("SSO provider disabled: {}", providerId);
    }

    private void mapRequest(SsoProviderRequest req, SsoProvider provider) {
        provider.setProviderId(req.providerId());
        provider.setDisplayName(req.displayName());
        provider.setType(req.type() != null ? req.type() : "oidc");
        provider.setIssuerUri(req.issuerUri());
        provider.setClientId(req.clientId());
        provider.setClientSecret(req.clientSecret());
        provider.setScopes(req.scopes() != null ? req.scopes() : "openid,profile,email");
        provider.setDefaultRoles(req.defaultRoles() != null ? req.defaultRoles() : "ROLE_USER");
        provider.setEnabled(req.enabled());
    }

    private SsoProviderResponse toResponse(SsoProvider p) {
        return new SsoProviderResponse(
                p.getProviderId(),
                p.getDisplayName(),
                p.getType(),
                "/oauth2/authorization/" + p.getProviderId()
        );
    }
}

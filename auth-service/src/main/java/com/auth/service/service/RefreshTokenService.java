package com.auth.service.service;

import com.auth.service.dto.TokenPair;
import com.auth.service.entity.RefreshToken;
import com.auth.service.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.ott.InvalidOneTimeTokenException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Value("${app.jwt.refresh-expiration-days:7}")
    private long refreshExpirationDays;

    public RefreshToken createRefreshToken(String username) {
        RefreshToken token = new RefreshToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUsername(username);
        token.setExpiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS));
        return refreshTokenRepository.save(token);
    }

    @Transactional
    public TokenPair rotate(String rawToken) {
        RefreshToken existing = refreshTokenRepository
                .findByTokenAndRevokedFalse(rawToken)
                .orElseThrow(() -> new InvalidOneTimeTokenException("Refresh token không hợp lệ"));

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
            throw new InvalidOneTimeTokenException("Refresh token đã hết hạn, vui lòng đăng nhập lại");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        UserDetails userDetails = userDetailsService.loadUserByUsername(existing.getUsername());

        String newAccessToken = jwtService.generateToken(userDetails);
        RefreshToken newRefreshToken = createRefreshToken(existing.getUsername());

        return new TokenPair(newAccessToken, newRefreshToken.getToken());
    }

    @Transactional
    public void revokeAllTokens(String username) {
        refreshTokenRepository.revokeAllByUsername(username);
    }

    @Transactional
    public void revokeToken(String rawToken) {
        RefreshToken token = refreshTokenRepository
                .findByTokenAndRevokedFalse(rawToken)
                .orElseThrow(() -> new InvalidOneTimeTokenException("Token không hợp lệ hoặc đã bị thu hồi"));
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }
}
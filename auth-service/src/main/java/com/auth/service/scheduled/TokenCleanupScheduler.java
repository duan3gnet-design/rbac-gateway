package com.auth.service.scheduled;

import com.auth.service.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 3 * * *") // 3AM mỗi ngày
    @Transactional
    public void cleanup() {
        refreshTokenRepository.deleteExpiredAndRevoked(Instant.now());
    }
}
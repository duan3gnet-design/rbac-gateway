package com.auth.service.service;

import com.auth.service.dto.TokenPair;
import com.auth.service.entity.RefreshToken;
import com.auth.service.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.ott.InvalidOneTimeTokenException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationDays", 7L);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private RefreshToken validToken(String rawToken, String username) {
        RefreshToken rt = new RefreshToken();
        rt.setToken(rawToken);
        rt.setUsername(username);
        rt.setRevoked(false);
        rt.setExpiresAt(Instant.now().plusSeconds(3600));
        return rt;
    }

    private RefreshToken expiredToken(String rawToken, String username) {
        RefreshToken rt = new RefreshToken();
        rt.setToken(rawToken);
        rt.setUsername(username);
        rt.setRevoked(false);
        rt.setExpiresAt(Instant.now().minusSeconds(60));
        return rt;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // createRefreshToken
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createRefreshToken")
    class CreateRefreshToken {

        @Test
        @DisplayName("Tạo token với username đúng và chưa revoked")
        void shouldCreateTokenWithCorrectFields() {
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RefreshToken result = refreshTokenService.createRefreshToken("phan@test.com");

            assertThat(result.getUsername()).isEqualTo("phan@test.com");
            assertThat(result.isRevoked()).isFalse();
            assertThat(result.getToken()).isNotBlank();
            assertThat(result.getExpiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("ExpiresAt = bây giờ + 7 ngày")
        void shouldExpireIn7Days() {
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RefreshToken result = refreshTokenService.createRefreshToken("user");

            Instant expectedMin = Instant.now().plusSeconds(6 * 24 * 3600);
            Instant expectedMax = Instant.now().plusSeconds(8 * 24 * 3600);
            assertThat(result.getExpiresAt()).isBetween(expectedMin, expectedMax);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // rotate
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("rotate")
    class Rotate {

        @Test
        @DisplayName("Token hợp lệ → revoke cũ, tạo mới, trả TokenPair")
        void validToken_shouldRotateAndReturnNewPair() {
            RefreshToken existing = validToken("old-token", "phan@test.com");
            when(refreshTokenRepository.findByTokenAndRevokedFalse("old-token"))
                    .thenReturn(Optional.of(existing));

            var userDetails = User.withUsername("phan@test.com")
                    .password("pass")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                    .build();
            when(userDetailsService.loadUserByUsername("phan@test.com")).thenReturn(userDetails);
            when(jwtService.generateToken(userDetails)).thenReturn("new-access-token");

            RefreshToken newRt = new RefreshToken();
            newRt.setToken("new-refresh-token");
            newRt.setUsername("phan@test.com");
            newRt.setExpiresAt(Instant.now().plusSeconds(3600));
            // save được gọi 2 lần: 1 lần revoke cũ, 1 lần save mới
            when(refreshTokenRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0))   // lần 1: revoke
                    .thenReturn(newRt);                       // lần 2: tạo mới

            TokenPair result = refreshTokenService.rotate("old-token");

            assertThat(result.accessToken()).isEqualTo("new-access-token");
            assertThat(result.refreshToken()).isEqualTo("new-refresh-token");

            // Xác nhận token cũ đã bị revoke
            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues().get(0).isRevoked()).isTrue();
        }

        @Test
        @DisplayName("Token không tồn tại hoặc đã revoked → throw InvalidOneTimeTokenException")
        void invalidToken_shouldThrow() {
            when(refreshTokenRepository.findByTokenAndRevokedFalse("bad-token"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.rotate("bad-token"))
                    .isInstanceOf(InvalidOneTimeTokenException.class)
                    .hasMessageContaining("không hợp lệ");
        }

        @Test
        @DisplayName("Token hết hạn → revoke token, throw InvalidOneTimeTokenException")
        void expiredToken_shouldRevokeAndThrow() {
            RefreshToken expired = expiredToken("expired-token", "user");
            when(refreshTokenRepository.findByTokenAndRevokedFalse("expired-token"))
                    .thenReturn(Optional.of(expired));
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
                    .isInstanceOf(InvalidOneTimeTokenException.class)
                    .hasMessageContaining("hết hạn");

            // Token phải được mark revoked
            assertThat(expired.isRevoked()).isTrue();
            verify(refreshTokenRepository).save(expired);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // revokeToken
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("revokeToken")
    class RevokeToken {

        @Test
        @DisplayName("Token hợp lệ → set revoked = true, save")
        void validToken_shouldSetRevokedTrue() {
            RefreshToken rt = validToken("valid-token", "user");
            when(refreshTokenRepository.findByTokenAndRevokedFalse("valid-token"))
                    .thenReturn(Optional.of(rt));
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            refreshTokenService.revokeToken("valid-token");

            assertThat(rt.isRevoked()).isTrue();
            verify(refreshTokenRepository).save(rt);
        }

        @Test
        @DisplayName("Token không tồn tại → throw InvalidOneTimeTokenException")
        void invalidToken_shouldThrow() {
            when(refreshTokenRepository.findByTokenAndRevokedFalse("ghost-token"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.revokeToken("ghost-token"))
                    .isInstanceOf(InvalidOneTimeTokenException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // revokeAllTokens
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("revokeAllTokens")
    class RevokeAllTokens {

        @Test
        @DisplayName("Gọi revokeAllByUsername đúng username")
        void shouldCallRepositoryWithUsername() {
            refreshTokenService.revokeAllTokens("phan@test.com");

            verify(refreshTokenRepository).revokeAllByUsername("phan@test.com");
        }
    }
}

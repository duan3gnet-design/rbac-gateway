package com.auth.service.service;

import com.auth.service.dto.MfaBackupCodesResponse;
import com.auth.service.dto.MfaSetupResponse;
import com.auth.service.entity.MfaBackupCode;
import com.auth.service.entity.MfaSecret;
import com.auth.service.entity.User;
import com.auth.service.repository.MfaBackupCodeRepository;
import com.auth.service.repository.MfaSecretRepository;
import com.auth.service.repository.UserRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * MFA service: TOTP setup, verification, backup codes, and MFA session management.
 *
 * <p>Flow:
 * <pre>
 * 1. POST /api/mfa/setup        → returns QR code + secret
 * 2. POST /api/mfa/enable       → user submits first TOTP code → MFA activated, backup codes returned
 * 3. POST /api/auth/login       → if mfa_enabled: returns mfa_session_token (MFA_PENDING in Redis)
 * 4. POST /api/auth/mfa/verify  → user submits TOTP or backup code → full JWT issued
 * 5. POST /api/mfa/disable      → user submits TOTP to disable MFA
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    private static final String MFA_SESSION_PREFIX  = "mfa:session:";
    private static final int    BACKUP_CODE_COUNT    = 10;
    private static final int    BACKUP_CODE_LENGTH   = 8;
    private static final Duration MFA_SESSION_TTL   = Duration.ofMinutes(5);

    private final MfaSecretRepository      mfaSecretRepository;
    private final MfaBackupCodeRepository  mfaBackupCodeRepository;
    private final UserRepository           userRepository;
    private final PasswordEncoder          passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${mfa.issuer:RbacGateway}")
    private String issuerName;

    // ── Setup ──────────────────────────────────────────────────────────────────

    /**
     * Generates a new TOTP secret for the user and returns setup info.
     * Does NOT enable MFA yet — user must confirm with a valid code.
     */
    @Transactional
    public MfaSetupResponse initSetup(String username) {
        User user = getUser(username);

        // Generate or reuse pending secret
        MfaSecret mfaSecret = mfaSecretRepository.findByUser(user)
                .orElseGet(() -> {
                    MfaSecret s = new MfaSecret();
                    s.setUser(user);
                    return s;
                });

        SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
        String secret = secretGenerator.generate();
        mfaSecret.setSecret(secret);
        mfaSecret.setVerified(false);
        mfaSecret.setEnabled(false);
        mfaSecretRepository.save(mfaSecret);

        String otpauthUri = buildOtpauthUri(username, secret);
        String qrCodeDataUrl = generateQrCodeDataUrl(otpauthUri);

        return new MfaSetupResponse(secret, otpauthUri, qrCodeDataUrl);
    }

    /**
     * Confirms setup by verifying the user's first TOTP code.
     * Enables MFA and generates backup codes.
     */
    @Transactional
    public MfaBackupCodesResponse enableMfa(String username, String totpCode) {
        User user = getUser(username);

        MfaSecret mfaSecret = mfaSecretRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("MFA setup chưa được khởi tạo. Gọi /api/mfa/setup trước."));

        if (!verifyTotpCode(mfaSecret.getSecret(), totpCode)) {
            throw new IllegalArgumentException("Mã TOTP không hợp lệ");
        }

        mfaSecret.setVerified(true);
        mfaSecret.setEnabled(true);
        mfaSecretRepository.save(mfaSecret);

        user.setMfaEnabled(true);
        userRepository.save(user);

        List<String> backupCodes = generateAndSaveBackupCodes(user);
        log.info("MFA đã được bật cho user: {}", username);

        return new MfaBackupCodesResponse(backupCodes,
                "MFA đã được kích hoạt. Lưu các backup codes ở nơi an toàn!");
    }

    /**
     * Disables MFA. Requires a valid TOTP code for confirmation.
     */
    @Transactional
    public void disableMfa(String username, String totpCode) {
        User user = getUser(username);

        MfaSecret mfaSecret = mfaSecretRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("MFA chưa được cấu hình"));

        if (!verifyTotpCode(mfaSecret.getSecret(), totpCode)) {
            throw new IllegalArgumentException("Mã TOTP không hợp lệ");
        }

        mfaSecret.setEnabled(false);
        mfaSecret.setVerified(false);
        mfaSecretRepository.save(mfaSecret);

        mfaBackupCodeRepository.deleteAllByUser(user);

        user.setMfaEnabled(false);
        userRepository.save(user);

        log.info("MFA đã được tắt cho user: {}", username);
    }

    /**
     * Regenerates backup codes. Requires a valid TOTP code.
     * All existing unused backup codes are invalidated.
     */
    @Transactional
    public MfaBackupCodesResponse regenerateBackupCodes(String username, String totpCode) {
        User user = getUser(username);

        MfaSecret mfaSecret = mfaSecretRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("MFA chưa được cấu hình"));

        if (!mfaSecret.isEnabled()) {
            throw new IllegalStateException("MFA chưa được bật");
        }

        if (!verifyTotpCode(mfaSecret.getSecret(), totpCode)) {
            throw new IllegalArgumentException("Mã TOTP không hợp lệ");
        }

        mfaBackupCodeRepository.deleteAllByUser(user);
        List<String> newCodes = generateAndSaveBackupCodes(user);

        return new MfaBackupCodesResponse(newCodes,
                "Backup codes mới đã được tạo. Các codes cũ đã bị vô hiệu hóa.");
    }

    // ── MFA Session (Redis) ────────────────────────────────────────────────────

    /**
     * Creates a short-lived MFA session in Redis.
     * Called after successful password auth when user has MFA enabled.
     *
     * @return mfaSessionToken to return to client
     */
    public String createMfaSession(String username) {
        String sessionToken = UUID.randomUUID().toString();
        String key = MFA_SESSION_PREFIX + sessionToken;
        redisTemplate.opsForValue().set(key, username, MFA_SESSION_TTL);
        log.debug("MFA session created for {} (TTL={})", username, MFA_SESSION_TTL);
        return sessionToken;
    }

    /**
     * Resolves and consumes a MFA session token.
     * Returns the username if valid, throws otherwise.
     */
    public String consumeMfaSession(String sessionToken) {
        String key = MFA_SESSION_PREFIX + sessionToken;
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            throw new IllegalArgumentException("MFA session token không hợp lệ hoặc đã hết hạn");
        }
        redisTemplate.delete(key);
        return value.toString();
    }

    // ── Verification ──────────────────────────────────────────────────────────

    /**
     * Verifies a TOTP code or backup code for the given user.
     */
    @Transactional
    public boolean verifyMfaCode(String username, String code) {
        User user = getUser(username);

        MfaSecret mfaSecret = mfaSecretRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("MFA chưa được cấu hình"));

        if (!mfaSecret.isEnabled()) {
            throw new IllegalStateException("MFA chưa được bật");
        }

        // Try TOTP first
        if (code.length() == 6 && verifyTotpCode(mfaSecret.getSecret(), code)) {
            return true;
        }

        // Try backup code
        return verifyBackupCode(user, code);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean verifyTotpCode(String secret, String code) {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
        CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        return verifier.isValidCode(secret, code);
    }

    private boolean verifyBackupCode(User user, String rawCode) {
        List<MfaBackupCode> unusedCodes = mfaBackupCodeRepository.findByUserAndUsedFalse(user);
        for (MfaBackupCode backupCode : unusedCodes) {
            if (passwordEncoder.matches(rawCode, backupCode.getCodeHash())) {
                backupCode.setUsed(true);
                backupCode.setUsedAt(Instant.now());
                mfaBackupCodeRepository.save(backupCode);
                log.info("Backup code used by user: {}", user.getUsername());
                return true;
            }
        }
        return false;
    }

    private List<String> generateAndSaveBackupCodes(User user) {
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Remove ambiguous chars
        List<String> plainCodes = new ArrayList<>();

        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(BACKUP_CODE_LENGTH);
            for (int j = 0; j < BACKUP_CODE_LENGTH; j++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            String code = sb.toString();
            plainCodes.add(code);

            MfaBackupCode backupCode = new MfaBackupCode();
            backupCode.setUser(user);
            backupCode.setCodeHash(passwordEncoder.encode(code));
            mfaBackupCodeRepository.save(backupCode);
        }

        return plainCodes;
    }

    private String buildOtpauthUri(String username, String secret) {
        String encodedIssuer  = URLEncoder.encode(issuerName, StandardCharsets.UTF_8);
        String encodedAccount = URLEncoder.encode(username, StandardCharsets.UTF_8);
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                encodedIssuer, encodedAccount, secret, encodedIssuer);
    }

    private String generateQrCodeDataUrl(String otpauthUri) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(otpauthUri, BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (Exception e) {
            log.warn("Không thể tạo QR code image: {}", e.getMessage());
            return "";
        }
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user: " + username));
    }
}

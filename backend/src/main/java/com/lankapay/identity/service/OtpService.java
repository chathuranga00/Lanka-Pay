package com.lankapay.identity.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.OtpException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.common.util.PhoneNumberNormalizer;
import com.lankapay.identity.domain.OtpMetadataRecord;
import com.lankapay.identity.domain.OtpPurpose;
import com.lankapay.identity.domain.OtpRecord;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.identity.otp.OtpProvider;
import com.lankapay.notification.domain.NotificationChannelType;
import com.lankapay.notification.domain.NotificationRecord;
import com.lankapay.notification.domain.NotificationStore;
import com.lankapay.notification.domain.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Service for OTP generation, delivery, and verification.
 *
 * Security rules enforced here:
 * - Plaintext OTP is NEVER stored in the database.
 * - Plaintext OTP is NEVER logged.
 * - Phone numbers are only logged in masked form.
 * - Resend cooldown prevents flood attacks (default: 60 s between requests).
 * - Daily cap prevents SMS-toll-fraud abuse (default: 10 per day).
 * - Replay protection: once an OTP is used or expires, it cannot be reused.
 * - Brute-force protection: failed attempts lock the account after max attempts.
 * - Every outbound OTP creates a Notification event in PENDING/SENT status,
 *   so the notification audit trail is complete even before a real gateway exists.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_BCRYPT_COST = 10;

    private final UserStore              userStore;
    private final NotificationStore      notificationStore;
    private final OtpProvider            otpProvider;
    private final AuditService           auditService;
    private final BCryptPasswordEncoder  otpEncoder;

    private final long otpExpirySeconds;
    private final int  maxAttempts;
    private final long resendCooldownSeconds;
    private final int  dailyOtpCap;

    public OtpService(
            UserStore userStore,
            NotificationStore notificationStore,
            OtpProvider otpProvider,
            AuditService auditService,
            @Value("${app.otp.expiry-seconds:300}")          long otpExpirySeconds,
            @Value("${app.otp.max-attempts:5}")              int  maxAttempts,
            @Value("${app.otp.resend-cooldown-seconds:60}")  long resendCooldownSeconds,
            @Value("${app.otp.daily-cap:10}")                int  dailyOtpCap) {

        this.userStore            = userStore;
        this.notificationStore    = notificationStore;
        this.otpProvider          = otpProvider;
        this.auditService         = auditService;
        this.otpEncoder           = new BCryptPasswordEncoder(OTP_BCRYPT_COST);
        this.otpExpirySeconds     = otpExpirySeconds;
        this.maxAttempts          = maxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.dailyOtpCap          = dailyOtpCap;
    }

    // ── Generate & send ────────────────────────────────────────

    /**
     * Generate a 6-digit OTP, hash it, persist it, enforce rate limits,
     * deliver via {@link OtpProvider}, and record a notification event.
     */
    @Transactional
    public Instant generateAndSend(User user, OtpPurpose purpose) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        OtpMetadataRecord meta = userStore.findOtpMetadata(user.getId(), purpose)
                .orElse(new OtpMetadataRecord(user.getId(), purpose));

        if (meta.isInCooldown()) {
            long secondsLeft = ChronoUnit.SECONDS.between(
                    Instant.now(), meta.resendCooldownUntil());
            auditService.log(user.getId(), "OTP_RESEND_BLOCKED", "USER",
                    user.getId(),
                    "{\"reason\":\"cooldown\",\"secondsLeft\":" + secondsLeft + "}",
                    null);
            throw new ValidationException("OTP_RESEND_TOO_SOON",
                    "Please wait " + secondsLeft + " second(s) before requesting a new OTP");
        }

        OtpMetadataRecord updatedMeta = meta.incrementDailyCount(today);
        if (updatedMeta.dailyOtpCount() > dailyOtpCap) {
            auditService.log(user.getId(), "OTP_DAILY_CAP_REACHED", "USER",
                    user.getId(), "{\"purpose\":\"" + purpose + "\"}", null);
            throw new ValidationException("OTP_DAILY_CAP_REACHED",
                    "Daily OTP limit reached. Please try again tomorrow.");
        }

        Instant cooldownUntil = Instant.now().plusSeconds(resendCooldownSeconds);
        userStore.saveOtpMetadata(updatedMeta.withCooldownUntil(cooldownUntil));

        // Generate, hash, persist OTP
        String plainOtp   = generateSixDigit();
        String otpHash    = otpEncoder.encode(plainOtp);
        Instant expiresAt = Instant.now().plus(otpExpirySeconds, ChronoUnit.SECONDS);

        userStore.saveOtp(new OtpRecord(user.getId(), otpHash, purpose, expiresAt));

        // Deliver via OtpProvider
        String maskedPhone = PhoneNumberNormalizer.mask(user.getPhone());
        long expiryMinutes = otpExpirySeconds / 60;
        NotificationRecord notif = new NotificationRecord(
                user.getId(), NotificationChannelType.SMS, NotificationType.OTP,
                "One-Time Password", buildOtpMessage(purpose, expiresAt)
        );

        try {
            otpProvider.send(user.getPhone(), maskedPhone, plainOtp, purpose, expiryMinutes);
            if (notificationStore != null) {
                notificationStore.saveNotification(notif.markSent());
            }
        } catch (Exception ex) {
            if (notificationStore != null) {
                notificationStore.saveNotification(notif.markFailed(ex.getMessage()));
            }
            log.error("OTP delivery failed for maskedPhone={} purpose={}: {}",
                    maskedPhone, purpose, ex.getMessage());
            throw ex;
        }

        auditService.log(user.getId(), "OTP_SENT", "USER",
                user.getId(), "{\"purpose\":\"" + purpose + "\"}", null);
        log.info("OTP sent [purpose={} maskedPhone={}]", purpose, maskedPhone);
        return expiresAt;
    }

    // ── Verify ─────────────────────────────────────────────────

    /**
     * Verify an OTP submitted by the user.
     */
    @Transactional
    public void verify(User user, OtpPurpose purpose, String plainOtp) {
        OtpRecord latest = userStore
                .findLatestValidOtp(user.getId(), purpose, Instant.now())
                .orElseThrow(OtpException::expired);

        try {
            userStore.verifyAndConsumeOtp(latest.id(), plainOtp, (p, h) -> otpEncoder.matches(p, h), maxAttempts);
        } catch (OtpException ex) {
            if ("OTP_MAX_ATTEMPTS".equals(ex.getErrorCode())) {
                user.lockUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
                userStore.incrementFailedLoginAttempts(user.getId(), maxAttempts, Instant.now().plus(15, ChronoUnit.MINUTES));
                auditService.log(user.getId(), "OTP_MAX_ATTEMPTS", "USER",
                        user.getId(), "{\"purpose\":\"" + purpose + "\"}", null);
                log.warn("OTP max attempts reached, locking userId={}", user.getId());
            } else if ("OTP_INVALID".equals(ex.getErrorCode())) {
                int attempts = user.incrementFailedAttempts();
                userStore.incrementFailedLoginAttempts(user.getId(), maxAttempts, null);
                auditService.log(user.getId(), "OTP_INVALID_ATTEMPT", "USER",
                        user.getId(),
                        "{\"purpose\":\"" + purpose + "\",\"attempt\":" + attempts + "}",
                        null);
            }
            throw ex;
        }

        // Success — reset attempts
        user.resetLoginAttempts();
        userStore.resetFailedLoginAttempts(user.getId());

        auditService.log(user.getId(), "OTP_VERIFIED", "USER",
                user.getId(), "{\"purpose\":\"" + purpose + "\"}", null);
    }

    private String generateSixDigit() {
        int code = new SecureRandom().nextInt(900_000) + 100_000; // 100000–999999
        return String.valueOf(code);
    }

    private String buildOtpMessage(OtpPurpose purpose, Instant expiresAt) {
        long minutesLeft = ChronoUnit.MINUTES.between(Instant.now(), expiresAt);
        return "Lanka Pay OTP for %s. Valid for %d minute(s). [SIMULATED]"
                .formatted(purpose.name().toLowerCase().replace('_', ' '), minutesLeft);
    }
}

package com.lankapay.identity.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.OtpException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.common.util.PhoneNumberNormalizer;
import com.lankapay.identity.domain.Otp;
import com.lankapay.identity.domain.OtpMetadata;
import com.lankapay.identity.domain.OtpPurpose;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.otp.OtpProvider;
import com.lankapay.identity.repository.OtpMetadataRepository;
import com.lankapay.identity.repository.OtpRepository;
import com.lankapay.notification.domain.Notification;
import com.lankapay.notification.domain.NotificationChannelType;
import com.lankapay.notification.domain.NotificationType;
import com.lankapay.notification.repository.NotificationRepository;
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
 * Handles OTP generation, delivery, and verification.
 *
 * Security rules:
 * - 6-digit codes generated with {@link SecureRandom}.
 * - Plaintext OTP is NEVER persisted — only a BCrypt hash (cost 10) is stored.
 * - Plaintext is passed to {@link OtpProvider} and then goes out of scope.
 * - After {@code maxAttempts} wrong guesses, the user account is locked.
 * - Replay protection: each OTP row has a {@code used} flag; once marked used,
 *   the same code is rejected even if the expiry has not elapsed.
 * - Resend cooldown: a second OTP cannot be requested until
 *   {@code resendCooldownSeconds} have elapsed.
 * - Daily cap: at most {@code dailyOtpCap} OTPs per (user, purpose) per day.
 *
 * Notification hook:
 * - Every send persists a {@link Notification} row (status=PENDING/SENT)
 *   so the notification audit trail is complete even before a real gateway exists.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_BCRYPT_COST = 10;

    private final OtpRepository         otpRepository;
    private final OtpMetadataRepository  metadataRepository;
    private final NotificationRepository notificationRepository;
    private final OtpProvider            otpProvider;
    private final AuditService           auditService;
    private final BCryptPasswordEncoder  otpEncoder;

    private final long otpExpirySeconds;
    private final int  maxAttempts;
    private final long resendCooldownSeconds;
    private final int  dailyOtpCap;

    public OtpService(
            OtpRepository otpRepository,
            OtpMetadataRepository metadataRepository,
            NotificationRepository notificationRepository,
            OtpProvider otpProvider,
            AuditService auditService,
            @Value("${app.otp.expiry-seconds:300}")          long otpExpirySeconds,
            @Value("${app.otp.max-attempts:5}")              int  maxAttempts,
            @Value("${app.otp.resend-cooldown-seconds:60}")  long resendCooldownSeconds,
            @Value("${app.otp.daily-cap:10}")                int  dailyOtpCap) {

        this.otpRepository        = otpRepository;
        this.metadataRepository   = metadataRepository;
        this.notificationRepository = notificationRepository;
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
     *
     * @param user    Recipient user
     * @param purpose Why the OTP is being sent
     * @return        When the OTP expires (for inclusion in API response)
     * @throws ValidationException {@code OTP_RESEND_TOO_SOON} if cooldown active
     * @throws ValidationException {@code OTP_DAILY_CAP_REACHED} if daily limit hit
     */
    @Transactional
    public Instant generateAndSend(User user, OtpPurpose purpose) {

        // ── Rate-limit checks ──────────────────────────────────
        OtpMetadata meta = getOrCreateMetadata(user, purpose);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        if (meta.isInCooldown()) {
            long secondsLeft = ChronoUnit.SECONDS.between(
                    Instant.now(), meta.getResendCooldownUntil());
            auditService.log(user.getId(), "OTP_RESEND_BLOCKED", "USER",
                    user.getId(),
                    "{\"reason\":\"cooldown\",\"secondsLeft\":" + secondsLeft + "}",
                    null);
            throw new ValidationException("OTP_RESEND_TOO_SOON",
                    "Please wait " + secondsLeft + " second(s) before requesting a new OTP");
        }

        int todayCount = meta.incrementDailyCount(today);
        if (todayCount > dailyOtpCap) {
            auditService.log(user.getId(), "OTP_DAILY_CAP_REACHED", "USER",
                    user.getId(), "{\"purpose\":\"" + purpose + "\"}", null);
            throw new ValidationException("OTP_DAILY_CAP_REACHED",
                    "Daily OTP limit reached. Please try again tomorrow.");
        }

        meta.setCooldownUntil(Instant.now().plusSeconds(resendCooldownSeconds));
        metadataRepository.save(meta);

        // ── Generate, hash, persist OTP ────────────────────────
        String plainOtp   = generateSixDigit();
        String otpHash    = otpEncoder.encode(plainOtp);
        Instant expiresAt = Instant.now().plus(otpExpirySeconds, ChronoUnit.SECONDS);

        otpRepository.save(new Otp(user, otpHash, purpose, expiresAt));

        // ── Persist notification event (hook — no delivery yet) ─
        String maskedPhone = PhoneNumberNormalizer.mask(user.getPhone());
        String notifMsg = buildOtpMessage(purpose, expiresAt);
        Notification notif = new Notification(user, NotificationChannelType.SMS,
                NotificationType.OTP, notifMsg);

        // ── Deliver via OtpProvider ────────────────────────────
        long expiryMinutes = otpExpirySeconds / 60;
        try {
            otpProvider.send(user.getPhone(), maskedPhone, plainOtp, purpose, expiryMinutes);
            notif.markSent();
        } catch (Exception ex) {
            notif.markFailed();
            log.error("OTP delivery failed for maskedPhone={} purpose={}: {}",
                    maskedPhone, purpose, ex.getMessage());
            // Rethrow so the caller knows delivery failed
            throw ex;
        } finally {
            notificationRepository.save(notif);
            // plainOtp goes out of scope here
        }

        auditService.log(user.getId(), "OTP_SENT", "USER",
                user.getId(), "{\"purpose\":\"" + purpose + "\"}", null);
        log.info("OTP sent [purpose={} maskedPhone={}]", purpose, maskedPhone);
        return expiresAt;
    }

    // ── Verify ─────────────────────────────────────────────────

    /**
     * Verify an OTP submitted by the user.
     *
     * Replay protection: even a correct code is rejected if it has already
     * been used ({@code used=true}).
     *
     * @param user     The user submitting the OTP
     * @param purpose  Expected purpose
     * @param plainOtp The 6-digit code submitted by the user
     * @throws OtpException {@code OTP_EXPIRED}       – no valid unexpired+unused OTP found
     * @throws OtpException {@code OTP_INVALID}       – code does not match
     * @throws OtpException {@code OTP_MAX_ATTEMPTS}  – too many wrong guesses; account locked
     */
    @Transactional
    public void verify(User user, OtpPurpose purpose, String plainOtp) {

        Otp latest = otpRepository
                .findLatestValid(user.getId(), purpose, Instant.now())
                .orElseThrow(OtpException::expired);

        // Replay check — findLatestValid already filters used=false, but
        // an explicit guard makes the intent clear and future-proof.
        if (latest.isUsed()) {
            throw OtpException.expired();
        }

        if (!otpEncoder.matches(plainOtp, latest.getOtpHash())) {
            int attempts = user.incrementFailedAttempts();
            if (attempts >= maxAttempts) {
                user.lockUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
                auditService.log(user.getId(), "OTP_MAX_ATTEMPTS", "USER",
                        user.getId(), "{\"purpose\":\"" + purpose + "\"}", null);
                log.warn("OTP max attempts reached, locking userId={}", user.getId());
                throw OtpException.maxAttemptsReached();
            }
            auditService.log(user.getId(), "OTP_INVALID_ATTEMPT", "USER",
                    user.getId(),
                    "{\"purpose\":\"" + purpose + "\",\"attempt\":" + attempts + "}",
                    null);
            throw OtpException.invalid();
        }

        // Success — mark used (single-use / replay protection)
        latest.markUsed();
        user.resetLoginAttempts();

        auditService.log(user.getId(), "OTP_VERIFIED", "USER",
                user.getId(), "{\"purpose\":\"" + purpose + "\"}", null);
    }

    // ── Helpers ────────────────────────────────────────────────

    private OtpMetadata getOrCreateMetadata(User user, OtpPurpose purpose) {
        return metadataRepository
                .findByUserIdAndPurpose(user.getId(), purpose)
                .orElseGet(() -> metadataRepository.save(new OtpMetadata(user, purpose)));
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

package com.lankapay.identity.service;

import com.lankapay.common.exception.OtpException;
import com.lankapay.identity.domain.Otp;
import com.lankapay.identity.domain.OtpPurpose;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.repository.OtpRepository;
import com.lankapay.notification.service.SmsChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Handles OTP generation, storage, and verification.
 *
 * Security rules:
 * - OTPs are 6-digit codes generated with SecureRandom.
 * - The plaintext OTP is NEVER persisted. Only a BCrypt hash is stored.
 * - The plaintext is handed to the SMS channel immediately then discarded.
 * - After {@code maxAttempts} wrong guesses the account is locked via
 *   User.lockUntil() for the configured duration.
 * - OTPs expire after {@code otpExpirySeconds}.
 * - OTP BCrypt uses cost 10 (lower than PIN cost 12) for speed—6-digit
 *   codes have low entropy anyway; the attempt limit is the real guard.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_BCRYPT_COST = 10;

    private final OtpRepository otpRepository;
    private final SmsChannel smsChannel;
    private final BCryptPasswordEncoder otpEncoder;
    private final long otpExpirySeconds;
    private final int maxAttempts;

    public OtpService(
            OtpRepository otpRepository,
            SmsChannel smsChannel,
            @Value("${app.otp.expiry-seconds:300}") long otpExpirySeconds,
            @Value("${app.otp.max-attempts:5}") int maxAttempts) {

        this.otpRepository    = otpRepository;
        this.smsChannel       = smsChannel;
        this.otpEncoder       = new BCryptPasswordEncoder(OTP_BCRYPT_COST);
        this.otpExpirySeconds = otpExpirySeconds;
        this.maxAttempts      = maxAttempts;
    }

    /**
     * Generate a 6-digit OTP, hash it, persist it, and send via SMS.
     * Returns the expiry instant so the caller can include it in the response.
     *
     * @param user    The user to send the OTP to
     * @param purpose Why this OTP is being sent
     * @return        When the OTP expires
     */
    @Transactional
    public Instant generateAndSend(User user, OtpPurpose purpose) {
        String plainOtp  = generateSixDigit();
        String otpHash   = otpEncoder.encode(plainOtp);
        Instant expiresAt = Instant.now().plus(otpExpirySeconds, ChronoUnit.SECONDS);

        otpRepository.save(new Otp(user, otpHash, purpose, expiresAt));

        // Mask phone for logging; pass masked phone to SMS channel
        String maskedPhone = maskPhone(user.getPhone());
        smsChannel.send(maskedPhone, buildOtpMessage(plainOtp, purpose));

        // plainOtp is now out of scope — GC will collect it
        log.info("OTP sent [purpose={} maskedPhone={}]", purpose, maskedPhone);
        return expiresAt;
    }

    /**
     * Verify an OTP submitted by the user.
     * Throws {@link OtpException} for all failure cases.
     * On success, marks the OTP as used.
     *
     * @param user    The user submitting the OTP
     * @param purpose Expected purpose
     * @param plainOtp The 6-digit code submitted by the user
     */
    @Transactional
    public void verify(User user, OtpPurpose purpose, String plainOtp) {
        Otp latest = otpRepository
                .findLatestValid(user.getId(), purpose, Instant.now())
                .orElseThrow(OtpException::expired);   // no valid (unexpired+unused) OTP exists

        if (!otpEncoder.matches(plainOtp, latest.getOtpHash())) {
            // Count all failed OTP attempts via login-attempt counter on the user
            int attempts = user.incrementFailedAttempts();
            if (attempts >= maxAttempts) {
                user.lockUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
                log.warn("OTP max attempts reached, locking userId={}", user.getId());
                throw OtpException.maxAttemptsReached();
            }
            throw OtpException.invalid();
        }

        latest.markUsed();
        user.resetLoginAttempts();
    }

    // ── helpers ────────────────────────────────────────────────

    private String generateSixDigit() {
        int code = new SecureRandom().nextInt(900_000) + 100_000; // 100000–999999
        return String.valueOf(code);
    }

    private String buildOtpMessage(String otp, OtpPurpose purpose) {
        return "Your Lanka Pay OTP for %s is: %s. Valid for %d minutes. [SIMULATED]"
                .formatted(purpose.name().toLowerCase(), otp, otpExpirySeconds / 60);
    }

    /** Mask phone to +9477***4567 – safe to log. */
    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 5) + "***" + phone.substring(phone.length() - 4);
    }
}

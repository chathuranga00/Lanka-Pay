package com.lankapay.simvault.internal;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.OtpException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.identity.domain.OtpPurpose;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.identity.otp.OtpProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends and verifies the VIRTUAL_CARD_CHALLENGE OTP via the existing OtpProvider.
 *
 * ISOLATION: package-private — never exported outside com.lankapay.simvault.
 */
final class VaultOtpChallengeService {

    private static final Logger log = LoggerFactory.getLogger(VaultOtpChallengeService.class);
    private static final int CHALLENGE_TTL_MINUTES = 5;
    private static final int MAX_ATTEMPTS          = 3;

    private final ConcurrentHashMap<String, PendingChallenge> pending = new ConcurrentHashMap<>();

    private final OtpProvider          otpProvider;
    private final UserStore            userStore;
    private final AuditService         auditService;
    private final BCryptPasswordEncoder otpEncoder = new BCryptPasswordEncoder(10);

    VaultOtpChallengeService(OtpProvider otpProvider,
                              UserStore userStore,
                              AuditService auditService) {
        this.otpProvider = otpProvider;
        this.userStore   = userStore;
        this.auditService = auditService;
    }

    /** Send OTP to user's phone; return challengeRef. */
    String startChallenge(Long userId, String ipAddress) {
        UserAccount user = userStore.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER", userId));

        String otp     = generateOtp();
        String hash    = otpEncoder.encode(otp);
        String ref     = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plus(CHALLENGE_TTL_MINUTES, ChronoUnit.MINUTES);

        pending.put(ref, new PendingChallenge(userId, hash, expiry, 0));

        String maskedPhone = com.lankapay.common.util.PhoneNumberNormalizer.mask(user.phone());
        otpProvider.send(user.phone(), maskedPhone, otp,
                OtpPurpose.VIRTUAL_CARD_CHALLENGE, CHALLENGE_TTL_MINUTES);

        auditService.log(userId, "VAULT_CHALLENGE_SENT", "USER", userId, null, ipAddress);
        log.info("[SIMVAULT] Challenge sent userId={}", userId);
        return ref;
    }

    /** Verify OTP for a challenge. Returns confirmed ref on success. */
    String confirmChallenge(String challengeRef, Long userId, String otp) {
        PendingChallenge ch = pending.get(challengeRef);
        if (ch == null || Instant.now().isAfter(ch.expiresAt())) {
            pending.remove(challengeRef);
            throw OtpException.expired();
        }
        if (!ch.userId().equals(userId)) {
            throw new com.lankapay.common.exception.AuthorizationException(
                    "VAULT_CHALLENGE_WRONG_USER", "Challenge does not belong to this user");
        }
        if (!otpEncoder.matches(otp, ch.otpHash())) {
            int attempts = ch.attempts() + 1;
            auditService.log(userId, "VAULT_CHALLENGE_FAILED", "USER", userId, "{\"attempts\":" + attempts + "}", null);
            if (attempts >= MAX_ATTEMPTS) {
                pending.remove(challengeRef);
                throw OtpException.maxAttemptsReached();
            }
            pending.put(challengeRef,
                    new PendingChallenge(ch.userId(), ch.otpHash(), ch.expiresAt(), attempts));
            throw OtpException.invalid();
        }
        pending.remove(challengeRef);
        auditService.log(userId, "VAULT_CHALLENGE_CONFIRMED", "USER", userId, null, null);
        return challengeRef;
    }

    private String generateOtp() {
        int code = 100_000 + new SecureRandom().nextInt(900_000);
        return String.valueOf(code);
    }

    private record PendingChallenge(Long userId, String otpHash, Instant expiresAt, int attempts) {}
}

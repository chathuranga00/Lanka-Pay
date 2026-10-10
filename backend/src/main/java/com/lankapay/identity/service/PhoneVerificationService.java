package com.lankapay.identity.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.util.PhoneNumberNormalizer;
import com.lankapay.identity.domain.OtpPurpose;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.identity.web.PaymentProfileResponse;
import com.lankapay.identity.web.PhoneVerifyResponse;
import com.lankapay.registry.domain.Registry;
import com.lankapay.registry.repository.RegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles the standalone phone-verification flow and the
 * payment-profile (registry) upsert.
 *
 * Flow:
 *   1. POST /api/v1/auth/verify-phone/send-otp
 *         → normalise phone, look up user, check phone belongs to them,
 *           call OtpService.generateAndSend(PHONE_VERIFY), return expiry.
 *   2. POST /api/v1/auth/verify-phone
 *         → normalise, look up user, call OtpService.verify(PHONE_VERIFY),
 *           upsert Registry row, audit PHONE_VERIFIED.
 *
 *   POST /api/v1/payment-profile/phone
 *         → require verified phone, upsert/return Registry entry.
 *
 * Duplicate-phone policy:
 *   A phone number can exist in idn_users (unique) and reg_registry (unique).
 *   If another active user already owns this E.164 number, both endpoints
 *   reject the request with PHONE_ALREADY_REGISTERED (409).
 *   This check is done BEFORE OTP is sent to prevent user-enumeration
 *   via the OTP delivery side-channel.
 */
@Service
public class PhoneVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerificationService.class);

    private final UserRepository     userRepository;
    private final RegistryRepository registryRepository;
    private final OtpService         otpService;
    private final AuditService       auditService;

    public PhoneVerificationService(
            UserRepository userRepository,
            RegistryRepository registryRepository,
            OtpService otpService,
            AuditService auditService) {

        this.userRepository     = userRepository;
        this.registryRepository = registryRepository;
        this.otpService         = otpService;
        this.auditService       = auditService;
    }

    // ── Send OTP ──────────────────────────────────────────────

    /**
     * Normalise the phone, verify it belongs to the authenticated user,
     * and dispatch a PHONE_VERIFY OTP.
     *
     * @param rawPhone   Raw phone string from the request (any SL format)
     * @param userId     Authenticated user's ID
     * @param ipAddress  Caller IP for audit
     * @return           Masked phone + OTP expiry
     */
    @Transactional
    public PhoneVerifyResponse sendOtp(String rawPhone, Long userId, String ipAddress) {
        String e164 = PhoneNumberNormalizer.normalize(rawPhone);
        String masked = PhoneNumberNormalizer.mask(e164);

        User user = requireUser(userId);

        // Ensure the phone belongs to this user account
        requirePhoneBelongsToUser(e164, user);

        // Check the phone isn't already claimed by another active user in the registry
        registryRepository.findByPhoneAndDiscoverableTrue(e164).ifPresent(existing -> {
            if (!existing.getUser().getId().equals(userId)) {
                throw new ConflictException("PHONE_ALREADY_REGISTERED",
                        "This phone number is already registered to another account");
            }
        });

        Instant expiresAt = otpService.generateAndSend(user, OtpPurpose.PHONE_VERIFY);

        auditService.log(userId, "PHONE_VERIFY_OTP_SENT", "USER",
                userId, "{\"maskedPhone\":\"" + masked + "\"}", ipAddress);

        log.info("Phone verify OTP sent userId={} maskedPhone={}", userId, masked);
        return new PhoneVerifyResponse(masked, expiresAt);
    }

    // ── Verify OTP & activate registry ────────────────────────

    /**
     * Verify the OTP and upsert the user's phone into the payment registry.
     *
     * @param rawPhone  Raw phone string from the request
     * @param otp       6-digit plaintext OTP submitted by the user
     * @param userId    Authenticated user's ID
     * @param ipAddress Caller IP for audit
     * @return          The user's payment profile (masked phone + discoverable flag)
     */
    @Transactional
    public PaymentProfileResponse verifyPhone(String rawPhone, String otp,
                                              Long userId, String ipAddress) {
        String e164 = PhoneNumberNormalizer.normalize(rawPhone);
        String masked = PhoneNumberNormalizer.mask(e164);

        User user = requireUser(userId);
        requirePhoneBelongsToUser(e164, user);

        // Duplicate policy: reject if another user already owns this phone in the registry
        registryRepository.findByPhoneAndDiscoverableTrue(e164).ifPresent(existing -> {
            if (!existing.getUser().getId().equals(userId)) {
                throw new ConflictException("PHONE_ALREADY_REGISTERED",
                        "This phone number is already registered to another account");
            }
        });

        // Verify the OTP (throws OtpException on failure)
        otpService.verify(user, OtpPurpose.PHONE_VERIFY, otp);

        // Upsert registry entry
        Registry registry = registryRepository.findByUserId(userId)
                .orElseGet(() -> new Registry(user, e164));
        boolean isNewEntry = registry.getId() == null;

        registryRepository.save(registry);

        String event = isNewEntry ? "PHONE_VERIFIED_NEW" : "PHONE_VERIFIED_UPDATE";
        auditService.log(userId, event, "USER",
                userId, "{\"maskedPhone\":\"" + masked + "\"}", ipAddress);

        log.info("Phone verified userId={} maskedPhone={}", userId, masked);
        return new PaymentProfileResponse(masked, registry.isDiscoverable());
    }

    // ── Payment profile ────────────────────────────────────────

    /**
     * Return (or create) the authenticated user's payment profile.
     * The phone must already be verified — if no registry entry exists,
     * the user is directed to the verify-phone flow first.
     *
     * @param userId    Authenticated user's ID
     * @param ipAddress Caller IP for audit
     * @return          Current payment profile
     */
    @Transactional(readOnly = true)
    public PaymentProfileResponse getOrInitPaymentProfile(Long userId, String ipAddress) {
        Registry registry = registryRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PHONE_NOT_VERIFIED",
                        "No verified phone found. Please complete phone verification first."));

        String masked = PhoneNumberNormalizer.mask(registry.getPhone());
        return new PaymentProfileResponse(masked, registry.isDiscoverable());
    }

    // ── Helpers ───────────────────────────────────────────────

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER", userId));
    }

    /**
     * Ensures the supplied E.164 phone matches the phone stored on this user's account.
     * Prevents a user from verifying a phone number they don't own.
     */
    private void requirePhoneBelongsToUser(String e164, User user) {
        if (!e164.equals(user.getPhone())) {
            throw new ConflictException("PHONE_MISMATCH",
                    "The phone number does not match the one registered to your account");
        }
    }
}

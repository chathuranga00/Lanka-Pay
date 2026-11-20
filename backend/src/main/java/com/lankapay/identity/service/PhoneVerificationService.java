package com.lankapay.identity.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.util.PhoneNumberNormalizer;
import com.lankapay.identity.domain.OtpPurpose;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.identity.web.PaymentProfileResponse;
import com.lankapay.identity.web.PhoneVerifyResponse;
import com.lankapay.registry.domain.PhoneIdentity;
import com.lankapay.registry.domain.PhoneIdentityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles the standalone phone-verification flow and the
 * payment-profile (registry) upsert.
 */
@Service
public class PhoneVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerificationService.class);

    private final UserStore          userStore;
    private final PhoneIdentityStore phoneIdentityStore;
    private final OtpService         otpService;
    private final AuditService       auditService;

    public PhoneVerificationService(
            UserStore userStore,
            PhoneIdentityStore phoneIdentityStore,
            OtpService otpService,
            AuditService auditService) {

        this.userStore          = userStore;
        this.phoneIdentityStore = phoneIdentityStore;
        this.otpService         = otpService;
        this.auditService       = auditService;
    }

    // ── Send OTP ──────────────────────────────────────────────

    /**
     * Normalise the phone, verify it belongs to the authenticated user,
     * and dispatch a PHONE_VERIFY OTP.
     */
    @Transactional
    public PhoneVerifyResponse sendOtp(String rawPhone, Long userId, String ipAddress) {
        String e164 = PhoneNumberNormalizer.normalize(rawPhone);
        String masked = PhoneNumberNormalizer.mask(e164);

        UserAccount user = userStore.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER", userId));

        if (!e164.equals(user.phone())) {
            throw new ConflictException("PHONE_MISMATCH",
                    "The phone number does not match the one registered to your account");
        }

        phoneIdentityStore.findByPhoneAndDiscoverableTrue(e164).ifPresent(existing -> {
            if (!existing.userId().equals(userId)) {
                throw new ConflictException("PHONE_ALREADY_REGISTERED",
                        "This phone number is already registered to another account");
            }
        });

        User tempUser = new User(user.phone(), user.fullName(), user.nicNumber(), user.passwordHash());
        tempUser.setId(user.id());
        tempUser.setRole(user.role());
        tempUser.setStatus(user.status());

        Instant expiresAt = otpService.generateAndSend(tempUser, OtpPurpose.PHONE_VERIFY);

        auditService.log(userId, "PHONE_VERIFY_OTP_SENT", "USER",
                userId, "{\"maskedPhone\":\"" + masked + "\"}", ipAddress);

        log.info("Phone verify OTP sent userId={} maskedPhone={}", userId, masked);
        return new PhoneVerifyResponse(masked, expiresAt);
    }

    // ── Verify OTP & activate registry ────────────────────────

    /**
     * Verify the OTP and upsert the user's phone into the payment registry.
     */
    @Transactional
    public PaymentProfileResponse verifyPhone(String rawPhone, String otp,
                                              Long userId, String ipAddress) {
        String e164 = PhoneNumberNormalizer.normalize(rawPhone);
        String masked = PhoneNumberNormalizer.mask(e164);

        UserAccount user = userStore.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER", userId));

        if (!e164.equals(user.phone())) {
            throw new ConflictException("PHONE_MISMATCH",
                    "The phone number does not match the one registered to your account");
        }

        phoneIdentityStore.findByPhoneAndDiscoverableTrue(e164).ifPresent(existing -> {
            if (!existing.userId().equals(userId)) {
                throw new ConflictException("PHONE_ALREADY_REGISTERED",
                        "This phone number is already registered to another account");
            }
        });

        User tempUser = new User(user.phone(), user.fullName(), user.nicNumber(), user.passwordHash());
        tempUser.setId(user.id());
        tempUser.setRole(user.role());
        tempUser.setStatus(user.status());

        otpService.verify(tempUser, OtpPurpose.PHONE_VERIFY, otp);

        java.util.Optional<PhoneIdentity> existingOpt = phoneIdentityStore.findByUserId(userId);
        boolean isNewEntry = existingOpt.isEmpty();
        PhoneIdentity toSave = existingOpt
                .map(existing -> new PhoneIdentity(
                        existing.id(), userId, e164, existing.discoverable(), user.fullName(), Instant.now()))
                .orElseGet(() -> new PhoneIdentity(
                        null, userId, e164, true, user.fullName(), Instant.now()));

        PhoneIdentity saved = phoneIdentityStore.save(toSave);

        String event = isNewEntry ? "PHONE_VERIFIED_NEW" : "PHONE_VERIFIED_UPDATE";
        auditService.log(userId, event, "USER",
                userId, "{\"maskedPhone\":\"" + masked + "\"}", ipAddress);

        log.info("Phone verified userId={} maskedPhone={}", userId, masked);
        return new PaymentProfileResponse(masked, saved.discoverable());
    }

    // ── Update discovery flag ──────────────────────────────────

    /**
     * Update the authenticated user's discoverable flag.
     * The user must have a verified phone in the registry.
     */
    @Transactional
    public PaymentProfileResponse setDiscovery(Long userId, boolean discoverable,
                                               String ipAddress) {
        PhoneIdentity identity = phoneIdentityStore.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PHONE_NOT_VERIFIED",
                        "No verified phone found. Please complete phone verification first."));

        PhoneIdentity updated = phoneIdentityStore.save(identity.withDiscoverable(discoverable));
        String masked = PhoneNumberNormalizer.mask(updated.phone());
        auditService.log(userId, "DISCOVERY_UPDATED", "USER",
                userId, "{\"discoverable\":" + discoverable + "}", ipAddress);

        log.info("Discovery updated userId={} discoverable={}", userId, discoverable);
        return new PaymentProfileResponse(masked, discoverable);
    }

    // ── Full profile (me) ──────────────────────────────────────

    /**
     * Return the authenticated user's full payment profile including
     * display name and verification status.
     */
    @Transactional(readOnly = true)
    public com.lankapay.identity.web.PaymentProfileMeResponse getMe(Long userId) {
        PhoneIdentity identity = phoneIdentityStore.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PHONE_NOT_VERIFIED",
                        "No verified phone found. Please complete phone verification first."));

        UserAccount user = userStore.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER", userId));

        String masked  = PhoneNumberNormalizer.mask(identity.phone());
        String display = safeDisplayName(user.fullName());

        return new com.lankapay.identity.web.PaymentProfileMeResponse(
                masked, identity.discoverable(), "VERIFIED", display);
    }

    /** First name + last initial, e.g. "Kamal P." */
    static String safeDisplayName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "User";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        return parts[0] + " " + parts[parts.length - 1].substring(0, 1).toUpperCase() + ".";
    }

    // ── Payment profile ────────────────────────────────────────

    /**
     * Return (or create) the authenticated user's payment profile.
     */
    @Transactional(readOnly = true)
    public PaymentProfileResponse getOrInitPaymentProfile(Long userId, String ipAddress) {
        PhoneIdentity identity = phoneIdentityStore.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PHONE_NOT_VERIFIED",
                        "No verified phone found. Please complete phone verification first."));

        String masked = PhoneNumberNormalizer.mask(identity.phone());
        return new PaymentProfileResponse(masked, identity.discoverable());
    }
}

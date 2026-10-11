package com.lankapay.identity.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.OtpException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.identity.domain.*;
import com.lankapay.identity.web.PaymentProfileResponse;
import com.lankapay.identity.web.PhoneVerifyResponse;
import com.lankapay.registry.domain.PhoneIdentity;
import com.lankapay.registry.domain.PhoneIdentityStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PhoneVerificationService.
 * All collaborators mocked — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    @Mock UserStore          userStore;
    @Mock PhoneIdentityStore phoneIdentityStore;
    @Mock OtpService         otpService;
    @Mock AuditService       auditService;

    PhoneVerificationService service;

    static final String PHONE   = "+94771234567";
    static final String RAW     = "0771234567";   // normalises to PHONE
    static final String IP      = "127.0.0.1";

    @BeforeEach
    void setUp() {
        service = new PhoneVerificationService(
                userStore, phoneIdentityStore, otpService, auditService);
    }

    // ── Helpers ────────────────────────────────────────────────

    User userWithPhone(String e164) {
        User u = new User(e164, "Kamal", "199012345678", "hash");
        ReflectionTestUtils.setField(u, "id", 1L);
        u.setStatus(UserStatus.ACTIVE);
        u.setRole(UserRole.CUSTOMER);
        return u;
    }

    PhoneIdentity identityFor(User owner) {
        return new PhoneIdentity(5L, owner.getId(), owner.getPhone(), true, owner.getFullName(), Instant.now());
    }

    // ── sendOtp() ──────────────────────────────────────────────

    @Nested @DisplayName("sendOtp()")
    class SendOtp {

        @Test
        @DisplayName("success – normalises phone, sends OTP, returns masked phone")
        void success() {
            User user = userWithPhone(PHONE);
            when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(user)));
            when(phoneIdentityStore.findByPhoneAndDiscoverableTrue(PHONE))
                    .thenReturn(Optional.empty());
            when(otpService.generateAndSend(any(User.class), eq(OtpPurpose.PHONE_VERIFY)))
                    .thenReturn(Instant.now().plusSeconds(300));

            PhoneVerifyResponse resp = service.sendOtp(RAW, 1L, IP);

            assertThat(resp.maskedPhone()).isEqualTo("+9477***4567");
            assertThat(resp.otpExpiresAt()).isAfter(Instant.now());
            verify(otpService).generateAndSend(any(User.class), eq(OtpPurpose.PHONE_VERIFY));
        }

        @Test
        @DisplayName("PHONE_MISMATCH when phone does not belong to user")
        void phoneMismatch() {
            // User is registered with a different phone
            User user = userWithPhone("+94701111111");
            when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(user)));

            // RAW normalises to PHONE (+94771234567) which != user's phone
            assertThatThrownBy(() -> service.sendOtp(RAW, 1L, IP))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode").isEqualTo("PHONE_MISMATCH");
        }

        @Test
        @DisplayName("PHONE_ALREADY_REGISTERED when another user owns this phone in registry")
        void duplicatePhoneInRegistry() {
            User owner = userWithPhone(PHONE);
            when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(owner)));

            // A different user owns this phone in the registry
            PhoneIdentity existingIdentity = new PhoneIdentity(20L, 99L, PHONE, true, "Other", Instant.now());

            when(phoneIdentityStore.findByPhoneAndDiscoverableTrue(PHONE))
                    .thenReturn(Optional.of(existingIdentity));

            assertThatThrownBy(() -> service.sendOtp(RAW, 1L, IP))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode").isEqualTo("PHONE_ALREADY_REGISTERED");

            verify(otpService, never()).generateAndSend(any(), any());
        }

        @Test
        @DisplayName("same user re-sending OTP for their own registered phone is allowed")
        void ownPhoneReregistrationAllowed() {
            User owner = userWithPhone(PHONE);
            when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(owner)));

            // Same user already has this phone in the registry
            PhoneIdentity ownIdentity = identityFor(owner);
            when(phoneIdentityStore.findByPhoneAndDiscoverableTrue(PHONE))
                    .thenReturn(Optional.of(ownIdentity));
            when(otpService.generateAndSend(any(User.class), eq(OtpPurpose.PHONE_VERIFY)))
                    .thenReturn(Instant.now().plusSeconds(300));

            assertThatCode(() -> service.sendOtp(RAW, 1L, IP)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("user not found → ResourceNotFoundException")
        void userNotFound() {
            when(userStore.findUserById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.sendOtp(RAW, 99L, IP))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── verifyPhone() ──────────────────────────────────────────

    @Nested @DisplayName("verifyPhone()")
    class VerifyPhone {

        @Test
        @DisplayName("success – creates new registry entry on first verification")
        void successNewEntry() {
            User user = userWithPhone(PHONE);
            when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(user)));
            when(phoneIdentityStore.findByPhoneAndDiscoverableTrue(PHONE))
                    .thenReturn(Optional.empty());
            when(phoneIdentityStore.findByUserId(1L)).thenReturn(Optional.empty());

            ArgumentCaptor<PhoneIdentity> savedIdentity = ArgumentCaptor.forClass(PhoneIdentity.class);
            when(phoneIdentityStore.save(savedIdentity.capture()))
                    .thenAnswer(i -> i.getArgument(0));

            doNothing().when(otpService).verify(any(User.class), eq(OtpPurpose.PHONE_VERIFY), eq("123456"));

            PaymentProfileResponse resp = service.verifyPhone(RAW, "123456", 1L, IP);

            assertThat(resp.maskedPhone()).isEqualTo("+9477***4567");
            assertThat(resp.discoverable()).isTrue();
            assertThat(savedIdentity.getValue().phone()).isEqualTo(PHONE);
            verify(auditService).log(eq(1L), eq("PHONE_VERIFIED_NEW"), any(), any(), any(), eq(IP));
        }

        @Test
        @DisplayName("success – updates existing registry entry on re-verification")
        void successExistingEntry() {
            User user = userWithPhone(PHONE);
            PhoneIdentity existing = identityFor(user);
            when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(user)));
            when(phoneIdentityStore.findByPhoneAndDiscoverableTrue(PHONE))
                    .thenReturn(Optional.empty());
            when(phoneIdentityStore.findByUserId(1L)).thenReturn(Optional.of(existing));
            when(phoneIdentityStore.save(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(otpService).verify(any(User.class), eq(OtpPurpose.PHONE_VERIFY), eq("123456"));

            service.verifyPhone(RAW, "123456", 1L, IP);

            verify(auditService).log(eq(1L), eq("PHONE_VERIFIED_UPDATE"), any(), any(), any(), eq(IP));
        }

        @Test
        @DisplayName("OTP_INVALID propagates from OtpService")
        void wrongOtp() {
            User user = userWithPhone(PHONE);
            when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(user)));
            when(phoneIdentityStore.findByPhoneAndDiscoverableTrue(PHONE))
                    .thenReturn(Optional.empty());
            doThrow(OtpException.invalid())
                    .when(otpService).verify(any(User.class), eq(OtpPurpose.PHONE_VERIFY), eq("000000"));

            assertThatThrownBy(() -> service.verifyPhone(RAW, "000000", 1L, IP))
                    .isInstanceOf(OtpException.class)
                    .extracting("errorCode").isEqualTo("OTP_INVALID");

            verify(phoneIdentityStore, never()).save(any());
        }

        @Test
        @DisplayName("OTP_EXPIRED propagates from OtpService")
        void expiredOtp() {
            User user = userWithPhone(PHONE);
            when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(user)));
            when(phoneIdentityStore.findByPhoneAndDiscoverableTrue(PHONE))
                    .thenReturn(Optional.empty());
            doThrow(OtpException.expired())
                    .when(otpService).verify(any(User.class), eq(OtpPurpose.PHONE_VERIFY), eq("123456"));

            assertThatThrownBy(() -> service.verifyPhone(RAW, "123456", 1L, IP))
                    .isInstanceOf(OtpException.class)
                    .extracting("errorCode").isEqualTo("OTP_EXPIRED");
        }

        @Test
        @DisplayName("OTP_MAX_ATTEMPTS propagates from OtpService")
        void tooManyAttempts() {
            User user = userWithPhone(PHONE);
            when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(user)));
            when(phoneIdentityStore.findByPhoneAndDiscoverableTrue(PHONE))
                    .thenReturn(Optional.empty());
            doThrow(OtpException.maxAttemptsReached())
                    .when(otpService).verify(any(User.class), eq(OtpPurpose.PHONE_VERIFY), eq("000000"));

            assertThatThrownBy(() -> service.verifyPhone(RAW, "000000", 1L, IP))
                    .isInstanceOf(OtpException.class)
                    .extracting("errorCode").isEqualTo("OTP_MAX_ATTEMPTS");
        }

        @Test
        @DisplayName("duplicate phone from another user blocks verification")
        void duplicatePhoneBlocksVerify() {
            User owner = userWithPhone(PHONE);
            when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(owner)));

            PhoneIdentity other = new PhoneIdentity(20L, 99L, PHONE, true, "Other", Instant.now());

            when(phoneIdentityStore.findByPhoneAndDiscoverableTrue(PHONE))
                    .thenReturn(Optional.of(other));

            assertThatThrownBy(() -> service.verifyPhone(RAW, "123456", 1L, IP))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode").isEqualTo("PHONE_ALREADY_REGISTERED");

            verify(otpService, never()).verify(any(), any(), any());
        }
    }

    // ── getOrInitPaymentProfile() ──────────────────────────────

    @Nested @DisplayName("getOrInitPaymentProfile()")
    class GetPaymentProfile {

        @Test
        @DisplayName("returns masked phone when registry entry exists")
        void success() {
            User user = userWithPhone(PHONE);
            PhoneIdentity identity = identityFor(user);
            when(phoneIdentityStore.findByUserId(1L)).thenReturn(Optional.of(identity));

            PaymentProfileResponse resp = service.getOrInitPaymentProfile(1L, IP);

            assertThat(resp.maskedPhone()).isEqualTo("+9477***4567");
            assertThat(resp.discoverable()).isTrue();
        }

        @Test
        @DisplayName("PHONE_NOT_VERIFIED when no registry entry")
        void notVerified() {
            when(phoneIdentityStore.findByUserId(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getOrInitPaymentProfile(1L, IP))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("PHONE_NOT_VERIFIED");
        }
    }
}

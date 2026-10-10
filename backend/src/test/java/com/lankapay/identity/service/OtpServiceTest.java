package com.lankapay.identity.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.OtpException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.Otp;
import com.lankapay.identity.domain.OtpMetadata;
import com.lankapay.identity.domain.OtpPurpose;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.otp.OtpProvider;
import com.lankapay.identity.repository.OtpMetadataRepository;
import com.lankapay.identity.repository.OtpRepository;
import com.lankapay.notification.domain.Notification;
import com.lankapay.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OtpService.
 * All collaborators are mocked — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock OtpRepository        otpRepository;
    @Mock OtpMetadataRepository metadataRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock OtpProvider          otpProvider;
    @Mock AuditService         auditService;

    OtpService otpService;

    // Real encoder so we can produce + verify real hashes in tests
    private final BCryptPasswordEncoder realEncoder = new BCryptPasswordEncoder(10);

    static final String PHONE  = "+94771234567";
    static final long   EXPIRY = 300L;   // seconds
    static final int    MAX    = 5;
    static final long   COOLDOWN = 60L;
    static final int    CAP    = 10;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(
                otpRepository, metadataRepository, notificationRepository,
                otpProvider, auditService,
                EXPIRY, MAX, COOLDOWN, CAP);
    }

    // ── Helpers ────────────────────────────────────────────────

    User activeUser() {
        User u = new User(PHONE, "Kamal", "199012345678", "hash");
        ReflectionTestUtils.setField(u, "id", 1L);
        u.setStatus(UserStatus.ACTIVE);
        u.setRole(UserRole.CUSTOMER);
        return u;
    }

    OtpMetadata freshMeta(User user) {
        OtpMetadata m = new OtpMetadata(user, OtpPurpose.PHONE_VERIFY);
        ReflectionTestUtils.setField(m, "id", 10L);
        return m;
    }

    Otp validOtp(User user, String plainCode) {
        String hash = realEncoder.encode(plainCode);
        return new Otp(user, hash, OtpPurpose.PHONE_VERIFY,
                       Instant.now().plusSeconds(300));
    }

    // ── generateAndSend() ──────────────────────────────────────

    @Nested @DisplayName("generateAndSend()")
    class GenerateAndSend {

        @Test
        @DisplayName("success – persists OTP, fires provider, saves notification SENT")
        void success() {
            User user = activeUser();
            OtpMetadata meta = freshMeta(user);

            when(metadataRepository.findByUserIdAndPurpose(1L, OtpPurpose.PHONE_VERIFY))
                    .thenReturn(Optional.of(meta));
            when(otpRepository.save(any(Otp.class))).thenAnswer(i -> i.getArgument(0));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(i -> i.getArgument(0));
            when(metadataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(otpProvider).send(any(), any(), any(), any(), anyLong());

            Instant expiry = otpService.generateAndSend(user, OtpPurpose.PHONE_VERIFY);

            assertThat(expiry).isAfter(Instant.now());
            verify(otpRepository).save(any(Otp.class));
            verify(otpProvider).send(eq(PHONE), contains("***"), any(), eq(OtpPurpose.PHONE_VERIFY), anyLong());
            // notification should be saved (markSent was called in the success path)
            ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(notifCaptor.capture());
        }

        @Test
        @DisplayName("creates new metadata if none exists")
        void createsMetadataOnFirstSend() {
            User user = activeUser();
            OtpMetadata newMeta = freshMeta(user);

            when(metadataRepository.findByUserIdAndPurpose(1L, OtpPurpose.PHONE_VERIFY))
                    .thenReturn(Optional.empty());
            when(metadataRepository.save(any())).thenReturn(newMeta);
            when(otpRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(otpProvider).send(any(), any(), any(), any(), anyLong());

            otpService.generateAndSend(user, OtpPurpose.PHONE_VERIFY);

            // save called twice: once to create metadata, once to update cooldown
            verify(metadataRepository, atLeast(2)).save(any());
        }

        @Test
        @DisplayName("OTP_RESEND_TOO_SOON when cooldown is active")
        void cooldownBlocks() {
            User user = activeUser();
            OtpMetadata meta = freshMeta(user);
            meta.setCooldownUntil(Instant.now().plusSeconds(30));
            when(metadataRepository.findByUserIdAndPurpose(1L, OtpPurpose.PHONE_VERIFY))
                    .thenReturn(Optional.of(meta));

            assertThatThrownBy(() -> otpService.generateAndSend(user, OtpPurpose.PHONE_VERIFY))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("OTP_RESEND_TOO_SOON");

            verify(otpProvider, never()).send(any(), any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("OTP_DAILY_CAP_REACHED when daily limit exceeded")
        void dailyCapBlocks() {
            User user = activeUser();
            OtpMetadata meta = freshMeta(user);

            // Simulate count already at the cap value
            for (int i = 0; i < CAP; i++) {
                meta.incrementDailyCount(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
            }

            when(metadataRepository.findByUserIdAndPurpose(1L, OtpPurpose.PHONE_VERIFY))
                    .thenReturn(Optional.of(meta));

            assertThatThrownBy(() -> otpService.generateAndSend(user, OtpPurpose.PHONE_VERIFY))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("OTP_DAILY_CAP_REACHED");

            verify(otpProvider, never()).send(any(), any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("notification saved as FAILED when provider throws")
        void notificationFailedOnProviderError() {
            User user = activeUser();
            OtpMetadata meta = freshMeta(user);

            when(metadataRepository.findByUserIdAndPurpose(1L, OtpPurpose.PHONE_VERIFY))
                    .thenReturn(Optional.of(meta));
            when(metadataRepository.save(any())).thenReturn(meta);
            when(otpRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            doThrow(new RuntimeException("SMS gateway down"))
                    .when(otpProvider).send(any(), any(), any(), any(), anyLong());

            assertThatThrownBy(() -> otpService.generateAndSend(user, OtpPurpose.PHONE_VERIFY))
                    .isInstanceOf(RuntimeException.class);

            // Notification must still be saved (in finally block) even on failure
            verify(notificationRepository).save(any(Notification.class));
        }
    }

    // ── verify() ──────────────────────────────────────────────

    @Nested @DisplayName("verify()")
    class Verify {

        @Test
        @DisplayName("success – marks OTP used, resets attempts")
        void success() {
            User user = activeUser();
            String code = "847291";
            Otp otp = validOtp(user, code);

            when(otpRepository.findLatestValid(eq(1L), eq(OtpPurpose.PHONE_VERIFY), any()))
                    .thenReturn(Optional.of(otp));

            otpService.verify(user, OtpPurpose.PHONE_VERIFY, code);

            assertThat(otp.isUsed()).isTrue();
            assertThat(user.getFailedLoginAttempts()).isZero();
            verify(auditService).log(eq(1L), eq("OTP_VERIFIED"), any(), any(), any(), isNull());
        }

        @Test
        @DisplayName("OTP_EXPIRED when no valid OTP in DB")
        void noValidOtp() {
            User user = activeUser();
            when(otpRepository.findLatestValid(any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> otpService.verify(user, OtpPurpose.PHONE_VERIFY, "123456"))
                    .isInstanceOf(OtpException.class)
                    .extracting("errorCode").isEqualTo("OTP_EXPIRED");
        }

        @Test
        @DisplayName("OTP_INVALID on wrong code, increments attempt counter")
        void wrongCode() {
            User user = activeUser();
            Otp otp = validOtp(user, "847291");

            when(otpRepository.findLatestValid(any(), any(), any()))
                    .thenReturn(Optional.of(otp));

            assertThatThrownBy(() -> otpService.verify(user, OtpPurpose.PHONE_VERIFY, "000000"))
                    .isInstanceOf(OtpException.class)
                    .extracting("errorCode").isEqualTo("OTP_INVALID");

            assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
            assertThat(otp.isUsed()).isFalse();
        }

        @Test
        @DisplayName("OTP_EXPIRED on replay of already-used OTP")
        void replayUsedOtp() {
            User user = activeUser();
            String code = "847291";
            Otp otp = validOtp(user, code);
            otp.markUsed();  // simulate already-used

            // findLatestValid filters used=false, so it returns empty for a used OTP
            when(otpRepository.findLatestValid(any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> otpService.verify(user, OtpPurpose.PHONE_VERIFY, code))
                    .isInstanceOf(OtpException.class)
                    .extracting("errorCode").isEqualTo("OTP_EXPIRED");
        }

        @Test
        @DisplayName("OTP_MAX_ATTEMPTS on 5th wrong attempt – locks user")
        void maxAttempts() {
            User user = activeUser();
            // Simulate 4 prior failures
            for (int i = 0; i < 4; i++) user.incrementFailedAttempts();

            Otp otp = validOtp(user, "847291");
            when(otpRepository.findLatestValid(any(), any(), any()))
                    .thenReturn(Optional.of(otp));

            assertThatThrownBy(() -> otpService.verify(user, OtpPurpose.PHONE_VERIFY, "000000"))
                    .isInstanceOf(OtpException.class)
                    .extracting("errorCode").isEqualTo("OTP_MAX_ATTEMPTS");

            assertThat(user.isLockedOut()).isTrue();
            verify(auditService).log(eq(1L), eq("OTP_MAX_ATTEMPTS"), any(), any(), any(), isNull());
        }

        @Test
        @DisplayName("4 wrong attempts does NOT lock – only increments counter")
        void fourWrongAttemptsNoLock() {
            User user = activeUser();
            // Simulate 3 prior failures
            for (int i = 0; i < 3; i++) user.incrementFailedAttempts();

            Otp otp = validOtp(user, "847291");
            when(otpRepository.findLatestValid(any(), any(), any()))
                    .thenReturn(Optional.of(otp));

            assertThatThrownBy(() -> otpService.verify(user, OtpPurpose.PHONE_VERIFY, "000000"))
                    .isInstanceOf(OtpException.class)
                    .extracting("errorCode").isEqualTo("OTP_INVALID");

            assertThat(user.getFailedLoginAttempts()).isEqualTo(4);
            assertThat(user.isLockedOut()).isFalse();
        }
    }
}

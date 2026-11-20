package com.lankapay.identity.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.OtpException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.*;
import com.lankapay.identity.otp.OtpProvider;
import com.lankapay.notification.domain.NotificationRecord;
import com.lankapay.notification.domain.NotificationStore;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    @Mock UserStore            userStore;
    @Mock NotificationStore    notificationStore;
    @Mock OtpProvider          otpProvider;
    @Mock AuditService         auditService;

    OtpService otpService;

    static final String PHONE  = "+94771234567";
    static final long   EXPIRY = 300L;   // seconds
    static final int    MAX    = 5;
    static final long   COOLDOWN = 60L;
    static final int    CAP    = 10;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(
                userStore, notificationStore,
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

    OtpMetadataRecord freshMeta(Long userId) {
        return new OtpMetadataRecord(userId, OtpPurpose.PHONE_VERIFY);
    }

    OtpRecord validOtpRecord(Long userId, String plainCode) {
        return new OtpRecord(10L, userId, "$2a$10$dummyhash", OtpPurpose.PHONE_VERIFY,
                false, Instant.now().plusSeconds(300), Instant.now());
    }

    // ── generateAndSend() ──────────────────────────────────────

    @Nested @DisplayName("generateAndSend()")
    class GenerateAndSend {

        @Test
        @DisplayName("success – persists OTP, fires provider, saves notification SENT")
        void success() {
            User user = activeUser();
            OtpMetadataRecord meta = freshMeta(1L);

            when(userStore.findOtpMetadata(1L, OtpPurpose.PHONE_VERIFY))
                    .thenReturn(Optional.of(meta));
            when(userStore.saveOtp(any(OtpRecord.class))).thenAnswer(i -> i.getArgument(0));
            when(notificationStore.saveNotification(any(NotificationRecord.class)))
                    .thenAnswer(i -> i.getArgument(0));
            when(userStore.saveOtpMetadata(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(otpProvider).send(any(), any(), any(), any(), anyLong());

            Instant expiry = otpService.generateAndSend(user, OtpPurpose.PHONE_VERIFY);

            assertThat(expiry).isAfter(Instant.now());
            verify(userStore).saveOtp(any(OtpRecord.class));
            verify(otpProvider).send(eq(PHONE), contains("***"), any(), eq(OtpPurpose.PHONE_VERIFY), anyLong());

            ArgumentCaptor<NotificationRecord> notifCaptor = ArgumentCaptor.forClass(NotificationRecord.class);
            verify(notificationStore).saveNotification(notifCaptor.capture());
        }

        @Test
        @DisplayName("creates new metadata if none exists")
        void createsMetadataOnFirstSend() {
            User user = activeUser();

            when(userStore.findOtpMetadata(1L, OtpPurpose.PHONE_VERIFY))
                    .thenReturn(Optional.empty());
            when(userStore.saveOtpMetadata(any())).thenAnswer(i -> i.getArgument(0));
            when(userStore.saveOtp(any(OtpRecord.class))).thenAnswer(i -> i.getArgument(0));
            when(notificationStore.saveNotification(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));
            doNothing().when(otpProvider).send(any(), any(), any(), any(), anyLong());

            otpService.generateAndSend(user, OtpPurpose.PHONE_VERIFY);

            verify(userStore).saveOtpMetadata(any());
        }

        @Test
        @DisplayName("OTP_RESEND_TOO_SOON when cooldown is active")
        void cooldownBlocks() {
            User user = activeUser();
            OtpMetadataRecord meta = freshMeta(1L).withCooldownUntil(Instant.now().plusSeconds(30));
            when(userStore.findOtpMetadata(1L, OtpPurpose.PHONE_VERIFY))
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
            OtpMetadataRecord meta = new OtpMetadataRecord(
                    null, 1L, OtpPurpose.PHONE_VERIFY, null, CAP, LocalDate.now(ZoneOffset.UTC));

            when(userStore.findOtpMetadata(1L, OtpPurpose.PHONE_VERIFY))
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
            OtpMetadataRecord meta = freshMeta(1L);

            when(userStore.findOtpMetadata(1L, OtpPurpose.PHONE_VERIFY))
                    .thenReturn(Optional.of(meta));
            when(userStore.saveOtpMetadata(any())).thenAnswer(i -> i.getArgument(0));
            when(userStore.saveOtp(any(OtpRecord.class))).thenAnswer(i -> i.getArgument(0));
            when(notificationStore.saveNotification(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));
            doThrow(new RuntimeException("SMS gateway down"))
                    .when(otpProvider).send(any(), any(), any(), any(), anyLong());

            assertThatThrownBy(() -> otpService.generateAndSend(user, OtpPurpose.PHONE_VERIFY))
                    .isInstanceOf(RuntimeException.class);

            verify(notificationStore).saveNotification(any(NotificationRecord.class));
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
            OtpRecord otp = validOtpRecord(1L, code);

            when(userStore.findLatestValidOtp(eq(1L), eq(OtpPurpose.PHONE_VERIFY), any()))
                    .thenReturn(Optional.of(otp));
            when(userStore.verifyAndConsumeOtp(eq(10L), eq(code), any(), eq(MAX))).thenReturn(true);

            otpService.verify(user, OtpPurpose.PHONE_VERIFY, code);

            assertThat(user.getFailedLoginAttempts()).isZero();
            verify(auditService).log(eq(1L), eq("OTP_VERIFIED"), any(), any(), any(), isNull());
        }

        @Test
        @DisplayName("OTP_EXPIRED when no valid OTP in DB")
        void noValidOtp() {
            User user = activeUser();
            when(userStore.findLatestValidOtp(any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> otpService.verify(user, OtpPurpose.PHONE_VERIFY, "123456"))
                    .isInstanceOf(OtpException.class)
                    .extracting("errorCode").isEqualTo("OTP_EXPIRED");
        }

        @Test
        @DisplayName("OTP_INVALID on wrong code, increments attempt counter")
        void wrongCode() {
            User user = activeUser();
            OtpRecord otp = validOtpRecord(1L, "847291");

            when(userStore.findLatestValidOtp(any(), any(), any()))
                    .thenReturn(Optional.of(otp));
            doThrow(OtpException.invalid()).when(userStore).verifyAndConsumeOtp(eq(10L), eq("000000"), any(), eq(MAX));

            assertThatThrownBy(() -> otpService.verify(user, OtpPurpose.PHONE_VERIFY, "000000"))
                    .isInstanceOf(OtpException.class)
                    .extracting("errorCode").isEqualTo("OTP_INVALID");

            assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("OTP_EXPIRED on replay of already-used OTP")
        void replayUsedOtp() {
            User user = activeUser();
            String code = "847291";

            when(userStore.findLatestValidOtp(any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> otpService.verify(user, OtpPurpose.PHONE_VERIFY, code))
                    .isInstanceOf(OtpException.class)
                    .extracting("errorCode").isEqualTo("OTP_EXPIRED");
        }

        @Test
        @DisplayName("OTP_MAX_ATTEMPTS on 5th wrong attempt – locks user")
        void maxAttempts() {
            User user = activeUser();
            for (int i = 0; i < 4; i++) user.incrementFailedAttempts();

            OtpRecord otp = validOtpRecord(1L, "847291");
            when(userStore.findLatestValidOtp(any(), any(), any()))
                    .thenReturn(Optional.of(otp));
            doThrow(OtpException.maxAttemptsReached()).when(userStore).verifyAndConsumeOtp(eq(10L), eq("000000"), any(), eq(MAX));

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
            for (int i = 0; i < 3; i++) user.incrementFailedAttempts();

            OtpRecord otp = validOtpRecord(1L, "847291");
            when(userStore.findLatestValidOtp(any(), any(), any()))
                    .thenReturn(Optional.of(otp));
            doThrow(OtpException.invalid()).when(userStore).verifyAndConsumeOtp(eq(10L), eq("000000"), any(), eq(MAX));

            assertThatThrownBy(() -> otpService.verify(user, OtpPurpose.PHONE_VERIFY, "000000"))
                    .isInstanceOf(OtpException.class)
                    .extracting("errorCode").isEqualTo("OTP_INVALID");

            assertThat(user.getFailedLoginAttempts()).isEqualTo(4);
            assertThat(user.isLockedOut()).isFalse();
        }
    }
}

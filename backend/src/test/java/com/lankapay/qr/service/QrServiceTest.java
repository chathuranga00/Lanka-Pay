package com.lankapay.qr.service;

import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.*;
import com.lankapay.qr.domain.*;
import com.lankapay.qr.web.dto.CreateQrReferenceRequest;
import com.lankapay.qr.web.dto.QrReferenceResponse;
import com.lankapay.qr.web.dto.ResolvedQrRecipientResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QrServiceTest {

    @Mock QrStore qrStore;
    @Mock UserStore userStore;

    QrService qrService;
    User merchant;

    @BeforeEach
    void setUp() {
        qrService = new QrService(qrStore, userStore);

        merchant = new User("+94771234567", "Kamal Perera", "199012345678", "$2a$12$hash");
        ReflectionTestUtils.setField(merchant, "id", 42L);
        ReflectionTestUtils.setField(merchant, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(merchant, "role", UserRole.MERCHANT);
    }

    @Test
    @DisplayName("Create dynamic QR: amount + expiry + unique opaque reference and one-time use")
    void testCreateDynamicQr() {
        when(qrStore.saveQr(any(QrCodeRecord.class))).thenAnswer(i -> i.getArgument(0));

        CreateQrReferenceRequest request = new CreateQrReferenceRequest(
                QrType.DYNAMIC, new BigDecimal("1500.00"), "LKR", "Table 5 bill", 15);

        QrReferenceResponse response = qrService.createQrReference(request, merchant);

        assertThat(response.qrType()).isEqualTo(QrType.DYNAMIC);
        assertThat(response.amount()).isEqualByComparingTo("1500.00");
        assertThat(response.currency()).isEqualTo("LKR");
        assertThat(response.note()).isEqualTo("Table 5 bill");
        assertThat(response.oneTimeUse()).isTrue();
        assertThat(response.status()).isEqualTo(QrStatus.ACTIVE);
        assertThat(response.expiresAt()).isAfter(Instant.now());

        // Invariant: Opaque, unguessable reference (no account data, no PII, no tokens)
        assertThat(response.reference()).startsWith("QREF-");
        assertThat(response.qrPayload()).isEqualTo("LANKAPAY:QR:" + response.reference());
        assertThat(response.qrPayload()).doesNotContain("199012345678"); // No NIC
        assertThat(response.qrPayload()).doesNotContain("bearer");       // No tokens
    }

    @Test
    @DisplayName("Create static QR: reusable, no fixed amount or expiry")
    void testCreateStaticQr() {
        when(qrStore.saveQr(any(QrCodeRecord.class))).thenAnswer(i -> i.getArgument(0));

        CreateQrReferenceRequest request = new CreateQrReferenceRequest(
                QrType.STATIC, null, "LKR", "Store counter", null);

        QrReferenceResponse response = qrService.createQrReference(request, merchant);

        assertThat(response.qrType()).isEqualTo(QrType.STATIC);
        assertThat(response.amount()).isNull();
        assertThat(response.expiresAt()).isNull();
        assertThat(response.oneTimeUse()).isFalse();
        assertThat(response.status()).isEqualTo(QrStatus.ACTIVE);
        assertThat(response.reference()).startsWith("QREF-STATIC-");
    }

    @Test
    @DisplayName("Scan only RESOLVES recipient: returns recipient info without executing transfer")
    void testScanResolvesRecipient() {
        QrCodeRecord dynamicQr = new QrCodeRecord(
                1L, 42L, "qr-1", "QREF-1", QrType.DYNAMIC, new BigDecimal("2500.00"), "LKR",
                "Dinner", QrStatus.ACTIVE, true, "LANKAPAY:QR:QREF-1", Instant.now().plus(15, ChronoUnit.MINUTES), Instant.now());

        when(qrStore.findQrByReferenceOrQrId("QREF-1")).thenReturn(Optional.of(dynamicQr));
        when(userStore.findUserById(42L)).thenReturn(Optional.of(UserAccount.fromUser(merchant)));

        ResolvedQrRecipientResponse resolved = qrService.resolveQrReference("QREF-1");

        assertThat(resolved.recipientId()).isEqualTo(42L);
        assertThat(resolved.recipientDisplayName()).isEqualTo("Kamal Perera");
        assertThat(resolved.maskedPhone()).isEqualTo("+9477***4567");
        assertThat(resolved.amount()).isEqualByComparingTo("2500.00");
        assertThat(resolved.status()).isEqualTo(QrStatus.ACTIVE);
        assertThat(resolved.message()).contains("Recipient resolved successfully");
    }

    @Test
    @DisplayName("Expiry test: scanning expired QR throws QR_EXPIRED")
    void testScanExpiredQr() {
        Instant pastExpiry = Instant.now().minus(10, ChronoUnit.MINUTES);
        QrCodeRecord expiredQr = new QrCodeRecord(
                1L, 42L, "qr-1", "QREF-1", QrType.DYNAMIC, new BigDecimal("1000.00"), "LKR",
                "Expired lunch", QrStatus.ACTIVE, true, "LANKAPAY:QR:QREF-1", pastExpiry, Instant.now());

        when(qrStore.findQrByReferenceOrQrId("QREF-1")).thenReturn(Optional.of(expiredQr));

        assertThatThrownBy(() -> qrService.resolveQrReference("QREF-1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("QR code has expired");

        verify(qrStore).saveQr(any(QrCodeRecord.class));
    }

    @Test
    @DisplayName("Tampered reference test: unknown or manipulated reference throws QR_NOT_FOUND")
    void testTamperedReference() {
        when(qrStore.findQrByReferenceOrQrId("QREF-TAMPERED-12345"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> qrService.resolveQrReference("QREF-TAMPERED-12345"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("QR reference is invalid or unknown");
    }

    @Test
    @DisplayName("Used request test: dynamic one-time QR already marked USED throws QR_ALREADY_USED")
    void testUsedRequestRejected() {
        QrCodeRecord usedQr = new QrCodeRecord(
                1L, 42L, "qr-1", "QREF-1", QrType.DYNAMIC, new BigDecimal("500.00"), "LKR",
                "Paid coffee", QrStatus.USED, true, "LANKAPAY:QR:QREF-1", Instant.now().plus(15, ChronoUnit.MINUTES), Instant.now());

        when(qrStore.findQrByReferenceOrQrId("QREF-1")).thenReturn(Optional.of(usedQr));

        assertThatThrownBy(() -> qrService.resolveQrReference("QREF-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("one-time QR code has already been used");
    }

    @Test
    @DisplayName("Static QR can be scanned multiple times without becoming USED")
    void testStaticQrMultipleScans() {
        QrCodeRecord staticQr = new QrCodeRecord(
                1L, 42L, "qr-static", "QREF-STATIC-1", QrType.STATIC, null, "LKR",
                "Shop tip jar", QrStatus.ACTIVE, false, "LANKAPAY:QR:QREF-STATIC-1", null, Instant.now());

        when(qrStore.findQrByReferenceOrQrId("QREF-STATIC-1")).thenReturn(Optional.of(staticQr));
        when(userStore.findUserById(42L)).thenReturn(Optional.of(UserAccount.fromUser(merchant)));

        ResolvedQrRecipientResponse scan1 = qrService.resolveQrReference("QREF-STATIC-1");
        ResolvedQrRecipientResponse scan2 = qrService.resolveQrReference("QREF-STATIC-1");

        assertThat(scan1.status()).isEqualTo(QrStatus.ACTIVE);
        assertThat(scan2.status()).isEqualTo(QrStatus.ACTIVE);
    }
}

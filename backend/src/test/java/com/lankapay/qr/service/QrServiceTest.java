package com.lankapay.qr.service;

import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.qr.domain.QrCode;
import com.lankapay.qr.domain.QrStatus;
import com.lankapay.qr.domain.QrType;
import com.lankapay.qr.repository.QrCodeRepository;
import com.lankapay.qr.web.dto.CreateQrReferenceRequest;
import com.lankapay.qr.web.dto.QrReferenceResponse;
import com.lankapay.qr.web.dto.ResolvedQrRecipientResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock QrCodeRepository qrCodeRepository;

    QrService qrService;
    User merchant;

    @BeforeEach
    void setUp() {
        qrService = new QrService(qrCodeRepository);

        merchant = new User("+94771234567", "Kamal Perera", "199012345678", "$2a$12$hash");
        ReflectionTestUtils.setField(merchant, "id", 42L);
        ReflectionTestUtils.setField(merchant, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(merchant, "role", UserRole.MERCHANT);
    }

    @Test
    @DisplayName("Create dynamic QR: amount + expiry + unique opaque reference and one-time use")
    void testCreateDynamicQr() {
        when(qrCodeRepository.save(any(QrCode.class))).thenAnswer(i -> i.getArgument(0));

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
        when(qrCodeRepository.save(any(QrCode.class))).thenAnswer(i -> i.getArgument(0));

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
        QrCode dynamicQr = QrCode.createDynamic(
                merchant, new BigDecimal("2500.00"), "LKR", "Dinner", Instant.now().plus(15, ChronoUnit.MINUTES));

        when(qrCodeRepository.findByReferenceOrQrId(dynamicQr.getReference()))
                .thenReturn(Optional.of(dynamicQr));

        ResolvedQrRecipientResponse resolved = qrService.resolveQrReference(dynamicQr.getReference());

        assertThat(resolved.recipientId()).isEqualTo(42L);
        assertThat(resolved.recipientDisplayName()).isEqualTo("Kamal Perera");
        assertThat(resolved.maskedPhone()).isEqualTo("+9477***4567");
        assertThat(resolved.amount()).isEqualByComparingTo("2500.00");
        assertThat(resolved.status()).isEqualTo(QrStatus.ACTIVE);
        assertThat(resolved.message()).contains("Recipient resolved successfully");

        // Verify QR is NOT yet marked used (it only resolves, payment happens in transfer flow)
        assertThat(dynamicQr.getStatus()).isEqualTo(QrStatus.ACTIVE);
    }

    @Test
    @DisplayName("Expiry test: scanning expired QR throws QR_EXPIRED")
    void testScanExpiredQr() {
        Instant pastExpiry = Instant.now().minus(10, ChronoUnit.MINUTES);
        QrCode expiredQr = QrCode.createDynamic(
                merchant, new BigDecimal("1000.00"), "LKR", "Expired lunch", pastExpiry);

        when(qrCodeRepository.findByReferenceOrQrId(expiredQr.getReference()))
                .thenReturn(Optional.of(expiredQr));

        assertThatThrownBy(() -> qrService.resolveQrReference(expiredQr.getReference()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("QR code has expired");

        assertThat(expiredQr.getStatus()).isEqualTo(QrStatus.EXPIRED);
        verify(qrCodeRepository).save(expiredQr);
    }

    @Test
    @DisplayName("Tampered reference test: unknown or manipulated reference throws QR_NOT_FOUND")
    void testTamperedReference() {
        when(qrCodeRepository.findByReferenceOrQrId("QREF-TAMPERED-12345"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> qrService.resolveQrReference("QREF-TAMPERED-12345"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("QR reference is invalid or unknown");
    }

    @Test
    @DisplayName("Used request test: dynamic one-time QR already marked USED throws QR_ALREADY_USED")
    void testUsedRequestRejected() {
        QrCode usedQr = QrCode.createDynamic(
                merchant, new BigDecimal("500.00"), "LKR", "Paid coffee", Instant.now().plus(15, ChronoUnit.MINUTES));
        usedQr.markUsed();

        when(qrCodeRepository.findByReferenceOrQrId(usedQr.getReference()))
                .thenReturn(Optional.of(usedQr));

        assertThatThrownBy(() -> qrService.resolveQrReference(usedQr.getReference()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("one-time QR code has already been used");
    }

    @Test
    @DisplayName("Static QR can be scanned multiple times without becoming USED")
    void testStaticQrMultipleScans() {
        QrCode staticQr = QrCode.createStatic(merchant, "LKR", "Shop tip jar");

        when(qrCodeRepository.findByReferenceOrQrId(staticQr.getReference()))
                .thenReturn(Optional.of(staticQr));

        ResolvedQrRecipientResponse scan1 = qrService.resolveQrReference(staticQr.getReference());
        ResolvedQrRecipientResponse scan2 = qrService.resolveQrReference(staticQr.getReference());

        assertThat(scan1.status()).isEqualTo(QrStatus.ACTIVE);
        assertThat(scan2.status()).isEqualTo(QrStatus.ACTIVE);
        assertThat(staticQr.getStatus()).isEqualTo(QrStatus.ACTIVE);
    }
}

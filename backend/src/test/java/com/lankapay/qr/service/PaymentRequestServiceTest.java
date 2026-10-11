package com.lankapay.qr.service;

import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.*;
import com.lankapay.qr.domain.*;
import com.lankapay.qr.web.dto.CreatePaymentRequestDto;
import com.lankapay.qr.web.dto.PaymentRequestResponse;
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
class PaymentRequestServiceTest {

    @Mock QrStore qrStore;
    @Mock UserStore userStore;

    PaymentRequestService service;

    User requester;
    User designatedPayer;
    User unauthorizedUser;

    @BeforeEach
    void setUp() {
        service = new PaymentRequestService(qrStore, userStore);

        requester = new User("+94771111111", "Requester User", "199011111111", "$2a$12$hash");
        ReflectionTestUtils.setField(requester, "id", 1L);
        ReflectionTestUtils.setField(requester, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(requester, "role", UserRole.CUSTOMER);

        designatedPayer = new User("+94772222222", "Payer User", "199022222222", "$2a$12$hash");
        ReflectionTestUtils.setField(designatedPayer, "id", 2L);
        ReflectionTestUtils.setField(designatedPayer, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(designatedPayer, "role", UserRole.CUSTOMER);

        unauthorizedUser = new User("+94773333333", "Intruder User", "199033333333", "$2a$12$hash");
        ReflectionTestUtils.setField(unauthorizedUser, "id", 99L);
        ReflectionTestUtils.setField(unauthorizedUser, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(unauthorizedUser, "role", UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("Create payment request: generated with unguessable reference and linked dynamic QR")
    void testCreatePaymentRequest() {
        when(userStore.findUserById(2L)).thenReturn(Optional.of(UserAccount.fromUser(designatedPayer)));
        when(qrStore.saveQr(any(QrCodeRecord.class))).thenAnswer(i -> {
            QrCodeRecord r = i.getArgument(0);
            return new QrCodeRecord(10L, r.merchantId(), r.qrId(), r.reference(), r.qrType(),
                    r.amount(), r.currency(), r.note(), r.status(), r.oneTimeUse(),
                    r.payload(), r.expiresAt(), r.createdAt());
        });
        when(qrStore.savePaymentRequest(any(PaymentRequestRecord.class))).thenAnswer(i -> {
            PaymentRequestRecord pr = i.getArgument(0);
            return new PaymentRequestRecord(501L, pr.requesterId(), pr.payerId(), pr.qrCodeId(),
                    pr.reference(), pr.amount(), pr.currency(), pr.note(), pr.status(),
                    pr.expiresAt(), pr.fulfilledTransferId(), pr.createdAt());
        });

        CreatePaymentRequestDto dto = new CreatePaymentRequestDto(
                new BigDecimal("2500.00"), "LKR", 2L, null, "Invoice #1042", 15);

        PaymentRequestResponse response = service.createPaymentRequest(dto, requester);

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.reference()).startsWith("PREQ-");
        assertThat(response.requesterId()).isEqualTo(1L);
        assertThat(response.payerId()).isEqualTo(2L);
        assertThat(response.amount()).isEqualByComparingTo("2500.00");
        assertThat(response.status()).isEqualTo(PaymentRequestStatus.PENDING);
        assertThat(response.qrReference()).startsWith("QREF-");
        assertThat(response.qrPayload()).isEqualTo("LANKAPAY:QR:" + response.qrReference());
    }

    @Test
    @DisplayName("Cannot request payment from oneself")
    void testCannotRequestFromSelf() {
        when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(requester)));

        CreatePaymentRequestDto dto = new CreatePaymentRequestDto(
                new BigDecimal("100.00"), "LKR", 1L, null, "Self", 15);

        assertThatThrownBy(() -> service.createPaymentRequest(dto, requester))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Requester and payer cannot be the same user");
    }

    @Test
    @DisplayName("Unauthorized access test: third party cannot view private payment request")
    void testUnauthorizedAccessThrowsForbidden() {
        PaymentRequestRecord pr = new PaymentRequestRecord(
                601L, 1L, 2L, null, "PREQ-601", new BigDecimal("1000.00"), "LKR", "Private bill",
                PaymentRequestStatus.PENDING, Instant.now().plus(15, ChronoUnit.MINUTES), null, Instant.now());

        when(qrStore.findPaymentRequestById(601L)).thenReturn(Optional.of(pr));

        // Unauthorized user id 99L is neither requester (1L) nor designated payer (2L)
        assertThatThrownBy(() -> service.getPaymentRequest(601L, 99L))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("You do not have authorization to view this payment request");
    }

    @Test
    @DisplayName("Authorized access: requester and designated payer can both access")
    void testAuthorizedAccess() {
        PaymentRequestRecord pr = new PaymentRequestRecord(
                601L, 1L, 2L, null, "PREQ-601", new BigDecimal("1000.00"), "LKR", "Private bill",
                PaymentRequestStatus.PENDING, Instant.now().plus(15, ChronoUnit.MINUTES), null, Instant.now());

        when(qrStore.findPaymentRequestById(601L)).thenReturn(Optional.of(pr));
        when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(requester)));
        when(userStore.findUserById(2L)).thenReturn(Optional.of(UserAccount.fromUser(designatedPayer)));

        // Requester access
        PaymentRequestResponse reqView = service.getPaymentRequest(601L, 1L);
        assertThat(reqView.id()).isEqualTo(601L);

        // Payer access
        PaymentRequestResponse payerView = service.getPaymentRequest(601L, 2L);
        assertThat(payerView.id()).isEqualTo(601L);
    }

    @Test
    @DisplayName("Expiry test: viewing expired request updates status to EXPIRED")
    void testExpiredPaymentRequest() {
        Instant pastExpiry = Instant.now().minus(5, ChronoUnit.MINUTES);
        PaymentRequestRecord pr = new PaymentRequestRecord(
                701L, 1L, 2L, null, "PREQ-701", new BigDecimal("1000.00"), "LKR", "Expired invoice",
                PaymentRequestStatus.PENDING, pastExpiry, null, Instant.now());

        when(qrStore.findPaymentRequestById(701L)).thenReturn(Optional.of(pr));
        when(qrStore.savePaymentRequest(any(PaymentRequestRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(requester)));
        when(userStore.findUserById(2L)).thenReturn(Optional.of(UserAccount.fromUser(designatedPayer)));

        PaymentRequestResponse response = service.getPaymentRequest(701L, 1L);
        assertThat(response.status()).isEqualTo(PaymentRequestStatus.EXPIRED);
    }
}

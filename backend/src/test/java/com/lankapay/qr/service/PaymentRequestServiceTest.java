package com.lankapay.qr.service;

import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.qr.domain.PaymentRequest;
import com.lankapay.qr.domain.PaymentRequestStatus;
import com.lankapay.qr.domain.QrCode;
import com.lankapay.qr.repository.PaymentRequestRepository;
import com.lankapay.qr.repository.QrCodeRepository;
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

    @Mock PaymentRequestRepository paymentRequestRepository;
    @Mock QrCodeRepository qrCodeRepository;
    @Mock UserRepository userRepository;

    PaymentRequestService service;

    User requester;
    User designatedPayer;
    User unauthorizedUser;

    @BeforeEach
    void setUp() {
        service = new PaymentRequestService(paymentRequestRepository, qrCodeRepository, userRepository);

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
        when(userRepository.findById(2L)).thenReturn(Optional.of(designatedPayer));
        when(qrCodeRepository.save(any(QrCode.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRequestRepository.save(any(PaymentRequest.class))).thenAnswer(i -> {
            PaymentRequest pr = i.getArgument(0);
            ReflectionTestUtils.setField(pr, "id", 501L);
            return pr;
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));

        CreatePaymentRequestDto dto = new CreatePaymentRequestDto(
                new BigDecimal("100.00"), "LKR", 1L, null, "Self", 15);

        assertThatThrownBy(() -> service.createPaymentRequest(dto, requester))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Requester and payer cannot be the same user");
    }

    @Test
    @DisplayName("Unauthorized access test: third party cannot view private payment request")
    void testUnauthorizedAccessThrowsForbidden() {
        PaymentRequest pr = new PaymentRequest(
                requester, designatedPayer, new BigDecimal("1000.00"), "LKR", "Private bill",
                Instant.now().plus(15, ChronoUnit.MINUTES), null);
        ReflectionTestUtils.setField(pr, "id", 601L);

        when(paymentRequestRepository.findById(601L)).thenReturn(Optional.of(pr));

        // Unauthorized user id 99L is neither requester (1L) nor designated payer (2L)
        assertThatThrownBy(() -> service.getPaymentRequest(601L, 99L))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("You do not have authorization to view this payment request");
    }

    @Test
    @DisplayName("Authorized access: requester and designated payer can both access")
    void testAuthorizedAccess() {
        PaymentRequest pr = new PaymentRequest(
                requester, designatedPayer, new BigDecimal("1000.00"), "LKR", "Private bill",
                Instant.now().plus(15, ChronoUnit.MINUTES), null);
        ReflectionTestUtils.setField(pr, "id", 601L);

        when(paymentRequestRepository.findById(601L)).thenReturn(Optional.of(pr));

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
        PaymentRequest pr = new PaymentRequest(
                requester, designatedPayer, new BigDecimal("1000.00"), "LKR", "Expired invoice",
                pastExpiry, null);
        ReflectionTestUtils.setField(pr, "id", 701L);

        when(paymentRequestRepository.findById(701L)).thenReturn(Optional.of(pr));
        when(paymentRequestRepository.save(any(PaymentRequest.class))).thenAnswer(i -> i.getArgument(0));

        PaymentRequestResponse response = service.getPaymentRequest(701L, 1L);
        assertThat(response.status()).isEqualTo(PaymentRequestStatus.EXPIRED);
    }
}

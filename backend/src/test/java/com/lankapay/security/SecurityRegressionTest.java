package com.lankapay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.repository.BankAccountRepository;
import com.lankapay.bank.repository.BankInstitutionRepository;
import com.lankapay.bank.service.BankAccountService;
import com.lankapay.bank.web.BankAccountController;
import com.lankapay.bank.web.RemoveBankAccountRequest;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.RateLimitException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.notification.web.NotificationController;
import com.lankapay.qr.service.PaymentRequestService;
import com.lankapay.qr.web.PaymentRequestController;
import com.lankapay.registry.service.LookupService;
import com.lankapay.registry.web.LookupResponse;
import com.lankapay.registry.web.PaymentIdentityController;
import com.lankapay.transfer.domain.*;
import com.lankapay.transfer.repository.IdempotencyRecordRepository;
import com.lankapay.transfer.service.IdempotencyService;
import com.lankapay.transfer.service.ReconciliationService;
import com.lankapay.transfer.service.ReversalService;
import com.lankapay.transfer.service.TransferService;
import com.lankapay.transfer.web.TransferController;
import com.lankapay.transfer.web.dto.InitiateTransferRequest;
import com.lankapay.transfer.web.dto.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security Regression Test Suite.
 * Covers:
 * 1. IDOR (Insecure Direct Object Reference) across transfers, receipts, accounts, requests.
 * 2. Enumeration defenses (404 masking, no PII leakage on non-discoverable, rate limiting).
 * 3. Replay attacks (Idempotency key reuse, tampering rejection).
 * 4. Duplicate payments & state machine integrity (double submit handling, illegal transitions).
 */
@ExtendWith(MockitoExtension.class)
public class SecurityRegressionTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // ─────────────────────────────────────────────────────────────
    // 1. IDOR REGRESSION TESTS
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("1. IDOR (Insecure Direct Object Reference) Protection")
    class IdorTests {

        @Mock TransferService transferService;
        @Mock ReconciliationService reconciliationService;
        @Mock ReversalService reversalService;
        @Mock UserRepository userRepository;
        @Mock BankAccountService bankAccountService;
        @Mock BankInstitutionRepository bankInstitutionRepository;
        @Mock PaymentRequestService paymentRequestService;
        @Mock NotificationService notificationService;

        MockMvc transferMvc;
        MockMvc bankMvc;
        MockMvc paymentRequestMvc;
        MockMvc notificationMvc;

        final Long ATTACKER_USER_ID = 100L;
        final Long VICTIM_TRANSFER_ID = 999L;
        final Long VICTIM_ACCOUNT_ID = 888L;
        final Long VICTIM_REQUEST_ID = 777L;
        final Long VICTIM_NOTIFICATION_ID = 666L;

        @BeforeEach
        void setUp() {
            HandlerMethodArgumentResolver authResolver = new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                }

                @Override
                public Object resolveArgument(MethodParameter parameter,
                                              ModelAndViewContainer mavContainer,
                                              NativeWebRequest webRequest,
                                              WebDataBinderFactory binderFactory) {
                    return ATTACKER_USER_ID;
                }
            };

            transferMvc = MockMvcBuilders.standaloneSetup(new TransferController(
                            transferService, reconciliationService, reversalService, userRepository))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(authResolver, new PageableHandlerMethodArgumentResolver())
                    .build();

            bankMvc = MockMvcBuilders.standaloneSetup(new BankAccountController(
                            bankAccountService, bankInstitutionRepository))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(authResolver)
                    .build();

            paymentRequestMvc = MockMvcBuilders.standaloneSetup(new PaymentRequestController(
                            paymentRequestService, userRepository))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(authResolver)
                    .build();

            notificationMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(authResolver, new PageableHandlerMethodArgumentResolver())
                    .build();
        }

        @Test
        @DisplayName("IDOR: Attacker cannot access victim's transfer details -> 403 Forbidden")
        void testTransferDetailsIdorForbidden() throws Exception {
            when(transferService.getTransferById(VICTIM_TRANSFER_ID, ATTACKER_USER_ID))
                    .thenThrow(new AuthorizationException("FORBIDDEN", "You do not have permission to access transfer id: " + VICTIM_TRANSFER_ID));

            transferMvc.perform(get("/api/v1/transfers/" + VICTIM_TRANSFER_ID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("IDOR: Attacker cannot access victim's transfer receipt -> 403 Forbidden")
        void testTransferReceiptIdorForbidden() throws Exception {
            when(transferService.getTransferReceipt(VICTIM_TRANSFER_ID, ATTACKER_USER_ID))
                    .thenThrow(new AuthorizationException("FORBIDDEN", "You do not have permission to view receipt for transfer id: " + VICTIM_TRANSFER_ID));

            transferMvc.perform(get("/api/v1/transfers/" + VICTIM_TRANSFER_ID + "/receipt"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("IDOR: Attacker cannot trigger reconciliation on victim's transfer -> 403 Forbidden")
        void testTransferReconcileIdorForbidden() throws Exception {
            when(reconciliationService.reconcileTransfer(VICTIM_TRANSFER_ID, ATTACKER_USER_ID))
                    .thenThrow(new AuthorizationException("FORBIDDEN", "You do not have permission to reconcile transfer id: " + VICTIM_TRANSFER_ID));

            transferMvc.perform(post("/api/v1/transfers/" + VICTIM_TRANSFER_ID + "/reconcile"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("IDOR: Attacker cannot view victim's bank account -> 403 Forbidden")
        void testBankAccountGetIdorForbidden() throws Exception {
            when(bankAccountService.getAccount(VICTIM_ACCOUNT_ID, ATTACKER_USER_ID))
                    .thenThrow(new AuthorizationException("FORBIDDEN", "Not the account owner"));

            bankMvc.perform(get("/api/v1/bank-accounts/" + VICTIM_ACCOUNT_ID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("IDOR: Attacker cannot set victim's bank account as default -> 403 Forbidden")
        void testBankAccountSetDefaultIdorForbidden() throws Exception {
            when(bankAccountService.setDefault(eq(VICTIM_ACCOUNT_ID), eq(ATTACKER_USER_ID), anyString()))
                    .thenThrow(new AuthorizationException("FORBIDDEN", "Not the account owner"));

            bankMvc.perform(post("/api/v1/bank-accounts/" + VICTIM_ACCOUNT_ID + "/set-default"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("IDOR: Attacker cannot remove victim's bank account -> 403 Forbidden")
        void testBankAccountRemoveIdorForbidden() throws Exception {
            doThrow(new AuthorizationException("FORBIDDEN", "Not the account owner"))
                    .when(bankAccountService).removeAccount(eq(VICTIM_ACCOUNT_ID), eq(ATTACKER_USER_ID), eq(true), anyString());

            RemoveBankAccountRequest req = new RemoveBankAccountRequest(true);

            bankMvc.perform(delete("/api/v1/bank-accounts/" + VICTIM_ACCOUNT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("IDOR: Attacker cannot inspect victim's payment request -> 403 Forbidden")
        void testPaymentRequestIdorForbidden() throws Exception {
            when(paymentRequestService.getPaymentRequest(VICTIM_REQUEST_ID, ATTACKER_USER_ID))
                    .thenThrow(new AuthorizationException("FORBIDDEN", "You do not have authorization to view this payment request"));

            paymentRequestMvc.perform(get("/api/v1/payment-requests/" + VICTIM_REQUEST_ID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("IDOR: Attacker marking victim's notification as read returns 404 NOT_FOUND (does not leak existence)")
        void testNotificationMarkReadIdorNotFound() throws Exception {
            when(notificationService.markAsRead(VICTIM_NOTIFICATION_ID, ATTACKER_USER_ID))
                    .thenThrow(new ResourceNotFoundException("NOTIFICATION_NOT_FOUND", "Notification not found"));

            notificationMvc.perform(patch("/api/v1/notifications/" + VICTIM_NOTIFICATION_ID + "/read"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. ENUMERATION & INFORMATION DISCLOSURE TESTS
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("2. Phone Number Enumeration & Information Disclosure Protection")
    class EnumerationTests {

        @Mock LookupService lookupService;
        MockMvc lookupMvc;

        final Long CALLER_ID = 50L;

        @BeforeEach
        void setUp() {
            HandlerMethodArgumentResolver authResolver = new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                }

                @Override
                public Object resolveArgument(MethodParameter parameter,
                                              ModelAndViewContainer mavContainer,
                                              NativeWebRequest webRequest,
                                              WebDataBinderFactory binderFactory) {
                    return CALLER_ID;
                }
            };

            lookupMvc = MockMvcBuilders.standaloneSetup(new PaymentIdentityController(lookupService))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(authResolver)
                    .build();
        }

        @Test
        @DisplayName("Enumeration defense: Non-registered phone returns 404 NOT_FOUND with zero PII")
        void testNonRegisteredPhoneReturns404WithNoPii() throws Exception {
            when(lookupService.lookup(eq("+94770000000"), eq(CALLER_ID), anyString()))
                    .thenReturn(LookupResponse.notFound());

            lookupMvc.perform(get("/api/v1/payment-identities/lookup")
                            .param("phone", "+94770000000"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.resultType").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.displayName").doesNotExist())
                    .andExpect(jsonPath("$.accounts").doesNotExist());
        }

        @Test
        @DisplayName("Enumeration defense: Registered phone with discoverable=false returns 404 with zero PII")
        void testUndiscoverablePhoneReturns404WithNoPii() throws Exception {
            when(lookupService.lookup(eq("+94771111111"), eq(CALLER_ID), anyString()))
                    .thenReturn(LookupResponse.discoveryDisabled());

            lookupMvc.perform(get("/api/v1/payment-identities/lookup")
                            .param("phone", "+94771111111"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.resultType").value("DISCOVERY_DISABLED"))
                    .andExpect(jsonPath("$.displayName").doesNotExist())
                    .andExpect(jsonPath("$.accounts").doesNotExist());
        }

        @Test
        @DisplayName("Enumeration defense: High volume lookups trigger rate limiting -> 429 Too Many Requests")
        void testRateLimitExceededReturns429() throws Exception {
            when(lookupService.lookup(anyString(), eq(CALLER_ID), anyString()))
                    .thenThrow(new RateLimitException("LOOKUP_RATE_LIMIT_EXCEEDED", "Too many lookup requests"));

            lookupMvc.perform(get("/api/v1/payment-identities/lookup")
                            .param("phone", "+94779999999"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("LOOKUP_RATE_LIMIT_EXCEEDED"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. REPLAY & IDEMPOTENCY ATTACK TESTS
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("3. Replay Attack & Idempotency Tampering Protection")
    class ReplayTests {

        @Mock IdempotencyRecordRepository idempotencyRecordRepository;
        IdempotencyService idempotencyService;

        @BeforeEach
        void setUp() {
            idempotencyService = new IdempotencyService(idempotencyRecordRepository, objectMapper);
        }

        @Test
        @DisplayName("Replay attack: Replaying request with same idempotency key and same payload returns cached response")
        void testReplayWithSamePayloadReturnsCached() throws Exception {
            String key = "IDEM-SEC-001";
            InitiateTransferRequest payload = new InitiateTransferRequest(
                    2L, null, new BigDecimal("1000.00"), "LKR", null, "Lunch", "123456");

            String hash = idempotencyService.hashPayload(payload);

            User sender = new User("+94771111111", "Sender", "199011111111", "hash");
            User receiver = new User("+94772222222", "Receiver", "199022222222", "hash");
            Transfer transfer = new Transfer("REF-001", sender, receiver, new BigDecimal("1000.00"), "LKR", null);
            ReflectionTestUtils.setField(transfer, "id", 10L);
            transfer.startProcessing("START");
            transfer.complete("TX-001", "PROV-001");

            TransferResponse resp = TransferResponse.from(transfer);
            String cachedJson = objectMapper.writeValueAsString(resp);

            IdempotencyRecord record = new IdempotencyRecord(key, hash, 10L, cachedJson, 200);
            when(idempotencyRecordRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

            Optional<TransferResponse> result = idempotencyService.findExistingResponse(key, hash);
            assertThat(result).isPresent();
            assertThat(result.get().reference()).isEqualTo("REF-001");
            assertThat(result.get().status()).isEqualTo(TransactionStatus.COMPLETED);
        }

        @Test
        @DisplayName("Replay tampering: Replaying request with same key but tampered amount is rejected with 409 Conflict")
        void testReplayWithTamperedPayloadThrowsConflict() {
            String key = "IDEM-SEC-002";
            InitiateTransferRequest originalPayload = new InitiateTransferRequest(
                    2L, null, new BigDecimal("1000.00"), "LKR", null, "Lunch", "123456");
            String originalHash = idempotencyService.hashPayload(originalPayload);

            IdempotencyRecord existingRecord = new IdempotencyRecord(key, originalHash, 10L, "{}", 200);
            when(idempotencyRecordRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existingRecord));

            // Attacker modifies amount from 1000.00 to 50000.00
            InitiateTransferRequest tamperedPayload = new InitiateTransferRequest(
                    2L, null, new BigDecimal("50000.00"), "LKR", null, "Lunch", "123456");
            String tamperedHash = idempotencyService.hashPayload(tamperedPayload);

            assertThatThrownBy(() -> idempotencyService.findExistingResponse(key, tamperedHash))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("different request payload");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. DUPLICATE PAYMENT & STATE MACHINE INTEGRITY TESTS
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("4. Duplicate Payments & State Machine Legal Transitions")
    class DuplicatePaymentTests {

        @Test
        @DisplayName("State machine integrity: COMPLETED transfer cannot be moved to FAILED (prevents repudiation/double-credit)")
        void testCompletedCannotTransitionToFailed() {
            User sender = new User("+94771111111", "Sender", "199011111111", "hash");
            User receiver = new User("+94772222222", "Receiver", "199022222222", "hash");
            Transfer transfer = new Transfer("REF-TX-01", sender, receiver, new BigDecimal("500.00"), "LKR", null);

            // Move PENDING -> PROCESSING -> COMPLETED
            transfer.startProcessing("START");
            transfer.complete("PROV-REF-1", "PAY-REF-1");
            assertThat(transfer.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

            // Attempt illegal transition: COMPLETED -> FAILED
            assertThatThrownBy(() -> transfer.fail("ILLEGAL_FAIL"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Illegal transfer state transition: COMPLETED -> FAILED");
        }

        @Test
        @DisplayName("State machine integrity: COMPLETED transfer cannot be moved back to PENDING")
        void testCompletedCannotTransitionToPending() {
            User sender = new User("+94771111111", "Sender", "199011111111", "hash");
            User receiver = new User("+94772222222", "Receiver", "199022222222", "hash");
            Transfer transfer = new Transfer("REF-TX-02", sender, receiver, new BigDecimal("500.00"), "LKR", null);

            transfer.startProcessing("START");
            transfer.complete("PROV-REF-2", "PAY-REF-2");

            assertThatThrownBy(() -> transfer.transitionTo(TransactionStatus.PENDING, "ILLEGAL_PENDING", "Rewind attempt"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Illegal transfer state transition: COMPLETED -> PENDING");
        }

        @Test
        @DisplayName("State machine integrity: FAILED transfer cannot be moved to COMPLETED")
        void testFailedCannotTransitionToCompleted() {
            User sender = new User("+94771111111", "Sender", "199011111111", "hash");
            User receiver = new User("+94772222222", "Receiver", "199022222222", "hash");
            Transfer transfer = new Transfer("REF-TX-03", sender, receiver, new BigDecimal("500.00"), "LKR", null);

            transfer.startProcessing("START");
            transfer.fail("DEBIT_FAILED");
            assertThat(transfer.getStatus()).isEqualTo(TransactionStatus.FAILED);

            assertThatThrownBy(() -> transfer.complete("PROV-REF-3", "PAY-REF-3"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Illegal transfer state transition: FAILED -> COMPLETED");
        }

        @Test
        @DisplayName("Reversal integrity: Reversal can ONLY be initiated on COMPLETED transfers")
        void testReversalOnlyFromCompleted() {
            User sender = new User("+94771111111", "Sender", "199011111111", "hash");
            User receiver = new User("+94772222222", "Receiver", "199022222222", "hash");
            Transfer transfer = new Transfer("REF-TX-04", sender, receiver, new BigDecimal("500.00"), "LKR", null);

            // Transfer is in PENDING status
            assertThatThrownBy(() -> transfer.requestReversal("Customer dispute"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Illegal transfer state transition: PENDING -> REVERSAL_PENDING");
        }
    }
}

package com.lankapay.transfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.*;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.InvalidCredentialsException;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.transfer.domain.*;
import com.lankapay.transfer.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock TransactionStore transactionStore;
    @Mock LinkedAccountStore linkedAccountStore;
    @Mock UserStore userStore;
    @Mock BankAdapterRegistry bankAdapterRegistry;
    @Mock BankAdapter bankAdapter;
    @Mock DailyLimitService dailyLimitService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;

    ObjectMapper objectMapper;
    IdempotencyService idempotencyService;
    TransferProcessor transferProcessor;
    ReconciliationService reconciliationService;
    ReversalService reversalService;
    TransferService transferService;

    User sender;
    User payee;
    BankAccount senderAccount;
    BankAccount payeeAccount;

    static final String RAW_PIN = "123456";
    static final String HASHED_PIN = "$2a$12$hashedpinplaceholder";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        idempotencyService = new IdempotencyService(transactionStore, objectMapper);

        transferProcessor = new TransferProcessor(
                transactionStore,
                bankAdapterRegistry,
                dailyLimitService,
                auditService,
                null
        );

        reconciliationService = new ReconciliationService(
                transactionStore,
                linkedAccountStore,
                bankAdapterRegistry,
                dailyLimitService,
                auditService
        );

        reversalService = new ReversalService(
                transactionStore,
                auditService
        );

        transferService = new TransferService(
                transactionStore,
                userStore,
                linkedAccountStore,
                transferProcessor,
                dailyLimitService,
                idempotencyService,
                passwordEncoder,
                null
        );

        sender = new User("+94771234567", "Sender User", "199012345678", HASHED_PIN);
        ReflectionTestUtils.setField(sender, "id", 1L);
        ReflectionTestUtils.setField(sender, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(sender, "role", UserRole.CUSTOMER);

        payee = new User("+94777654321", "Payee User", "199112345678", HASHED_PIN);
        ReflectionTestUtils.setField(payee, "id", 2L);
        ReflectionTestUtils.setField(payee, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(payee, "role", UserRole.CUSTOMER);

        senderAccount = new BankAccount(sender, "SIMULATED_BANK_A", "hash1", "****1001", "Sender Account", "enc1");
        ReflectionTestUtils.setField(senderAccount, "id", 10L);
        ReflectionTestUtils.setField(senderAccount, "isDefault", true);

        payeeAccount = new BankAccount(payee, "SIMULATED_BANK_B", "hash2", "****2002", "Payee Account", "enc2");
        ReflectionTestUtils.setField(payeeAccount, "id", 20L);
        ReflectionTestUtils.setField(payeeAccount, "isDefault", true);
    }

    private void mockCommonDependencies() {
        when(userStore.findUserById(2L)).thenReturn(Optional.of(UserAccount.fromUser(payee)));
        when(linkedAccountStore.findActiveDefaultAccount(1L))
                .thenReturn(Optional.of(LinkedAccount.fromBankAccount(senderAccount)));
        when(linkedAccountStore.findActiveDefaultAccount(2L))
                .thenReturn(Optional.of(LinkedAccount.fromBankAccount(payeeAccount)));
        when(bankAdapterRegistry.get("SIMULATED_BANK_A")).thenReturn(bankAdapter);
        when(passwordEncoder.matches(RAW_PIN, HASHED_PIN)).thenReturn(true);

        when(transactionStore.createTransferWithIdempotency(any(), any(), any()))
                .thenAnswer(inv -> {
                    TransactionRecord tr = inv.getArgument(0);
                    return new TransactionRecord(
                            100L, tr.reference(), tr.idempotencyKey(), tr.senderId(), tr.senderPhone(),
                            tr.senderFullName(), tr.receiverId(), tr.receiverPhone(), tr.receiverFullName(),
                            tr.senderBankCode(), tr.receiverBankCode(), tr.amount(), tr.currency(),
                            tr.status(), tr.failureReason(), tr.bankTxRef(), tr.providerReference(),
                            tr.note(), tr.simulated(), tr.initiatedVia(), tr.qrCodeId(),
                            1L, tr.createdAt(), tr.updatedAt()
                    );
                });

        when(transactionStore.transitionTransferStatus(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Long tid = inv.getArgument(0);
                    TransactionStatus st = inv.getArgument(1);
                    return new TransactionRecord(
                            tid, "ref", "idem", 1L, "+94771234567", "Sender", 2L, "+94777654321", "Payee",
                            "SIMULATED_BANK_A", "SIMULATED_BANK_B", new BigDecimal("1500.00"), "LKR",
                            st, null, inv.getArgument(4), inv.getArgument(5), null, true,
                            TransferInitiatedVia.APP, null, 2L, Instant.now(), Instant.now()
                    );
                });
    }

    @Test
    @DisplayName("Success: adapter confirms COMPLETED and response reflects COMPLETED status")
    void testSuccessfulTransfer() {
        mockCommonDependencies();

        when(bankAdapter.checkTransferEligibility(any(TransferEligibilityRequest.class)))
                .thenReturn(AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE"));
        when(bankAdapter.initiateTransfer(any(TransferRequest.class)))
                .thenReturn(AdapterResponse.success("SIM-TX-12345", "SUCCESS"));

        InitiateTransferRequest request = new InitiateTransferRequest(
                2L, null, new BigDecimal("1500.00"), "LKR", null, "Lunch split", RAW_PIN);

        TransferResponse response = transferService.initiateTransfer(
                "IDEM-KEY-1", null, request, sender, "127.0.0.1");

        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.bankTxRef()).isEqualTo("SIM-TX-12345");
        assertThat(response.amount()).isEqualByComparingTo("1500.00");

        // Verify daily spend was recorded
        verify(dailyLimitService).recordSpend(sender, new BigDecimal("1500.00"));
        // Verify transition saved
        verify(transactionStore, atLeast(2)).transitionTransferStatus(anyLong(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Step-up authentication: missing or invalid PIN is rejected")
    void testStepUpAuthFailure() {
        InitiateTransferRequest requestNoPin = new InitiateTransferRequest(
                2L, null, new BigDecimal("1500.00"), "LKR", null, "Lunch split", null);

        // Missing PIN
        assertThatThrownBy(() -> transferService.initiateTransfer(
                "IDEM-KEY-2", null, requestNoPin, sender, "127.0.0.1"))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Step-up authentication required");

        // Wrong PIN
        when(passwordEncoder.matches("000000", HASHED_PIN)).thenReturn(false);
        InitiateTransferRequest requestWrongPin = new InitiateTransferRequest(
                2L, null, new BigDecimal("1500.00"), "LKR", null, "Lunch split", "000000");

        assertThatThrownBy(() -> transferService.initiateTransfer(
                "IDEM-KEY-2", null, requestWrongPin, sender, "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid PIN");
    }

    @Test
    @DisplayName("Insufficient funds: rejected with 422 UnprocessableEntityException")
    void testInsufficientFunds() {
        mockCommonDependencies();

        when(bankAdapter.checkTransferEligibility(any(TransferEligibilityRequest.class)))
                .thenReturn(AdapterResponse.failure("INSUFFICIENT_FUNDS", "Account balance is below the requested transfer amount", "INSUFFICIENT_FUNDS"));

        InitiateTransferRequest request = new InitiateTransferRequest(
                2L, null, new BigDecimal("10000.00"), "LKR", null, "Rent", RAW_PIN);

        assertThatThrownBy(() -> transferService.initiateTransfer(
                "IDEM-KEY-3", null, request, sender, "127.0.0.1"))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("Account balance is below the requested transfer amount");

        // Adapter initiateTransfer must not be called
        verify(bankAdapter, never()).initiateTransfer(any());
        // Daily spend must not be recorded
        verify(dailyLimitService, never()).recordSpend(any(), any());
    }

    @Test
    @DisplayName("Duplicate submission: same key + same payload replays cached result")
    void testIdempotentDuplicateSubmissionSamePayload() throws Exception {
        mockCommonDependencies();

        when(bankAdapter.checkTransferEligibility(any(TransferEligibilityRequest.class)))
                .thenReturn(AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE"));
        when(bankAdapter.initiateTransfer(any(TransferRequest.class)))
                .thenReturn(AdapterResponse.success("SIM-TX-9999", "SUCCESS"));

        InitiateTransferRequest request = new InitiateTransferRequest(
                2L, null, new BigDecimal("500.00"), "LKR", null, "Coffee", RAW_PIN);

        // First call
        TransferResponse firstResp = transferService.initiateTransfer(
                "IDEM-KEY-DUP", null, request, sender, "127.0.0.1");

        // Mock cached record found in repository for second call
        String payloadHash = idempotencyService.hashPayload(request);
        IdempotencyRecordModel cachedRecord = new IdempotencyRecordModel(
                1L, "IDEM-KEY-DUP", payloadHash, 100L,
                objectMapper.writeValueAsString(firstResp), 200, Instant.now());
        when(transactionStore.findIdempotencyRecord("IDEM-KEY-DUP"))
                .thenReturn(Optional.of(cachedRecord));

        // Second call with identical payload
        TransferResponse replayResp = transferService.initiateTransfer(
                "IDEM-KEY-DUP", null, request, sender, "127.0.0.1");

        assertThat(replayResp.transferId()).isEqualTo(firstResp.transferId());
        assertThat(replayResp.reference()).isEqualTo(firstResp.reference());
        assertThat(replayResp.status()).isEqualTo(TransactionStatus.COMPLETED);

        // Bank adapter should only have been called ONCE
        verify(bankAdapter, times(1)).initiateTransfer(any());
    }

    @Test
    @DisplayName("Duplicate submission: same key + different payload is rejected with 409 Conflict")
    void testIdempotentDuplicateSubmissionDifferentPayload() {
        when(passwordEncoder.matches(RAW_PIN, HASHED_PIN)).thenReturn(true);

        InitiateTransferRequest request1 = new InitiateTransferRequest(
                2L, null, new BigDecimal("500.00"), "LKR", null, "Coffee", RAW_PIN);
        String hash1 = idempotencyService.hashPayload(request1);

        IdempotencyRecordModel existingRecord = new IdempotencyRecordModel(
                1L, "IDEM-KEY-CONFLICT", hash1, 100L, "{}", 200, Instant.now());
        when(transactionStore.findIdempotencyRecord("IDEM-KEY-CONFLICT"))
                .thenReturn(Optional.of(existingRecord));

        // Attempt second call with different amount
        InitiateTransferRequest request2 = new InitiateTransferRequest(
                2L, null, new BigDecimal("1500.00"), "LKR", null, "Dinner", RAW_PIN);

        assertThatThrownBy(() -> transferService.initiateTransfer(
                "IDEM-KEY-CONFLICT", null, request2, sender, "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already used with a different request payload");
    }

    @Test
    @DisplayName("Timeout moves to REQUIRES_RECONCILIATION, never to FAILED; ReconciliationService resolves it")
    void testTimeoutMovedToReconciliationAndResolved() {
        mockCommonDependencies();

        when(bankAdapter.checkTransferEligibility(any(TransferEligibilityRequest.class)))
                .thenReturn(AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE"));
        // Bank adapter returns TIMEOUT
        when(bankAdapter.initiateTransfer(any(TransferRequest.class)))
                .thenReturn(AdapterResponse.timeout("SIM-TIMEOUT-REF", "TIMEOUT_SUCCEEDED"));

        InitiateTransferRequest request = new InitiateTransferRequest(
                2L, null, new BigDecimal("20000.00"), "LKR", null, "Timeout test", RAW_PIN);

        TransferResponse response = transferService.initiateTransfer(
                "IDEM-KEY-TIMEOUT", null, request, sender, "127.0.0.1");

        // INVARIANT: Response and DB status must be REQUIRES_RECONCILIATION, NEVER FAILED or COMPLETED!
        assertThat(response.status()).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);
        assertThat(response.status()).isNotEqualTo(TransactionStatus.FAILED);
        assertThat(response.status()).isNotEqualTo(TransactionStatus.COMPLETED);

        // Now test ReconciliationService resolving it
        TransactionRecord pendingRecord = new TransactionRecord(
                100L, response.reference(), "IDEM-KEY-TIMEOUT", sender.getId(), sender.getPhone(),
                sender.getFullName(), payee.getId(), payee.getPhone(), payee.getFullName(),
                "SIMULATED_BANK_A", "SIMULATED_BANK_B", new BigDecimal("20000.00"), "LKR",
                TransactionStatus.REQUIRES_RECONCILIATION, null, null, "SIM-TIMEOUT-REF",
                null, true, TransferInitiatedVia.APP, null, 1L, Instant.now(), Instant.now());

        when(transactionStore.findTransferById(100L)).thenReturn(Optional.of(pendingRecord));
        // Status query reveals the bank actually succeeded
        when(bankAdapter.queryTransferStatus(any(TransferStatusRequest.class)))
                .thenReturn(AdapterResponse.success("SIM-TIMEOUT-REF", "TIMEOUT_SUCCEEDED"));

        ReconcileResultResponse reconResult = reconciliationService.reconcileTransfer(100L);

        assertThat(reconResult.resolved()).isTrue();
        assertThat(reconResult.resolvedStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Delayed confirmation: PENDING response moves to REQUIRES_RECONCILIATION, resolved on query")
    void testDelayedConfirmation() {
        mockCommonDependencies();

        when(bankAdapter.checkTransferEligibility(any(TransferEligibilityRequest.class)))
                .thenReturn(AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE"));
        // Bank returns PENDING
        when(bankAdapter.initiateTransfer(any(TransferRequest.class)))
                .thenReturn(AdapterResponse.pending("SIM-PENDING-REF", "DELAYED_CONFIRMATION"));

        InitiateTransferRequest request = new InitiateTransferRequest(
                2L, null, new BigDecimal("30000.00"), "LKR", null, "Delayed test", RAW_PIN);

        TransferResponse response = transferService.initiateTransfer(
                "IDEM-KEY-DELAYED", null, request, sender, "127.0.0.1");

        assertThat(response.status()).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);

        // Reconciliation
        TransactionRecord pendingRecord = new TransactionRecord(
                101L, response.reference(), "IDEM-KEY-DELAYED", sender.getId(), sender.getPhone(),
                sender.getFullName(), payee.getId(), payee.getPhone(), payee.getFullName(),
                "SIMULATED_BANK_A", "SIMULATED_BANK_B", new BigDecimal("30000.00"), "LKR",
                TransactionStatus.REQUIRES_RECONCILIATION, null, null, "SIM-PENDING-REF",
                null, true, TransferInitiatedVia.APP, null, 1L, Instant.now(), Instant.now());

        when(transactionStore.findTransferById(101L)).thenReturn(Optional.of(pendingRecord));
        when(bankAdapter.queryTransferStatus(any(TransferStatusRequest.class)))
                .thenReturn(AdapterResponse.success("SIM-PENDING-REF", "DELAYED_CONFIRMATION"));

        ReconcileResultResponse reconResult = reconciliationService.reconcileTransfer(101L);
        assertThat(reconResult.resolvedStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Reversal flow: COMPLETED -> REVERSAL_PENDING -> REVERSED")
    void testReversalWorkflow() {
        TransactionRecord completedRecord = new TransactionRecord(
                105L, "ref-rev-1", "idem-rev-1", sender.getId(), sender.getPhone(),
                sender.getFullName(), payee.getId(), payee.getPhone(), payee.getFullName(),
                "SIMULATED_BANK_A", "SIMULATED_BANK_B", new BigDecimal("50000.00"), "LKR",
                TransactionStatus.COMPLETED, null, "bank-tx-1", "prov-ref-1",
                null, true, TransferInitiatedVia.APP, null, 1L, Instant.now(), Instant.now());

        TransactionRecord revPendingRecord = new TransactionRecord(
                105L, "ref-rev-1", "idem-rev-1", sender.getId(), sender.getPhone(),
                sender.getFullName(), payee.getId(), payee.getPhone(), payee.getFullName(),
                "SIMULATED_BANK_A", "SIMULATED_BANK_B", new BigDecimal("50000.00"), "LKR",
                TransactionStatus.REVERSAL_PENDING, null, "bank-tx-1", "prov-ref-1",
                null, true, TransferInitiatedVia.APP, null, 2L, Instant.now(), Instant.now());

        when(transactionStore.findTransferById(105L))
                .thenReturn(Optional.of(completedRecord))
                .thenReturn(Optional.of(revPendingRecord));

        // 1. Request reversal (transitions COMPLETED -> REVERSAL_PENDING)
        Transfer revPending = reversalService.requestReversal(105L, 1L, "Customer dispute", "127.0.0.1");
        assertThat(revPending.getStatus()).isEqualTo(TransactionStatus.REVERSAL_PENDING);

        // 2. Confirm reversal (transitions REVERSAL_PENDING -> REVERSED)
        Transfer reversed = reversalService.confirmReversal(105L, "SIM-REV-CONFIRM", "127.0.0.1");
        assertThat(reversed.getStatus()).isEqualTo(TransactionStatus.REVERSED);
    }

    @Test
    @DisplayName("Object-level security: third party cannot access GET /transfers/{id}")
    void testObjectLevelSecurity() {
        TransactionRecord record = new TransactionRecord(
                110L, "ref-sec-1", "idem-sec-1", sender.getId(), sender.getPhone(),
                sender.getFullName(), payee.getId(), payee.getPhone(), payee.getFullName(),
                "SIMULATED_BANK_A", "SIMULATED_BANK_B", new BigDecimal("100.00"), "LKR",
                TransactionStatus.COMPLETED, null, "bank-tx-1", "prov-ref-1",
                null, true, TransferInitiatedVia.APP, null, 1L, Instant.now(), Instant.now());

        when(transactionStore.findTransferById(110L)).thenReturn(Optional.of(record));

        // Requester id 99L is neither sender (1L) nor receiver (2L)
        assertThatThrownBy(() -> transferService.getTransferById(110L, 99L))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("You do not have permission");

        // Sender can access
        TransferResponse senderView = transferService.getTransferById(110L, 1L);
        assertThat(senderView.transferId()).isEqualTo(110L);

        // Receiver can access
        TransferResponse receiverView = transferService.getTransferById(110L, 2L);
        assertThat(receiverView.transferId()).isEqualTo(110L);
    }
}

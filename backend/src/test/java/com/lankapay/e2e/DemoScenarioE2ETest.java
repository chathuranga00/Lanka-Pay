package com.lankapay.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.*;
import com.lankapay.bank.adapter.simulated.*;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.registry.domain.PhoneIdentity;
import com.lankapay.registry.domain.PhoneIdentityStore;
import com.lankapay.registry.domain.RegistryAccountRecord;
import com.lankapay.registry.service.LookupRateLimiter;
import com.lankapay.registry.service.LookupService;
import com.lankapay.registry.web.LookupResponse;
import com.lankapay.transfer.domain.*;
import com.lankapay.transfer.service.*;
import com.lankapay.transfer.web.dto.InitiateTransferRequest;
import com.lankapay.transfer.web.dto.QuoteRequest;
import com.lankapay.transfer.web.dto.QuoteResponse;
import com.lankapay.transfer.web.dto.ReconcileResultResponse;
import com.lankapay.transfer.web.dto.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-End API Scenario Test following Section 24 demo specification:
 *
 * 1. Kasun has 3 linked accounts (Banks A, B, C; default is Bank B).
 * 2. Nimal has 1 linked account (Bank A).
 * 3. Kasun searches for Nimal via Payment Identity Registry lookup.
 * 4. Kasun requests a transfer quote (fee, total, rate lock).
 * 5. Kasun initiates transfer of 500.00 LKR (happy path debits default Bank B, credits Bank A, status COMPLETED).
 * 6. Idempotency protection prevents double-charge on replayed Idempotency-Key.
 * 7. Failure scenario (insufficient funds magic amount 10000.00 LKR -> FAILED).
 * 8. Timeout scenario (delayed confirmation magic amount 30000.00 LKR -> REQUIRES_RECONCILIATION, reconciled to COMPLETED).
 * 9. Reversal scenario (reversal executed on completed transfer -> REVERSED).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DemoScenarioE2ETest {

    @Mock TransactionStore transactionStore;
    @Mock LinkedAccountStore linkedAccountStore;
    @Mock UserStore userStore;
    @Mock PhoneIdentityStore phoneIdentityStore;
    @Mock AuditService auditService;

    private PasswordEncoder passwordEncoder;
    private ObjectMapper objectMapper;
    private SimulatedAccountStore simulatedAccountStore;
    private BankAdapterRegistry bankAdapterRegistry;
    private TransferProcessor transferProcessor;
    private DailyLimitService dailyLimitService;
    private IdempotencyService idempotencyService;
    private TransferService transferService;
    private ReconciliationService reconciliationService;
    private ReversalService reversalService;
    private LookupService lookupService;

    // Test Personas
    private User kasun;
    private User nimal;
    private LinkedAccount kasunAccountA;
    private LinkedAccount kasunAccountB;
    private LinkedAccount kasunAccountC;
    private LinkedAccount nimalAccountA;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Real in-memory simulated bank store & adapters
        simulatedAccountStore = new SimulatedAccountStore();
        List<BankAdapter> adapters = List.of(
                new SimulatedBankAdapterA(simulatedAccountStore),
                new SimulatedBankAdapterB(simulatedAccountStore),
                new SimulatedBankAdapterC(simulatedAccountStore)
        );
        bankAdapterRegistry = new BankAdapterRegistry(adapters);

        dailyLimitService = new DailyLimitService(transactionStore);
        transferProcessor = new TransferProcessor(
                transactionStore, bankAdapterRegistry, dailyLimitService, auditService, null);
        idempotencyService = new IdempotencyService(transactionStore, objectMapper);
        reconciliationService = new ReconciliationService(
                transactionStore, linkedAccountStore, bankAdapterRegistry, dailyLimitService, auditService);
        reversalService = new ReversalService(transactionStore, auditService);

        transferService = new TransferService(
                transactionStore, userStore, linkedAccountStore,
                transferProcessor, dailyLimitService, idempotencyService, passwordEncoder, null);

        lookupService = new LookupService(
                phoneIdentityStore,
                new LookupRateLimiter(100, 100), auditService, 50);

        // 1. Kasun Perera [TEST]
        kasun = new User("+94771000001", "Kasun Perera [TEST]", "199010000001", passwordEncoder.encode("123456"));
        kasun.setStatus(UserStatus.ACTIVE);
        kasun.setRole(UserRole.CUSTOMER);
        ReflectionTestUtils.setField(kasun, "id", 101L);

        // Kasun's 3 Accounts (Default is Bank B)
        kasunAccountA = new LinkedAccount(
                201L, 101L, "SIMULATED_BANK_A", "hashA", "****1001", "Kasun Perera [TEST]",
                "Savings Account A [TEST]", true, true, "encRefA",
                BankAccountStatus.ACTIVE, false, Instant.now(), Instant.now());

        kasunAccountB = new LinkedAccount(
                202L, 101L, "SIMULATED_BANK_B", "hashB", "****1001", "Kasun Perera [TEST]",
                "Salary Account B [TEST]", true, true, "encRefB",
                BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now()); // Default Bank B

        kasunAccountC = new LinkedAccount(
                203L, 101L, "SIMULATED_BANK_C", "hashC", "****1001", "Kasun Perera [TEST]",
                "Investment Account C [TEST]", true, true, "encRefC",
                BankAccountStatus.ACTIVE, false, Instant.now(), Instant.now());

        // 2. Nimal Silva [TEST]
        nimal = new User("+94771000002", "Nimal Silva [TEST]", "199010000002", passwordEncoder.encode("123456"));
        nimal.setStatus(UserStatus.ACTIVE);
        nimal.setRole(UserRole.CUSTOMER);
        ReflectionTestUtils.setField(nimal, "id", 102L);

        // Nimal's 1 Account (Bank A)
        nimalAccountA = new LinkedAccount(
                204L, 102L, "SIMULATED_BANK_A", "hashNimalA", "****1002", "Nimal Silva [TEST]",
                "Primary Account [TEST]", true, true, "encRefNimalA",
                BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("Demo Step 1: Verify Kasun has 3 accounts (Banks A/B/C, default B) and Nimal has 1 account")
    void testDemoStep1_verifySeededPersonasAndAccounts() {
        assertThat(kasun.getFullName()).contains("[TEST]");
        assertThat(kasunAccountB.isDefault()).isTrue();
        assertThat(kasunAccountB.bankCode()).isEqualTo("SIMULATED_BANK_B");

        assertThat(kasunAccountA.isDefault()).isFalse();
        assertThat(kasunAccountA.bankCode()).isEqualTo("SIMULATED_BANK_A");

        assertThat(kasunAccountC.isDefault()).isFalse();
        assertThat(kasunAccountC.bankCode()).isEqualTo("SIMULATED_BANK_C");

        assertThat(nimal.getFullName()).contains("[TEST]");
        assertThat(nimalAccountA.isDefault()).isTrue();
        assertThat(nimalAccountA.bankCode()).isEqualTo("SIMULATED_BANK_A");
    }

    @Test
    @DisplayName("Demo Step 2: Kasun searches for Nimal in registry and verifies payee identity")
    void testDemoStep2_registryLookupForPayee() {
        PhoneIdentity nimalIdentity = new PhoneIdentity(1L, 102L, "+94771000002", true, "Nimal Silva [TEST]", Instant.now());
        when(phoneIdentityStore.findByPhone("+94771000002")).thenReturn(Optional.of(nimalIdentity));

        RegistryAccountRecord nimalAcc = new RegistryAccountRecord(
                204L, "SIMULATED_BANK_A", "Simulated Bank A [SIMULATED]", "****1002", true, true, true, "ACTIVE");
        when(phoneIdentityStore.findAccountsInRegistry("+94771000002")).thenReturn(List.of(nimalAcc));

        LookupResponse lookupResponse = lookupService.lookup("+94771000002", 101L, "127.0.0.1");

        assertThat(lookupResponse.resultType().name()).isEqualTo("FOUND");
        assertThat(lookupResponse.displayName()).startsWith("Nimal "); // Abbreviated name protection
        assertThat(lookupResponse.accounts()).hasSize(1);
        assertThat(lookupResponse.accounts().get(0).institutionCode()).isEqualTo("SIMULATED_BANK_A");
    }

    @Test
    @DisplayName("Demo Step 3 & 4: Happy path transfer from Kasun (Bank B) to Nimal (Bank A)")
    void testDemoStep3And4_transferQuoteAndExecutionHappyPath() {
        when(userStore.findUserById(102L)).thenReturn(Optional.of(UserAccount.fromUser(nimal)));
        when(userStore.findUserByPhone("+94771000002")).thenReturn(Optional.of(UserAccount.fromUser(nimal)));
        when(linkedAccountStore.findActiveDefaultAccount(101L)).thenReturn(Optional.of(kasunAccountB));
        when(linkedAccountStore.findActiveDefaultAccount(102L)).thenReturn(Optional.of(nimalAccountA));
        when(transactionStore.findDailyLimit(any(), any())).thenReturn(Optional.empty());

        // 3. Transfer Quote
        QuoteResponse quote = transferService.getQuote(
                new QuoteRequest(102L, null, new BigDecimal("500.00"), "LKR"), kasun);
        assertThat(quote.fee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.totalAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(quote.quoteReference()).startsWith("QUO-");

        // 4. Initiate Transfer
        when(transactionStore.createTransferWithIdempotency(any(TransactionRecord.class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    TransactionRecord r = invocation.getArgument(0);
                    return new TransactionRecord(
                            501L, r.reference(), r.idempotencyKey(),
                            r.senderId(), r.senderPhone(), r.senderFullName(),
                            r.receiverId(), r.receiverPhone(), r.receiverFullName(),
                            r.senderBankCode(), r.receiverBankCode(),
                            r.amount(), r.currency(), TransactionStatus.PENDING,
                            null, null, null, r.note(), true, r.initiatedVia(),
                            null, 1L, Instant.now(), Instant.now()
                    );
                });

        when(transactionStore.transitionTransferStatus(eq(501L), any(), any(), any(), any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    TransactionStatus status = invocation.getArgument(1);
                    String bankTxRef = invocation.getArgument(4);
                    String provRef = invocation.getArgument(5);
                    return new TransactionRecord(
                            501L, "ref-501", "key-501",
                            101L, "+94771000001", "Kasun Perera [TEST]",
                            102L, "+94771000002", "Nimal Silva [TEST]",
                            "SIMULATED_BANK_B", "SIMULATED_BANK_A",
                            new BigDecimal("500.00"), "LKR", status,
                            null, bankTxRef != null ? bankTxRef : "SIM-TX-501", provRef, "Dinner split [TEST]", true, TransferInitiatedVia.APP,
                            null, 2L, Instant.now(), Instant.now()
                    );
                });

        InitiateTransferRequest request = new InitiateTransferRequest(
                102L, null, new BigDecimal("500.00"), "LKR",
                quote.quoteReference(), "Dinner split [TEST]", "123456");

        String idempotencyKey = UUID.randomUUID().toString();
        TransferResponse response = transferService.initiateTransfer(
                idempotencyKey, "123456", request, kasun, "127.0.0.1");

        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.amount()).isEqualByComparingTo("500.00");
        assertThat(response.simulated()).isTrue();
    }

    @Test
    @DisplayName("Demo Step 5: Idempotency protection prevents duplicate transfers")
    void testDemoStep5_idempotencyReplayProtection() {
        when(userStore.findUserById(102L)).thenReturn(Optional.of(UserAccount.fromUser(nimal)));
        when(userStore.findUserByPhone("+94771000002")).thenReturn(Optional.of(UserAccount.fromUser(nimal)));
        when(linkedAccountStore.findActiveDefaultAccount(101L)).thenReturn(Optional.of(kasunAccountB));
        when(linkedAccountStore.findActiveDefaultAccount(102L)).thenReturn(Optional.of(nimalAccountA));
        when(transactionStore.findDailyLimit(any(), any())).thenReturn(Optional.empty());

        when(transactionStore.createTransferWithIdempotency(any(TransactionRecord.class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    TransactionRecord r = invocation.getArgument(0);
                    return new TransactionRecord(
                            601L, r.reference(), r.idempotencyKey(),
                            r.senderId(), r.senderPhone(), r.senderFullName(),
                            r.receiverId(), r.receiverPhone(), r.receiverFullName(),
                            r.senderBankCode(), r.receiverBankCode(),
                            r.amount(), r.currency(), TransactionStatus.PENDING,
                            null, null, null, r.note(), true, r.initiatedVia(),
                            null, 1L, Instant.now(), Instant.now()
                    );
                });

        when(transactionStore.transitionTransferStatus(eq(601L), any(), any(), any(), any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    TransactionStatus status = invocation.getArgument(1);
                    String bankTxRef = invocation.getArgument(4);
                    String provRef = invocation.getArgument(5);
                    return new TransactionRecord(
                            601L, "ref-601", "key-601",
                            101L, "+94771000001", "Kasun Perera [TEST]",
                            102L, "+94771000002", "Nimal Silva [TEST]",
                            "SIMULATED_BANK_B", "SIMULATED_BANK_A",
                            new BigDecimal("750.00"), "LKR", status,
                            null, bankTxRef != null ? bankTxRef : "SIM-TX-601", provRef, "Rent contribution [TEST]", true, TransferInitiatedVia.APP,
                            null, 2L, Instant.now(), Instant.now()
                    );
                });

        String sharedKey = "IDEM-KEY-DEMO-REPEAT";
        InitiateTransferRequest request = new InitiateTransferRequest(
                102L, null, new BigDecimal("750.00"), "LKR",
                null, "Rent contribution [TEST]", "123456");

        // First attempt -> processes and caches
        TransferResponse firstResp = transferService.initiateTransfer(
                sharedKey, "123456", request, kasun, "127.0.0.1");
        assertThat(firstResp.status()).isEqualTo(TransactionStatus.COMPLETED);

        // Simulate IdempotencyRecordModel found in store
        String payloadHash = idempotencyService.hashPayload(request);
        IdempotencyRecordModel cachedRecord = new IdempotencyRecordModel(
                1L, sharedKey, payloadHash, 601L, objectMapper.valueToTree(firstResp).toString(), 200, Instant.now());
        when(transactionStore.findIdempotencyRecord(sharedKey)).thenReturn(Optional.of(cachedRecord));

        // Replay attempt with same key and payload -> returns cached response directly
        TransferResponse replayResp = transferService.initiateTransfer(
                sharedKey, "123456", request, kasun, "127.0.0.1");

        assertThat(replayResp.transferId()).isEqualTo(firstResp.transferId());
        assertThat(replayResp.reference()).isEqualTo(firstResp.reference());
        assertThat(replayResp.status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Demo Step 6: Failure scenario triggers FAILED transaction outcome")
    void testDemoStep6_failureScenarioInsufficientFunds() {
        when(userStore.findUserById(102L)).thenReturn(Optional.of(UserAccount.fromUser(nimal)));
        when(userStore.findUserByPhone("+94771000002")).thenReturn(Optional.of(UserAccount.fromUser(nimal)));
        when(linkedAccountStore.findActiveDefaultAccount(101L)).thenReturn(Optional.of(kasunAccountB));
        when(linkedAccountStore.findActiveDefaultAccount(102L)).thenReturn(Optional.of(nimalAccountA));
        when(transactionStore.findDailyLimit(any(), any())).thenReturn(Optional.empty());

        when(transactionStore.createTransferWithIdempotency(any(TransactionRecord.class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    TransactionRecord r = invocation.getArgument(0);
                    return new TransactionRecord(
                            602L, r.reference(), r.idempotencyKey(),
                            r.senderId(), r.senderPhone(), r.senderFullName(),
                            r.receiverId(), r.receiverPhone(), r.receiverFullName(),
                            r.senderBankCode(), r.receiverBankCode(),
                            r.amount(), r.currency(), TransactionStatus.PENDING,
                            null, null, null, r.note(), true, r.initiatedVia(),
                            null, 1L, Instant.now(), Instant.now()
                    );
                });

        when(transactionStore.transitionTransferStatus(eq(602L), any(), any(), any(), any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    TransactionStatus status = invocation.getArgument(1);
                    return new TransactionRecord(
                            602L, "ref-602", "key-602",
                            101L, "+94771000001", "Kasun Perera [TEST]",
                            102L, "+94771000002", "Nimal Silva [TEST]",
                            "SIMULATED_BANK_B", "SIMULATED_BANK_A",
                            new BigDecimal("10000.00"), "LKR", status,
                            "INSUFFICIENT_FUNDS", null, null, "High value test [TEST]", true, TransferInitiatedVia.APP,
                            null, 2L, Instant.now(), Instant.now()
                    );
                });

        // Magic amount 10000.00 LKR triggers INSUFFICIENT_FUNDS in SimulatedBankAdapter
        InitiateTransferRequest request = new InitiateTransferRequest(
                102L, null, new BigDecimal("10000.00"), "LKR",
                null, "High value test [TEST]", "123456");

        TransferResponse failResp = transferService.initiateTransfer(
                UUID.randomUUID().toString(), "123456", request, kasun, "127.0.0.1");

        assertThat(failResp.status()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    @DisplayName("Demo Step 7: Timeout / delayed confirmation scenario reconciles to COMPLETED")
    void testDemoStep7_timeoutAndReconciliationScenario() {
        when(userStore.findUserById(102L)).thenReturn(Optional.of(UserAccount.fromUser(nimal)));
        when(userStore.findUserByPhone("+94771000002")).thenReturn(Optional.of(UserAccount.fromUser(nimal)));
        when(linkedAccountStore.findActiveDefaultAccount(101L)).thenReturn(Optional.of(kasunAccountB));
        when(linkedAccountStore.findActiveDefaultAccount(102L)).thenReturn(Optional.of(nimalAccountA));
        when(transactionStore.findDailyLimit(any(), any())).thenReturn(Optional.empty());

        when(transactionStore.createTransferWithIdempotency(any(TransactionRecord.class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    TransactionRecord r = invocation.getArgument(0);
                    return new TransactionRecord(
                            701L, r.reference(), r.idempotencyKey(),
                            r.senderId(), r.senderPhone(), r.senderFullName(),
                            r.receiverId(), r.receiverPhone(), r.receiverFullName(),
                            r.senderBankCode(), r.receiverBankCode(),
                            r.amount(), r.currency(), TransactionStatus.PENDING,
                            null, null, null, r.note(), true, r.initiatedVia(),
                            null, 1L, Instant.now(), Instant.now()
                    );
                });

        when(transactionStore.transitionTransferStatus(eq(701L), any(), any(), any(), any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    TransactionStatus status = invocation.getArgument(1);
                    return new TransactionRecord(
                            701L, "ref-701", "key-701",
                            101L, "+94771000001", "Kasun Perera [TEST]",
                            102L, "+94771000002", "Nimal Silva [TEST]",
                            "SIMULATED_BANK_B", "SIMULATED_BANK_A",
                            new BigDecimal("30000.00"), "LKR", status,
                            null, null, "SIM-PROV-701", "Delayed confirmation [TEST]", true, TransferInitiatedVia.APP,
                            null, 2L, Instant.now(), Instant.now()
                    );
                });

        InitiateTransferRequest request = new InitiateTransferRequest(
                102L, null, new BigDecimal("30000.00"), "LKR",
                null, "Delayed confirmation [TEST]", "123456");

        TransferResponse initialResp = transferService.initiateTransfer(
                UUID.randomUUID().toString(), "123456", request, kasun, "127.0.0.1");

        assertThat(initialResp.status()).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);

        // Reconciliation action by Admin/system
        TransactionRecord pendingRecord = new TransactionRecord(
                701L, "ref-701", "key-701",
                101L, "+94771000001", "Kasun Perera [TEST]",
                102L, "+94771000002", "Nimal Silva [TEST]",
                "SIMULATED_BANK_B", "SIMULATED_BANK_A",
                new BigDecimal("30000.00"), "LKR", TransactionStatus.REQUIRES_RECONCILIATION,
                null, null, "SIM-PROV-701", "Delayed confirmation [TEST]", true, TransferInitiatedVia.APP,
                null, 2L, Instant.now(), Instant.now()
        );
        when(transactionStore.findTransferById(701L)).thenReturn(Optional.of(pendingRecord));
        ReconcileResultResponse reconcileResp = reconciliationService.reconcileTransfer(701L);

        assertThat(reconcileResp.resolvedStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(reconcileResp.transferId()).isEqualTo(701L);
    }

    @Test
    @DisplayName("Demo Step 8: Reversal flow transitions completed transfer to REVERSED")
    void testDemoStep8_transferReversalFlow() {
        TransactionRecord completedRecord = new TransactionRecord(
                801L, "ref-801", "key-801",
                101L, "+94771000001", "Kasun Perera [TEST]",
                102L, "+94771000002", "Nimal Silva [TEST]",
                "SIMULATED_BANK_B", "SIMULATED_BANK_A",
                new BigDecimal("1200.00"), "LKR", TransactionStatus.COMPLETED,
                null, "BANK-TX-OK", "SIM-PROV-OK", "Disputed payment [TEST]", true, TransferInitiatedVia.APP,
                null, 1L, Instant.now(), Instant.now()
        );

        when(transactionStore.findTransferById(801L)).thenReturn(Optional.of(completedRecord));

        when(transactionStore.transitionTransferStatus(eq(801L), any(), any(), any(), any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    TransactionStatus status = invocation.getArgument(1);
                    return new TransactionRecord(
                            801L, "ref-801", "key-801",
                            101L, "+94771000001", "Kasun Perera [TEST]",
                            102L, "+94771000002", "Nimal Silva [TEST]",
                            "SIMULATED_BANK_B", "SIMULATED_BANK_A",
                            new BigDecimal("1200.00"), "LKR", status,
                            null, "BANK-TX-OK", "SIM-PROV-OK", "Disputed payment [TEST]", true, TransferInitiatedVia.APP,
                            null, 2L, Instant.now(), Instant.now()
                    );
                });

        // 1. Request reversal (sender requests reversal)
        Transfer pendingReversal = reversalService.requestReversal(801L, 101L, "Disputed payment [TEST]", "127.0.0.1");
        assertThat(pendingReversal.getStatus()).isEqualTo(TransactionStatus.REVERSAL_PENDING);

        // Update mock for confirmReversal
        TransactionRecord pendingReversalRecord = new TransactionRecord(
                801L, "ref-801", "key-801",
                101L, "+94771000001", "Kasun Perera [TEST]",
                102L, "+94771000002", "Nimal Silva [TEST]",
                "SIMULATED_BANK_B", "SIMULATED_BANK_A",
                new BigDecimal("1200.00"), "LKR", TransactionStatus.REVERSAL_PENDING,
                null, "BANK-TX-OK", "SIM-PROV-OK", "Disputed payment [TEST]", true, TransferInitiatedVia.APP,
                null, 2L, Instant.now(), Instant.now()
        );
        when(transactionStore.findTransferById(801L)).thenReturn(Optional.of(pendingReversalRecord));

        // 2. Confirm reversal (bank adapter confirms reversal)
        Transfer confirmedReversed = reversalService.confirmReversal(801L, "SIM-REV-CONFIRM-999", "127.0.0.1");
        assertThat(confirmedReversed.getStatus()).isEqualTo(TransactionStatus.REVERSED);
    }
}

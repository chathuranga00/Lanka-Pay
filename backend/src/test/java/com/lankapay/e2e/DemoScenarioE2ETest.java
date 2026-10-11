package com.lankapay.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.*;
import com.lankapay.bank.adapter.simulated.*;
import com.lankapay.bank.crypto.AccountReferenceEncryptor;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.BankInstitution;
import com.lankapay.bank.repository.BankAccountRepository;
import com.lankapay.bank.repository.BankInstitutionRepository;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.registry.domain.Registry;
import com.lankapay.registry.repository.LookupAbuseCounterRepository;
import com.lankapay.registry.repository.RegistryRepository;
import com.lankapay.registry.service.LookupRateLimiter;
import com.lankapay.registry.service.LookupService;
import com.lankapay.registry.web.LookupResponse;
import com.lankapay.transfer.domain.*;
import com.lankapay.transfer.repository.DailyLimitRepository;
import com.lankapay.transfer.repository.IdempotencyRecordRepository;
import com.lankapay.transfer.repository.TransactionEventRepository;
import com.lankapay.transfer.repository.TransferRepository;
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
import static org.mockito.ArgumentMatchers.any;
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

    @Mock TransferRepository transferRepository;
    @Mock TransactionEventRepository transactionEventRepository;
    @Mock BankAccountRepository bankAccountRepository;
    @Mock BankInstitutionRepository institutionRepository;
    @Mock UserRepository userRepository;
    @Mock RegistryRepository registryRepository;
    @Mock DailyLimitRepository dailyLimitRepository;
    @Mock IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock LookupAbuseCounterRepository abuseCounterRepository;
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
    private BankAccount kasunAccountA;
    private BankAccount kasunAccountB;
    private BankAccount kasunAccountC;
    private BankAccount nimalAccountA;

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

        dailyLimitService = new DailyLimitService(dailyLimitRepository);
        transferProcessor = new TransferProcessor(
                transferRepository, transactionEventRepository, bankAdapterRegistry, dailyLimitService, auditService);
        idempotencyService = new IdempotencyService(idempotencyRecordRepository, objectMapper);
        reconciliationService = new ReconciliationService(
                transferRepository, transactionEventRepository, bankAccountRepository, bankAdapterRegistry, dailyLimitService, auditService);
        reversalService = new ReversalService(
                transferRepository, transactionEventRepository, auditService);

        transferService = new TransferService(
                transferRepository, bankAccountRepository, userRepository,
                transferProcessor, dailyLimitService, idempotencyService, passwordEncoder);

        lookupService = new LookupService(
                registryRepository, bankAccountRepository, institutionRepository, abuseCounterRepository,
                new LookupRateLimiter(100, 100), auditService, 50);

        // 1. Kasun Perera [TEST]
        kasun = new User("+94771000001", "Kasun Perera [TEST]", "199010000001", passwordEncoder.encode("123456"));
        kasun.setStatus(UserStatus.ACTIVE);
        kasun.setRole(UserRole.CUSTOMER);
        ReflectionTestUtils.setField(kasun, "id", 101L);

        // Kasun's 3 Accounts (Default is Bank B)
        kasunAccountA = new BankAccount(kasun, "SIMULATED_BANK_A", "hashA", "****1001", "Kasun Perera [TEST]", "encRefA");
        kasunAccountA.setLabel("Savings Account A [TEST]");
        kasunAccountA.setDefault(false);
        kasunAccountA.setStatus(BankAccountStatus.ACTIVE);
        ReflectionTestUtils.setField(kasunAccountA, "id", 201L);

        kasunAccountB = new BankAccount(kasun, "SIMULATED_BANK_B", "hashB", "****1001", "Kasun Perera [TEST]", "encRefB");
        kasunAccountB.setLabel("Salary Account B [TEST]");
        kasunAccountB.setDefault(true); // Default Bank B
        kasunAccountB.setStatus(BankAccountStatus.ACTIVE);
        ReflectionTestUtils.setField(kasunAccountB, "id", 202L);

        kasunAccountC = new BankAccount(kasun, "SIMULATED_BANK_C", "hashC", "****1001", "Kasun Perera [TEST]", "encRefC");
        kasunAccountC.setLabel("Investment Account C [TEST]");
        kasunAccountC.setDefault(false);
        kasunAccountC.setStatus(BankAccountStatus.ACTIVE);
        ReflectionTestUtils.setField(kasunAccountC, "id", 203L);

        // 2. Nimal Silva [TEST]
        nimal = new User("+94771000002", "Nimal Silva [TEST]", "199010000002", passwordEncoder.encode("123456"));
        nimal.setStatus(UserStatus.ACTIVE);
        nimal.setRole(UserRole.CUSTOMER);
        ReflectionTestUtils.setField(nimal, "id", 102L);

        // Nimal's 1 Account (Bank A)
        nimalAccountA = new BankAccount(nimal, "SIMULATED_BANK_A", "hashNimalA", "****1002", "Nimal Silva [TEST]", "encRefNimalA");
        nimalAccountA.setLabel("Primary Account [TEST]");
        nimalAccountA.setDefault(true);
        nimalAccountA.setStatus(BankAccountStatus.ACTIVE);
        ReflectionTestUtils.setField(nimalAccountA, "id", 204L);
    }

    @Test
    @DisplayName("Demo Step 1: Verify Kasun has 3 accounts (Banks A/B/C, default B) and Nimal has 1 account")
    void testDemoStep1_verifySeededPersonasAndAccounts() {
        assertThat(kasun.getFullName()).contains("[TEST]");
        assertThat(kasunAccountB.isDefault()).isTrue();
        assertThat(kasunAccountB.getBankCode()).isEqualTo("SIMULATED_BANK_B");

        assertThat(kasunAccountA.isDefault()).isFalse();
        assertThat(kasunAccountA.getBankCode()).isEqualTo("SIMULATED_BANK_A");

        assertThat(kasunAccountC.isDefault()).isFalse();
        assertThat(kasunAccountC.getBankCode()).isEqualTo("SIMULATED_BANK_C");

        assertThat(nimal.getFullName()).contains("[TEST]");
        assertThat(nimalAccountA.isDefault()).isTrue();
        assertThat(nimalAccountA.getBankCode()).isEqualTo("SIMULATED_BANK_A");
    }

    @Test
    @DisplayName("Demo Step 2: Kasun searches for Nimal in registry and verifies payee identity")
    void testDemoStep2_registryLookupForPayee() {
        Registry nimalRegistry = new Registry(nimal, "+94771000002");
        when(registryRepository.findByUserId_Phone("+94771000002")).thenReturn(Optional.of(nimalRegistry));
        when(bankAccountRepository.findByUserIdAndStatus(102L, BankAccountStatus.ACTIVE))
                .thenReturn(List.of(nimalAccountA));
        when(abuseCounterRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BankInstitution instA = new BankInstitution("SIMULATED_BANK_A", "Simulated Bank A [SIMULATED]", true, true);
        when(institutionRepository.findByBankCodeAndActiveTrue("SIMULATED_BANK_A")).thenReturn(Optional.of(instA));

        LookupResponse lookupResponse = lookupService.lookup("+94771000002", 101L, "127.0.0.1");

        assertThat(lookupResponse.resultType().name()).isEqualTo("FOUND");
        assertThat(lookupResponse.displayName()).startsWith("Nimal "); // Abbreviated name protection
        assertThat(lookupResponse.accounts()).hasSize(1);
        assertThat(lookupResponse.accounts().get(0).institutionCode()).isEqualTo("SIMULATED_BANK_A");
    }

    @Test
    @DisplayName("Demo Step 3 & 4: Happy path transfer from Kasun (Bank B) to Nimal (Bank A)")
    void testDemoStep3And4_transferQuoteAndExecutionHappyPath() {
        when(userRepository.findById(102L)).thenReturn(Optional.of(nimal));
        when(userRepository.findByPhone("+94771000002")).thenReturn(Optional.of(nimal));
        when(bankAccountRepository.findByUserIdAndIsDefaultTrueAndStatus(101L, BankAccountStatus.ACTIVE))
                .thenReturn(Optional.of(kasunAccountB)); // Auto-picks default Bank B
        when(bankAccountRepository.findByUserIdAndIsDefaultTrueAndStatus(102L, BankAccountStatus.ACTIVE))
                .thenReturn(Optional.of(nimalAccountA)); // Auto-picks Nimal's Bank A

        // 3. Transfer Quote
        QuoteResponse quote = transferService.getQuote(
                new QuoteRequest(102L, null, new BigDecimal("500.00"), "LKR"), kasun);
        assertThat(quote.fee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.totalAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(quote.quoteReference()).startsWith("QUO-");

        // 4. Initiate Transfer
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", 501L);
            }
            return t;
        });

        InitiateTransferRequest request = new InitiateTransferRequest(
                102L, null, new BigDecimal("500.00"), "LKR",
                quote.quoteReference(), "Dinner split [TEST]", "123456");

        String idempotencyKey = UUID.randomUUID().toString();
        TransferResponse response = transferService.initiateTransfer(
                idempotencyKey, null, request, kasun, "127.0.0.1");

        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.amount()).isEqualByComparingTo("500.00");
        assertThat(response.simulated()).isTrue();
    }

    @Test
    @DisplayName("Demo Step 5: Idempotency protection prevents duplicate transfers")
    void testDemoStep5_idempotencyReplayProtection() {
        when(userRepository.findById(102L)).thenReturn(Optional.of(nimal));
        when(userRepository.findByPhone("+94771000002")).thenReturn(Optional.of(nimal));
        when(bankAccountRepository.findByUserIdAndIsDefaultTrueAndStatus(101L, BankAccountStatus.ACTIVE))
                .thenReturn(Optional.of(kasunAccountB));
        when(bankAccountRepository.findByUserIdAndIsDefaultTrueAndStatus(102L, BankAccountStatus.ACTIVE))
                .thenReturn(Optional.of(nimalAccountA));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            ReflectionTestUtils.setField(t, "id", 601L);
            return t;
        });

        String sharedKey = "IDEM-KEY-DEMO-REPEAT";
        InitiateTransferRequest request = new InitiateTransferRequest(
                102L, null, new BigDecimal("750.00"), "LKR",
                null, "Rent contribution [TEST]", "123456");

        // First attempt -> processes and caches
        TransferResponse firstResp = transferService.initiateTransfer(
                sharedKey, null, request, kasun, "127.0.0.1");
        assertThat(firstResp.status()).isEqualTo(TransactionStatus.COMPLETED);

        // Simulate IdempotencyRecord found in repository
        String payloadHash = idempotencyService.hashPayload(request);
        IdempotencyRecord cachedRecord = new IdempotencyRecord(
                sharedKey, payloadHash, 601L, objectMapper.valueToTree(firstResp).toString(), 200);
        when(idempotencyRecordRepository.findByIdempotencyKey(sharedKey)).thenReturn(Optional.of(cachedRecord));

        // Replay attempt with same key and payload -> returns cached response directly
        TransferResponse replayResp = transferService.initiateTransfer(
                sharedKey, null, request, kasun, "127.0.0.1");

        assertThat(replayResp.transferId()).isEqualTo(firstResp.transferId());
        assertThat(replayResp.reference()).isEqualTo(firstResp.reference());
        assertThat(replayResp.status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Demo Step 6: Failure scenario triggers FAILED transaction outcome")
    void testDemoStep6_failureScenarioInsufficientFunds() {
        when(userRepository.findById(102L)).thenReturn(Optional.of(nimal));
        when(userRepository.findByPhone("+94771000002")).thenReturn(Optional.of(nimal));
        when(bankAccountRepository.findByUserIdAndIsDefaultTrueAndStatus(101L, BankAccountStatus.ACTIVE))
                .thenReturn(Optional.of(kasunAccountB));
        when(bankAccountRepository.findByUserIdAndIsDefaultTrueAndStatus(102L, BankAccountStatus.ACTIVE))
                .thenReturn(Optional.of(nimalAccountA));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Magic amount 10000.00 LKR triggers INSUFFICIENT_FUNDS in SimulatedBankAdapter
        InitiateTransferRequest request = new InitiateTransferRequest(
                102L, null, new BigDecimal("10000.00"), "LKR",
                null, "High value test [TEST]", "123456");

        TransferResponse failResp = transferService.initiateTransfer(
                UUID.randomUUID().toString(), null, request, kasun, "127.0.0.1");

        assertThat(failResp.status()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    @DisplayName("Demo Step 7: Timeout / delayed confirmation scenario reconciles to COMPLETED")
    void testDemoStep7_timeoutAndReconciliationScenario() {
        when(userRepository.findById(102L)).thenReturn(Optional.of(nimal));
        when(userRepository.findByPhone("+94771000002")).thenReturn(Optional.of(nimal));
        when(bankAccountRepository.findByUserIdAndIsDefaultTrueAndStatus(101L, BankAccountStatus.ACTIVE))
                .thenReturn(Optional.of(kasunAccountB));
        when(bankAccountRepository.findByUserIdAndIsDefaultTrueAndStatus(102L, BankAccountStatus.ACTIVE))
                .thenReturn(Optional.of(nimalAccountA));

        // Magic amount 30000.00 LKR triggers DELAYED_CONFIRMATION (REQUIRES_RECONCILIATION state)
        Transfer pendingTransfer = new Transfer(
                UUID.randomUUID().toString(), kasun, nimal, new BigDecimal("30000.00"), "LKR", TransferInitiatedVia.APP);
        pendingTransfer.setSenderBankCode("SIMULATED_BANK_B");
        pendingTransfer.setReceiverBankCode("SIMULATED_BANK_A");
        ReflectionTestUtils.setField(pendingTransfer, "id", 701L);

        when(transferRepository.save(any(Transfer.class))).thenReturn(pendingTransfer);

        InitiateTransferRequest request = new InitiateTransferRequest(
                102L, null, new BigDecimal("30000.00"), "LKR",
                null, "Delayed confirmation [TEST]", "123456");

        TransferResponse initialResp = transferService.initiateTransfer(
                UUID.randomUUID().toString(), null, request, kasun, "127.0.0.1");

        assertThat(initialResp.status()).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);

        // Reconciliation action by Admin/system
        when(transferRepository.findById(701L)).thenReturn(Optional.of(pendingTransfer));
        ReconcileResultResponse reconcileResp = reconciliationService.reconcileTransfer(701L);

        assertThat(reconcileResp.resolvedStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(reconcileResp.transferId()).isEqualTo(701L);
    }

    @Test
    @DisplayName("Demo Step 8: Reversal flow transitions completed transfer to REVERSED")
    void testDemoStep8_transferReversalFlow() {
        Transfer completedTransfer = new Transfer(
                UUID.randomUUID().toString(), kasun, nimal, new BigDecimal("1200.00"), "LKR", TransferInitiatedVia.APP);
        completedTransfer.startProcessing("SENT_TO_BANK");
        completedTransfer.complete("BANK-TX-OK", "SIM-PROV-OK");
        completedTransfer.setSenderBankCode("SIMULATED_BANK_B");
        completedTransfer.setReceiverBankCode("SIMULATED_BANK_A");
        ReflectionTestUtils.setField(completedTransfer, "id", 801L);

        when(transferRepository.findById(801L)).thenReturn(Optional.of(completedTransfer));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 1. Request reversal (sender requests reversal)
        Transfer pendingReversal = reversalService.requestReversal(801L, 101L, "Disputed payment [TEST]", "127.0.0.1");
        assertThat(pendingReversal.getStatus()).isEqualTo(TransactionStatus.REVERSAL_PENDING);

        // 2. Confirm reversal (bank adapter confirms reversal)
        Transfer confirmedReversed = reversalService.confirmReversal(801L, "SIM-REV-CONFIRM-999", "127.0.0.1");
        assertThat(confirmedReversed.getStatus()).isEqualTo(TransactionStatus.REVERSED);
    }
}

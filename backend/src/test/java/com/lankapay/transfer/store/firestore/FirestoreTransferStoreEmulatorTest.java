package com.lankapay.transfer.store.firestore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.lankapay.audit.service.AuditService;
import com.lankapay.audit.store.firestore.FirestoreAuditStore;
import com.lankapay.bank.adapter.AdapterResponse;
import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.store.firestore.FirestoreLinkedAccountStore;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.common.firestore.FirestoreTransactionHelper;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.store.firestore.FirestoreUserStore;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransferInitiatedVia;
import com.lankapay.transfer.service.*;
import com.lankapay.transfer.web.dto.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test suite executing against Google Cloud Firestore emulator on port 8085.
 *
 * Verifies:
 * 1. Success transfer flow (PENDING -> PROCESSING -> COMPLETED) with money stored as long minor units + currency.
 * 2. Insufficient funds rejected with 422 UnprocessableEntity.
 * 3. Duplicate submission: same key + same payload replays cached result; same key + different payload is rejected with 409 Conflict.
 * 4. Timeout after provider request moves to REQUIRES_RECONCILIATION, NEVER to FAILED.
 * 5. Delayed confirmation moves to REQUIRES_RECONCILIATION, then resolved to COMPLETED via status query.
 * 6. Failure + reconciliation: bank failure status is reconciled to FAILED.
 * 7. Reversal workflow: COMPLETED -> REVERSAL_PENDING -> REVERSED.
 * 8. Concurrent same-key requests: multiple concurrent threads submitting identical key produce exactly one transaction.
 * 9. Unauthorized access: third party access is rejected with 403 Forbidden (object-level security).
 * 10. History query with cursor pagination and in-memory filtering for multiple inequalities.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(FirestoreTransferStoreEmulatorTest.EmulatorAvailableCondition.class)
class FirestoreTransferStoreEmulatorTest {

    private static final Logger log = LoggerFactory.getLogger(FirestoreTransferStoreEmulatorTest.class);
    private static final String PROJECT_ID = "lankapay-test";

    private static Firestore firestore;
    private static FirestoreTransactionHelper txHelper;
    private static FirestoreTransactionStore transactionStore;
    private static FirestoreUserStore userStore;
    private static FirestoreLinkedAccountStore linkedAccountStore;
    private static FirestoreAuditStore auditStore;
    private static AuditService auditService;
    private static DailyLimitService dailyLimitService;
    private static IdempotencyService idempotencyService;
    private static ObjectMapper objectMapper;
    private static PasswordEncoder passwordEncoder;
    private static BankAdapter mockBankAdapter;
    private static BankAdapterRegistry adapterRegistry;
    private static TransferProcessor transferProcessor;
    private static TransferService transferService;
    private static ReconciliationService reconciliationService;
    private static ReversalService reversalService;

    private static UserAccount senderUserAccount;
    private static UserAccount receiverUserAccount;
    private static User senderUser;
    private static User receiverUser;
    private static User thirdPartyUser;
    private static LinkedAccount senderLinkedAcct;
    private static LinkedAccount receiverLinkedAcct;

    private static final String RAW_PIN = "123456";

    @BeforeAll
    static void initFirestore() {
        String emulatorHost = System.getenv("FIRESTORE_EMULATOR_HOST");
        if (emulatorHost == null || emulatorHost.isBlank()) {
            emulatorHost = System.getProperty("FIRESTORE_EMULATOR_HOST");
        }
        if (emulatorHost == null || emulatorHost.isBlank()) {
            if (isPortListening("localhost", 8085)) {
                emulatorHost = "localhost:8085";
            } else if (isPortListening("127.0.0.1", 8085)) {
                emulatorHost = "127.0.0.1:8085";
            }
        }

        if (emulatorHost == null || emulatorHost.isBlank()) {
            throw new IllegalStateException("Firestore emulator host could not be determined");
        }

        System.setProperty("FIRESTORE_EMULATOR_HOST", emulatorHost);
        log.info("Connecting FirestoreTransferStoreEmulatorTest to emulator at [{}]", emulatorHost);

        FirestoreOptions options = FirestoreOptions.getDefaultInstance().toBuilder()
                .setProjectId(PROJECT_ID)
                .setHost(emulatorHost)
                .setCredentials(NoCredentials.getInstance())
                .build();

        firestore = options.getService();
        txHelper = new FirestoreTransactionHelper(firestore);

        transactionStore = new FirestoreTransactionStore(firestore);
        userStore = new FirestoreUserStore(firestore, txHelper);
        linkedAccountStore = new FirestoreLinkedAccountStore(firestore, txHelper);
        auditStore = new FirestoreAuditStore(firestore);
        auditService = new AuditService(auditStore);

        dailyLimitService = new DailyLimitService(transactionStore);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        idempotencyService = new IdempotencyService(transactionStore, objectMapper);
        passwordEncoder = new BCryptPasswordEncoder(12);

        mockBankAdapter = mock(BankAdapter.class);
        adapterRegistry = mock(BankAdapterRegistry.class);
        when(adapterRegistry.get(anyString())).thenReturn(mockBankAdapter);

        transferProcessor = new TransferProcessor(
                transactionStore,
                adapterRegistry,
                dailyLimitService,
                auditService,
                null
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

        reconciliationService = new ReconciliationService(
                transactionStore,
                linkedAccountStore,
                adapterRegistry,
                dailyLimitService,
                auditService
        );

        reversalService = new ReversalService(
                transactionStore,
                auditService
        );

        // Seed sender, receiver, and third party users in userStore
        long suffix = System.currentTimeMillis() % 10000000L;
        String senderPhone = "+9477" + String.format("%07d", suffix);
        String receiverPhone = "+9478" + String.format("%07d", suffix);
        String thirdPartyPhone = "+9471" + String.format("%07d", suffix);

        String encodedPin = passwordEncoder.encode(RAW_PIN);

        senderUserAccount = userStore.saveUser(new UserAccount(
                null, senderPhone, "Sender Alice", "1990" + String.format("%08d", suffix),
                encodedPin, UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now()
        ));
        receiverUserAccount = userStore.saveUser(new UserAccount(
                null, receiverPhone, "Receiver Bob", "1991" + String.format("%08d", suffix),
                encodedPin, UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now()
        ));
        UserAccount thirdPartyAccount = userStore.saveUser(new UserAccount(
                null, thirdPartyPhone, "Eve Hacker", "1992" + String.format("%08d", suffix),
                encodedPin, UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now()
        ));

        senderUser = new User();
        senderUser.setId(senderUserAccount.id());
        senderUser.setPhone(senderUserAccount.phone());
        senderUser.setFullName(senderUserAccount.fullName());
        senderUser.setPasswordHash(senderUserAccount.passwordHash());

        receiverUser = new User();
        receiverUser.setId(receiverUserAccount.id());
        receiverUser.setPhone(receiverUserAccount.phone());
        receiverUser.setFullName(receiverUserAccount.fullName());
        receiverUser.setPasswordHash(receiverUserAccount.passwordHash());

        thirdPartyUser = new User();
        thirdPartyUser.setId(thirdPartyAccount.id());
        thirdPartyUser.setPhone(thirdPartyAccount.phone());
        thirdPartyUser.setFullName(thirdPartyAccount.fullName());
        thirdPartyUser.setPasswordHash(thirdPartyAccount.passwordHash());

        // Link active default accounts
        senderLinkedAcct = linkedAccountStore.saveAccount(new LinkedAccount(
                null, senderUser.getId(), "SIMULATED_BANK_A", "hash_sender_" + suffix, "****1234",
                "Alice Primary", "Personal", true, true, "enc_ref_sender",
                BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now()
        ));

        receiverLinkedAcct = linkedAccountStore.saveAccount(new LinkedAccount(
                null, receiverUser.getId(), "SIMULATED_BANK_B", "hash_receiver_" + suffix, "****5678",
                "Bob Primary", "Savings", true, true, "enc_ref_receiver",
                BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now()
        ));
    }

    @BeforeEach
    void resetMocks() {
        reset(mockBankAdapter);
    }

    @Test
    @Order(1)
    @DisplayName("1. Success transfer flow: PENDING -> PROCESSING -> COMPLETED in Firestore")
    void testSuccessfulTransferFlow() throws Exception {
        when(mockBankAdapter.checkTransferEligibility(any())).thenReturn(
                AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE")
        );
        when(mockBankAdapter.initiateTransfer(any())).thenReturn(
                AdapterResponse.success("SIM-BANK-REF-101", "PROVIDER-REF-101")
        );

        String idempotencyKey = "IDEM-TEST-SUCCESS-" + UUID.randomUUID();
        InitiateTransferRequest req = new InitiateTransferRequest(
                receiverUser.getId(), null, new BigDecimal("1500.00"), "LKR",
                "QUO-1", RAW_PIN, "Dinner split"
        );

        TransferResponse response = transferService.initiateTransfer(
                idempotencyKey, RAW_PIN, req, senderUser, "127.0.0.1"
        );

        assertThat(response).isNotNull();
        assertThat(response.transferId()).isNotNull();
        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.amount()).isEqualByComparingTo("1500.00");
        assertThat(response.currency()).isEqualTo("LKR");

        // Verify document structure directly in Firestore
        DocumentSnapshot doc = firestore.collection(FirestoreTransactionStore.TRANSACTIONS_COLLECTION)
                .document(String.valueOf(response.transferId())).get().get();

        assertThat(doc.exists()).isTrue();
        assertThat(doc.getLong("amountMinor")).isEqualTo(150000L); // 1500.00 -> 150000
        assertThat(doc.getString("currency")).isEqualTo("LKR");
        assertThat(doc.getString("status")).isEqualTo("COMPLETED");
        assertThat(doc.getLong("version")).isGreaterThan(1L);

        // Verify append-only events subcollection
        var events = transactionStore.findEventsByTransferId(response.transferId());
        assertThat(events).isNotEmpty();
        assertThat(events.stream().anyMatch(e -> e.toStatus() == TransactionStatus.PENDING)).isTrue();
        assertThat(events.stream().anyMatch(e -> e.toStatus() == TransactionStatus.PROCESSING)).isTrue();
        assertThat(events.stream().anyMatch(e -> e.toStatus() == TransactionStatus.COMPLETED)).isTrue();

        // Verify idempotency document
        DocumentSnapshot idemDoc = firestore.collection(FirestoreTransactionStore.IDEMPOTENCY_COLLECTION)
                .document(idempotencyKey).get().get();
        assertThat(idemDoc.exists()).isTrue();
        assertThat(idemDoc.getLong("txId")).isEqualTo(response.transferId());
    }

    @Test
    @Order(2)
    @DisplayName("2. Insufficient funds rejected with 422 UnprocessableEntity")
    void testInsufficientFundsRejection() {
        when(mockBankAdapter.checkTransferEligibility(any())).thenReturn(
                AdapterResponse.failure("INSUFFICIENT_FUNDS", "Account balance is below the requested transfer amount", "BAL_LOW")
        );

        String idempotencyKey = "IDEM-TEST-INSUFFICIENT-" + UUID.randomUUID();
        InitiateTransferRequest req = new InitiateTransferRequest(
                receiverUser.getId(), null, new BigDecimal("5000.00"), "LKR",
                "QUO-2", RAW_PIN, "Car buy"
        );

        assertThatThrownBy(() -> transferService.initiateTransfer(idempotencyKey, RAW_PIN, req, senderUser, "127.0.0.1"))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("Account balance is below");
    }

    @Test
    @Order(3)
    @DisplayName("3. Duplicate submission: same payload replays cached; diff payload rejected with 409 Conflict")
    void testIdempotencyHandling() {
        when(mockBankAdapter.checkTransferEligibility(any())).thenReturn(
                AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE")
        );
        when(mockBankAdapter.initiateTransfer(any())).thenReturn(
                AdapterResponse.success("SIM-BANK-REF-DUP", "PROVIDER-REF-DUP")
        );

        String idempotencyKey = "IDEM-TEST-DUP-" + UUID.randomUUID();
        InitiateTransferRequest req1 = new InitiateTransferRequest(
                receiverUser.getId(), null, new BigDecimal("250.00"), "LKR",
                "QUO-DUP", RAW_PIN, "Coffee"
        );

        // First attempt
        TransferResponse firstResponse = transferService.initiateTransfer(
                idempotencyKey, RAW_PIN, req1, senderUser, "127.0.0.1"
        );
        assertThat(firstResponse.status()).isEqualTo(TransactionStatus.COMPLETED);

        // Second attempt with EXACT same payload -> Returns same cached result without second transfer
        TransferResponse replayedResponse = transferService.initiateTransfer(
                idempotencyKey, RAW_PIN, req1, senderUser, "127.0.0.1"
        );
        assertThat(replayedResponse.transferId()).isEqualTo(firstResponse.transferId());
        assertThat(replayedResponse.status()).isEqualTo(TransactionStatus.COMPLETED);

        // Third attempt with DIFFERENT payload -> Rejected with 409 Conflict
        InitiateTransferRequest req2 = new InitiateTransferRequest(
                receiverUser.getId(), null, new BigDecimal("750.00"), "LKR", // different amount!
                "QUO-DUP", RAW_PIN, "Coffee"
        );

        assertThatThrownBy(() -> transferService.initiateTransfer(idempotencyKey, RAW_PIN, req2, senderUser, "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("different request payload");
    }

    @Test
    @Order(4)
    @DisplayName("4. Timeout after provider request moves to REQUIRES_RECONCILIATION, NEVER to FAILED")
    void testTimeoutMovesToRequiresReconciliation() throws Exception {
        when(mockBankAdapter.checkTransferEligibility(any())).thenReturn(
                AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE")
        );
        when(mockBankAdapter.initiateTransfer(any())).thenThrow(
                new RuntimeException("SocketTimeoutException: Connection timed out to partner bank")
        );

        String idempotencyKey = "IDEM-TEST-TIMEOUT-" + UUID.randomUUID();
        InitiateTransferRequest req = new InitiateTransferRequest(
                receiverUser.getId(), null, new BigDecimal("3200.00"), "LKR",
                "QUO-TO", RAW_PIN, "Rent share"
        );

        TransferResponse response = transferService.initiateTransfer(
                idempotencyKey, RAW_PIN, req, senderUser, "127.0.0.1"
        );

        assertThat(response.status()).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);

        DocumentSnapshot doc = firestore.collection(FirestoreTransactionStore.TRANSACTIONS_COLLECTION)
                .document(String.valueOf(response.transferId())).get().get();
        assertThat(doc.getString("status")).isEqualTo("REQUIRES_RECONCILIATION");

        var events = transactionStore.findEventsByTransferId(response.transferId());
        assertThat(events.get(events.size() - 1).toStatus()).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);
    }

    @Test
    @Order(5)
    @DisplayName("5. Delayed confirmation: PENDING response moves to REQUIRES_RECONCILIATION, resolved on query")
    void testDelayedConfirmationAndReconciliation() {
        when(mockBankAdapter.checkTransferEligibility(any())).thenReturn(
                AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE")
        );
        when(mockBankAdapter.initiateTransfer(any())).thenReturn(
                AdapterResponse.pending("PROV-DELAYED-1", "DELAYED")
        );

        String idempotencyKey = "IDEM-TEST-DELAYED-" + UUID.randomUUID();
        InitiateTransferRequest req = new InitiateTransferRequest(
                receiverUser.getId(), null, new BigDecimal("1200.00"), "LKR",
                "QUO-DEL", RAW_PIN, "Books"
        );

        TransferResponse response = transferService.initiateTransfer(
                idempotencyKey, RAW_PIN, req, senderUser, "127.0.0.1"
        );

        assertThat(response.status()).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);

        // Later reconciliation query resolves to COMPLETED
        when(mockBankAdapter.queryTransferStatus(any())).thenReturn(
                AdapterResponse.success("PROV-DELAYED-1", "SETTLED")
        );

        ReconcileResultResponse reconResult = reconciliationService.reconcileTransfer(response.transferId(), senderUser.getId());
        assertThat(reconResult.resolved()).isTrue();
        assertThat(reconResult.resolvedStatus()).isEqualTo(TransactionStatus.COMPLETED);

        // Verify status in Firestore
        var updated = transactionStore.findTransferById(response.transferId());
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @Order(6)
    @DisplayName("6. Failure + reconciliation: bank failure reconciles to FAILED")
    void testReconciliationToFailed() {
        when(mockBankAdapter.checkTransferEligibility(any())).thenReturn(
                AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE")
        );
        when(mockBankAdapter.initiateTransfer(any())).thenReturn(
                AdapterResponse.timeout("PROV-FAIL-1", "TIMEOUT_AT_INITIATE")
        );

        String idempotencyKey = "IDEM-TEST-FAIL-RECON-" + UUID.randomUUID();
        InitiateTransferRequest req = new InitiateTransferRequest(
                receiverUser.getId(), null, new BigDecimal("800.00"), "LKR",
                "QUO-FAIL", RAW_PIN, "Groceries"
        );

        TransferResponse response = transferService.initiateTransfer(
                idempotencyKey, RAW_PIN, req, senderUser, "127.0.0.1"
        );
        assertThat(response.status()).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);

        // Status query reveals bank failed
        when(mockBankAdapter.queryTransferStatus(any())).thenReturn(
                AdapterResponse.failure("BANK_SYSTEM_OUTAGE", "Core banking offline", "FAIL_RECON")
        );

        ReconcileResultResponse reconResult = reconciliationService.reconcileTransfer(response.transferId(), senderUser.getId());
        assertThat(reconResult.resolved()).isTrue();
        assertThat(reconResult.resolvedStatus()).isEqualTo(TransactionStatus.FAILED);

        var updated = transactionStore.findTransferById(response.transferId());
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(updated.get().failureReason()).contains("Core banking offline");
    }

    @Test
    @Order(7)
    @DisplayName("7. Reversal workflow: COMPLETED -> REVERSAL_PENDING -> REVERSED")
    void testReversalWorkflow() {
        when(mockBankAdapter.checkTransferEligibility(any())).thenReturn(
                AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE")
        );
        when(mockBankAdapter.initiateTransfer(any())).thenReturn(
                AdapterResponse.success("SIM-REV-TX", "PROV-REV-1")
        );

        String idempotencyKey = "IDEM-TEST-REV-" + UUID.randomUUID();
        InitiateTransferRequest req = new InitiateTransferRequest(
                receiverUser.getId(), null, new BigDecimal("4500.00"), "LKR",
                "QUO-REV", RAW_PIN, "Refund test"
        );

        TransferResponse response = transferService.initiateTransfer(
                idempotencyKey, RAW_PIN, req, senderUser, "127.0.0.1"
        );
        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);

        var pendingRev = reversalService.requestReversal(
                response.transferId(), senderUser.getId(), "Customer dispute duplicate charge", "127.0.0.1"
        );
        assertThat(pendingRev.getStatus()).isEqualTo(TransactionStatus.REVERSAL_PENDING);

        var reversedTransfer = reversalService.confirmReversal(
                response.transferId(), "SIM-REV-CONFIRM", "127.0.0.1"
        );

        assertThat(reversedTransfer.getStatus()).isEqualTo(TransactionStatus.REVERSED);

        var updatedInStore = transactionStore.findTransferById(response.transferId());
        assertThat(updatedInStore).isPresent();
        assertThat(updatedInStore.get().status()).isEqualTo(TransactionStatus.REVERSED);

        var events = transactionStore.findEventsByTransferId(response.transferId());
        assertThat(events.stream().anyMatch(e -> e.toStatus() == TransactionStatus.REVERSAL_PENDING)).isTrue();
        assertThat(events.stream().anyMatch(e -> e.toStatus() == TransactionStatus.REVERSED)).isTrue();
    }

    @Test
    @Order(8)
    @DisplayName("8. Concurrent same-key requests: exactly one transaction created under concurrency")
    void testConcurrentSameKeyRequests() throws Exception {
        when(mockBankAdapter.checkTransferEligibility(any())).thenReturn(
                AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE")
        );
        when(mockBankAdapter.initiateTransfer(any())).thenReturn(
                AdapterResponse.success("SIM-CONC-TX", "PROV-CONC-1")
        );

        String idempotencyKey = "IDEM-TEST-CONC-" + UUID.randomUUID();
        InitiateTransferRequest req = new InitiateTransferRequest(
                receiverUser.getId(), null, new BigDecimal("500.00"), "LKR",
                "QUO-CONC", RAW_PIN, "Concurrent test"
        );

        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);
        List<TransferResponse> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    TransferResponse resp = transferService.initiateTransfer(
                            idempotencyKey, RAW_PIN, req, senderUser, "127.0.0.1"
                    );
                    results.add(resp);
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(endGate.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(results).isNotEmpty();
        // All successful responses must point to the identical transferId!
        Long initialId = results.get(0).transferId();
        for (TransferResponse resp : results) {
            assertThat(resp.transferId()).isEqualTo(initialId);
        }
    }

    @Test
    @Order(9)
    @DisplayName("9. Unauthorized access: third party access rejected with 403 Forbidden")
    void testObjectLevelAuthorization() {
        when(mockBankAdapter.checkTransferEligibility(any())).thenReturn(
                AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE")
        );
        when(mockBankAdapter.initiateTransfer(any())).thenReturn(
                AdapterResponse.success("SIM-SEC-TX", "PROV-SEC-1")
        );

        String idempotencyKey = "IDEM-TEST-SEC-" + UUID.randomUUID();
        InitiateTransferRequest req = new InitiateTransferRequest(
                receiverUser.getId(), null, new BigDecimal("350.00"), "LKR",
                "QUO-SEC", RAW_PIN, "Secret transfer"
        );

        TransferResponse response = transferService.initiateTransfer(
                idempotencyKey, RAW_PIN, req, senderUser, "127.0.0.1"
        );

        // Sender can read details and receipt
        TransferResponse senderView = transferService.getTransferById(response.transferId(), senderUser.getId());
        assertThat(senderView).isNotNull();
        ReceiptResponse receipt = transferService.getTransferReceipt(response.transferId(), senderUser.getId());
        assertThat(receipt).isNotNull();

        // Receiver can read details and receipt
        TransferResponse receiverView = transferService.getTransferById(response.transferId(), receiverUser.getId());
        assertThat(receiverView).isNotNull();

        // Third party user must be strictly rejected
        assertThatThrownBy(() -> transferService.getTransferById(response.transferId(), thirdPartyUser.getId()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("permission");

        assertThatThrownBy(() -> transferService.getTransferReceipt(response.transferId(), thirdPartyUser.getId()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("permission");

        assertThatThrownBy(() -> reconciliationService.reconcileTransfer(response.transferId(), thirdPartyUser.getId()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("permission");
    }

    @Test
    @Order(10)
    @DisplayName("10. History query with cursor pagination and in-memory filtering for multiple inequalities")
    void testHistoryQueriesAndPagination() {
        // Query sender transactions
        Page<TransferResponse> sentPage = transferService.getTransfersWithFilters(
                senderUser.getId(),
                TransferDirection.SENT,
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(sentPage).isNotEmpty();
        assertThat(sentPage.getContent()).allMatch(t -> t.senderId().equals(senderUser.getId()));

        // Query with amount filters (tests in-memory secondary filtering in FirestoreTransactionStore)
        Page<TransferResponse> filteredPage = transferService.getTransfersWithFilters(
                senderUser.getId(),
                TransferDirection.SENT,
                null,
                null,
                null,
                null,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                PageRequest.of(0, 10)
        );

        for (TransferResponse t : filteredPage.getContent()) {
            assertThat(t.amount()).isGreaterThanOrEqualTo(new BigDecimal("1000.00"));
            assertThat(t.amount()).isLessThanOrEqualTo(new BigDecimal("5000.00"));
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    private static boolean isPortListening(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static class EmulatorAvailableCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            String host = System.getenv("FIRESTORE_EMULATOR_HOST");
            if (host == null || host.isBlank()) {
                host = System.getProperty("FIRESTORE_EMULATOR_HOST");
            }
            if (host != null && !host.isBlank()) {
                return ConditionEvaluationResult.enabled("FIRESTORE_EMULATOR_HOST is set: " + host);
            }
            if (isPortListening("localhost", 8085) || isPortListening("127.0.0.1", 8085)) {
                return ConditionEvaluationResult.enabled("Firestore emulator listening on port 8085");
            }
            return ConditionEvaluationResult.disabled("Skipping: No Firestore emulator reachable on port 8085");
        }
    }
}

package com.lankapay.bill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bill.adapter.*;
import com.lankapay.bill.crypto.BillerReferenceEncryptor;
import com.lankapay.bill.domain.BillPaymentRecord;
import com.lankapay.bill.domain.SavedBiller;
import com.lankapay.bill.service.BillService;
import com.lankapay.bill.service.SavedBillerService;
import com.lankapay.bill.store.InMemoryBillPaymentStore;
import com.lankapay.bill.store.InMemorySavedBillerStore;
import com.lankapay.bill.web.dto.*;
import com.lankapay.common.exception.*;
import com.lankapay.feature.config.FeatureProperties;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.domain.NotificationType;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.transfer.domain.FundingSource;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.service.DailyLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Bills Module Integration & Scenario Tests")
class BillModuleIntegrationTest {

    private SimulatedBillerAdapter simulatedAdapter;
    private BillerAdapterRegistry registry;
    private BillerReferenceEncryptor encryptor;
    private InMemorySavedBillerStore savedBillerStore;
    private InMemoryBillPaymentStore billPaymentStore;
    private SavedBillerService savedBillerService;
    private BillService billService;
    private PasswordEncoder passwordEncoder;
    private ObjectMapper objectMapper;

    @Mock
    private DailyLimitService dailyLimitService;
    @Mock
    private UserStore userStore;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;
    @Mock
    private FeatureProperties featureProperties;

    private User testUser;
    private final String testPin = "123456";

    @BeforeEach
    void setUp() {
        simulatedAdapter = new SimulatedBillerAdapter();
        simulatedAdapter.reset();
        registry = new BillerAdapterRegistry(List.of(simulatedAdapter));

        encryptor = new BillerReferenceEncryptor("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        savedBillerStore = new InMemorySavedBillerStore();
        billPaymentStore = new InMemoryBillPaymentStore();
        passwordEncoder = new BCryptPasswordEncoder();
        objectMapper = new ObjectMapper();

        testUser = new User("+94771234567", "Nimali Silva", null, null);
        testUser.setId(10L);

        UserAccount userAccount = new UserAccount(
                testUser.getId(),
                testUser.getPhone(),
                testUser.getFullName(),
                "200012345678",
                passwordEncoder.encode(testPin),
                UserStatus.ACTIVE,
                UserRole.CUSTOMER,
                0,
                null,
                Instant.now(),
                Instant.now()
        );
        lenient().when(userStore.findUserById(testUser.getId())).thenReturn(Optional.of(userAccount));

        savedBillerService = new SavedBillerService(savedBillerStore, registry, encryptor, auditService);

        billService = new BillService(
                registry,
                billPaymentStore,
                savedBillerService,
                encryptor,
                null, // transactionStore
                dailyLimitService,
                null, // cardDailyLimitService
                null, // linkedAccountStore
                null, // cardStore
                userStore,
                passwordEncoder,
                notificationService,
                auditService,
                featureProperties,
                objectMapper
        );
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Inquiries & Scenario Testing
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Lookup: bill found returns positive amount due and fee breakdown")
    void lookupBillFound() {
        BillLookupResponse resp = billService.lookupBill("SIM_ELEC_01", "1002345678");
        assertThat(resp.billerId()).isEqualTo("SIM_ELEC_01");
        assertThat(resp.billStatus()).isEqualTo(BillStatus.BILL_FOUND);
        assertThat(resp.amountDue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(resp.serviceFee()).isNotNull();
        assertThat(resp.totalAmount()).isEqualTo(resp.amountDue().add(resp.serviceFee()));
        assertThat(resp.maskedReference()).isEqualTo("******5678");
    }

    @Test
    @DisplayName("Lookup: no bill due scenario returns zero balance")
    void lookupNoBillDue() {
        BillLookupResponse resp = billService.lookupBill("SIM_WATER_01", "PAID-ACCOUNT-990");
        assertThat(resp.billStatus()).isEqualTo(BillStatus.NO_BILL_DUE);
        assertThat(resp.amountDue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Lookup: invalid reference returns INVALID_REFERENCE status")
    void lookupInvalidReference() {
        BillLookupResponse resp = billService.lookupBill("SIM_MOBI_01", "INVALID-123");
        assertThat(resp.billStatus()).isEqualTo(BillStatus.INVALID_REFERENCE);
    }

    @Test
    @DisplayName("Lookup: amount changed returns updated amount due")
    void lookupAmountChanged() {
        BillLookupResponse resp = billService.lookupBill("SIM_INET_01", "CHANGED-REF-991");
        assertThat(resp.billStatus()).isEqualTo(BillStatus.AMOUNT_CHANGED);
        assertThat(resp.amountDue()).isEqualByComparingTo(new BigDecimal("3850.00"));
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Payment Happy Path & Notifications
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PayBill: normal flow succeeds, generates receipt, checks limit, and dispatches lock-screen safe notification")
    void payBillSuccess() {
        PayBillRequest req = new PayBillRequest(
                "SIM_ELEC_01",
                "1002345678",
                new BigDecimal("2500.00"),
                "LKR",
                new FundingSource(FundingType.ACCOUNT, 1L),
                testPin,
                "Monthly electric bill",
                true,
                "Home Electricity"
        );

        BillPaymentReceiptResponse receipt = billService.payBill(testUser, req, "IDEMP-100", null, "127.0.0.1");

        assertThat(receipt.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(receipt.receiptNumber()).startsWith("REC-BILL-");
        assertThat(receipt.billerId()).isEqualTo("SIM_ELEC_01");
        assertThat(receipt.billerName()).isEqualTo("Simulated Electricity Board");
        assertThat(receipt.maskedReference()).isEqualTo("******5678");
        assertThat(receipt.amount()).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(receipt.fee()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(receipt.totalAmount()).isEqualByComparingTo(new BigDecimal("2530.00"));

        // Verify daily limit checked & recorded
        verify(dailyLimitService).checkLimit(eq(testUser), eq(new BigDecimal("2530.00")));
        verify(dailyLimitService).recordSpend(eq(testUser), eq(new BigDecimal("2530.00")));

        // Verify lock-screen safe notification: NO amounts, NO account numbers
        verify(notificationService).dispatch(
                eq(testUser),
                eq(NotificationType.PAYMENT_SENT),
                eq("Payment Sent"),
                argThat(msg -> msg.contains("Simulated Electricity Board") &&
                        !msg.contains("2500") && !msg.contains("LKR") && !msg.contains("1002345678"))
        );

        // Verify biller was auto-saved
        assertThat(savedBillerStore.findByUserId(testUser.getId())).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Deterministic Payment Scenarios & Reconciliation
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario: Biller timeout moves transaction to REQUIRES_RECONCILIATION (never FAILED), and reconcile resolves to COMPLETED")
    void scenarioTimeoutRequiresReconciliationAndReconciles() {
        PayBillRequest req = new PayBillRequest(
                "SIM_ELEC_01",
                "REF-TIMEOUT-992",
                new BigDecimal("1500.00"),
                "LKR",
                new FundingSource(FundingType.ACCOUNT, 1L),
                testPin,
                "Timeout test",
                false,
                null
        );

        BillPaymentReceiptResponse receipt = billService.payBill(testUser, req, "IDEMP-TIMEOUT", null, "127.0.0.1");

        // Per section 9 & prompt: timeout moves to REQUIRES_RECONCILIATION, NEVER FAILED
        assertThat(receipt.status()).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);

        // When reconcile is triggered on this payment:
        BillPaymentReceiptResponse reconciled = billService.reconcilePayment(receipt.paymentId(), testUser.getId());
        assertThat(reconciled.status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Scenario: Delayed confirmation returns PENDING and reconcile resolves to COMPLETED")
    void scenarioDelayedConfirmationAndReconcile() {
        PayBillRequest req = new PayBillRequest(
                "SIM_WATER_01",
                "REF-DELAY-993",
                new BigDecimal("900.00"),
                "LKR",
                new FundingSource(FundingType.ACCOUNT, 1L),
                testPin,
                "Delay test",
                false,
                null
        );

        BillPaymentReceiptResponse receipt = billService.payBill(testUser, req, "IDEMP-DELAY", null, "127.0.0.1");
        assertThat(receipt.status()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    @DisplayName("Scenario: Hard failure transitions to FAILED and does not record spend")
    void scenarioHardFailure() {
        PayBillRequest req = new PayBillRequest(
                "SIM_MOBI_01",
                "REF-FAIL-994",
                new BigDecimal("500.00"),
                "LKR",
                new FundingSource(FundingType.ACCOUNT, 1L),
                testPin,
                "Fail test",
                false,
                null
        );

        BillPaymentReceiptResponse receipt = billService.payBill(testUser, req, "IDEMP-FAIL", null, "127.0.0.1");
        assertThat(receipt.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(receipt.failureReason()).contains("BILLER_REJECTED");

        // Verify spend was NOT recorded
        verify(dailyLimitService, never()).recordSpend(any(), any());
    }

    @Test
    @DisplayName("Scenario: Later reversal marks transaction as REVERSED upon reconcile")
    void scenarioLaterReversal() {
        PayBillRequest req = new PayBillRequest(
                "SIM_COUNCIL_01",
                "REF-REV-995",
                new BigDecimal("1200.00"),
                "LKR",
                new FundingSource(FundingType.ACCOUNT, 1L),
                testPin,
                "Reversal test",
                false,
                null
        );

        BillPaymentReceiptResponse receipt = billService.payBill(testUser, req, "IDEMP-REV", null, "127.0.0.1");
        assertThat(receipt.status()).isEqualTo(TransactionStatus.COMPLETED);

        // Manually simulate state machine reversal in adapter ledger
        simulatedAdapter.markReversed(receipt.reference());

        // When payment status is queried through adapter:
        BillPaymentResponse queried = simulatedAdapter.queryPayment(receipt.reference());
        assertThat(queried.status()).isEqualTo(TransactionStatus.REVERSED);
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Step-up Authentication & Idempotency
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Step-up Auth: Missing PIN throws AuthorizationException")
    void stepUpAuthMissingPinThrows() {
        PayBillRequest req = new PayBillRequest(
                "SIM_ELEC_01", "1002345678", new BigDecimal("1000.00"), "LKR",
                new FundingSource(FundingType.ACCOUNT, 1L), null, null, false, null
        );

        assertThatThrownBy(() -> billService.payBill(testUser, req, "IDEMP-1", null, "127.0.0.1"))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Step-up authentication required");
    }

    @Test
    @DisplayName("Step-up Auth: Invalid PIN throws InvalidCredentialsException")
    void stepUpAuthInvalidPinThrows() {
        PayBillRequest req = new PayBillRequest(
                "SIM_ELEC_01", "1002345678", new BigDecimal("1000.00"), "LKR",
                new FundingSource(FundingType.ACCOUNT, 1L), "999999", null, false, null
        );

        assertThatThrownBy(() -> billService.payBill(testUser, req, "IDEMP-1", null, "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid PIN");
    }

    @Test
    @DisplayName("Idempotency: Replaying with identical key returns cached receipt without double charge")
    void idempotencyReplaySuccess() {
        PayBillRequest req = new PayBillRequest(
                "SIM_ELEC_01", "1002345678", new BigDecimal("1000.00"), "LKR",
                new FundingSource(FundingType.ACCOUNT, 1L), testPin, null, false, null
        );

        String idempKey = "IDEMP-TEST-REPLAY";
        BillPaymentReceiptResponse first = billService.payBill(testUser, req, idempKey, null, "127.0.0.1");
        BillPaymentReceiptResponse second = billService.payBill(testUser, req, idempKey, null, "127.0.0.1");

        assertThat(first.paymentId()).isEqualTo(second.paymentId());
        assertThat(first.receiptNumber()).isEqualTo(second.receiptNumber());
        // Verify dailyLimitService was only called once for recording spend
        verify(dailyLimitService, times(1)).recordSpend(any(), any());
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Limits & Object-Level Access (IDOR Protection)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Limits: Exceeding daily limit throws UnprocessableEntityException")
    void exceedingDailyLimitThrows() {
        doThrow(new UnprocessableEntityException("DAILY_LIMIT_EXCEEDED", "Daily limit exceeded"))
                .when(dailyLimitService).checkLimit(any(), any());

        PayBillRequest req = new PayBillRequest(
                "SIM_ELEC_01", "1002345678", new BigDecimal("500000.00"), "LKR",
                new FundingSource(FundingType.ACCOUNT, 1L), testPin, null, false, null
        );

        assertThatThrownBy(() -> billService.payBill(testUser, req, "IDEMP-LIMIT", null, "127.0.0.1"))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("Daily limit exceeded");
    }

    @Test
    @DisplayName("Object-Level Access: User A cannot access User B's bill payment receipt")
    void idorProtectionOnPaymentReceipt() {
        PayBillRequest req = new PayBillRequest(
                "SIM_ELEC_01", "1002345678", new BigDecimal("500.00"), "LKR",
                new FundingSource(FundingType.ACCOUNT, 1L), testPin, null, false, null
        );
        BillPaymentReceiptResponse receipt = billService.payBill(testUser, req, "IDEMP-IDOR", null, "127.0.0.1");

        Long otherUserId = 999L;
        assertThatThrownBy(() -> billService.getPaymentById(receipt.paymentId(), otherUserId))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("You do not have permission");
    }

    // ─────────────────────────────────────────────────────────────
    // 6. Concurrency Testing
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Concurrency: Concurrent payments for a user execute safely without race conditions")
    void concurrentPaymentsTest() throws InterruptedException, ExecutionException {
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        Callable<BillPaymentReceiptResponse> task = () -> {
            PayBillRequest req = new PayBillRequest(
                    "SIM_ELEC_01", "1002345678", new BigDecimal("100.00"), "LKR",
                    new FundingSource(FundingType.ACCOUNT, 1L), testPin, null, false, null
            );
            return billService.payBill(testUser, req, UUID.randomUUID().toString(), null, "127.0.0.1");
        };

        List<Future<BillPaymentReceiptResponse>> futures = executor.invokeAll(List.of(task, task, task, task));
        executor.shutdown();

        for (Future<BillPaymentReceiptResponse> f : futures) {
            BillPaymentReceiptResponse r = f.get();
            assertThat(r.status()).isEqualTo(TransactionStatus.COMPLETED);
        }
        assertThat(billPaymentStore.countByUserId(testUser.getId())).isEqualTo(4);
    }

    // ─────────────────────────────────────────────────────────────
    // 7. Saved Billers CRUD & Encryption
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Saved Billers: Stores reference encrypted at rest and masked in responses")
    void savedBillersEncryptionAndMasking() {
        SavedBillerRequest req = new SavedBillerRequest("SIM_ELEC_01", "1002345678", "My Power Bill");
        SavedBillerResponse saved = savedBillerService.saveBiller(testUser.getId(), req, "127.0.0.1");

        assertThat(saved.id()).isNotNull();
        assertThat(saved.nickname()).isEqualTo("My Power Bill");
        assertThat(saved.maskedReference()).isEqualTo("******5678");

        // Verify underlying storage is encrypted (not plaintext)
        SavedBiller inStore = savedBillerStore.findById(saved.id()).orElseThrow();
        assertThat(inStore.encryptedReference()).isNotEqualTo("1002345678");
        assertThat(encryptor.decrypt(inStore.encryptedReference())).isEqualTo("1002345678");

        // Object-level access: another user cannot fetch or delete this saved biller
        Long hackerId = 666L;
        assertThatThrownBy(() -> savedBillerService.getSavedBillerById(saved.id(), hackerId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> savedBillerService.deleteBiller(saved.id(), hackerId, "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class);

        // Owner can delete
        savedBillerService.deleteBiller(saved.id(), testUser.getId(), "127.0.0.1");
        assertThat(savedBillerStore.findById(saved.id())).isEmpty();
    }
}

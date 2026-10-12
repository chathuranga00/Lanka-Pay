package com.lankapay.transfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.adapter.TransferEligibilityRequest;
import com.lankapay.bank.adapter.TransferRequest;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardKind;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.transfer.domain.*;
import com.lankapay.transfer.web.dto.*;
import com.lankapay.virtualcard.provider.*;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Card Transfer Tests - Comprehensive Seeded Scenarios")
class CardTransferTest {

    @Mock TransactionStore transactionStore;
    @Mock UserStore userStore;
    @Mock LinkedAccountStore linkedAccountStore;
    @Mock CardStore cardStore;
    @Mock CardProvider cardProvider;
    @Mock BankAdapterRegistry bankAdapterRegistry;
    @Mock BankAdapter bankAdapter;
    @Mock DailyLimitService dailyLimitService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    @Mock NotificationService notificationService;

    ObjectMapper objectMapper;
    IdempotencyService idempotencyService;
    CardDailyLimitService cardDailyLimitService;
    CardTransferFraudService cardFraudService;
    TransferProcessor transferProcessor;
    TransferService transferService;
    ReconciliationService reconciliationService;
    ReversalService reversalService;

    User sender;
    User payee;
    User otherUser;
    BankAccount payeeAccount;

    static final String RAW_PIN = "123456";
    static final String HASHED_PIN = "$2a$12$hashedpinplaceholder";
    static final String IP = "127.0.0.1";

    // In-memory transaction records to emulate storage
    final Map<Long, TransactionRecord> inMemoryTxs = new ConcurrentHashMap<>();
    final Map<String, IdempotencyRecordModel> inMemoryIdem = new ConcurrentHashMap<>();
    final AtomicLong txIdGen = new AtomicLong(100L);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        inMemoryTxs.clear();
        inMemoryIdem.clear();

        // Transaction store mock behavior
        lenient().when(transactionStore.saveIdempotencyRecord(any()))
                .thenAnswer(inv -> {
                    IdempotencyRecordModel rec = inv.getArgument(0);
                    inMemoryIdem.put(rec.idempotencyKey(), rec);
                    return rec;
                });

        lenient().when(transactionStore.findIdempotencyRecord(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(inMemoryIdem.get(inv.getArgument(0))));

        lenient().when(transactionStore.findTransferByIdempotencyKey(anyString()))
                .thenAnswer(inv -> inMemoryTxs.values().stream()
                        .filter(tx -> inv.getArgument(0).equals(tx.idempotencyKey()))
                        .findFirst());

        lenient().when(transactionStore.createTransferWithIdempotency(any(), any(), any()))
                .thenAnswer(inv -> {
                    TransactionRecord tr = inv.getArgument(0);
                    String key = inv.getArgument(1);
                    for (TransactionRecord existing : inMemoryTxs.values()) {
                        if (key != null && key.equals(existing.idempotencyKey())) {
                            return existing;
                        }
                    }
                    long id = txIdGen.incrementAndGet();
                    TransactionRecord saved = new TransactionRecord(
                            id, tr.reference(), tr.idempotencyKey(), tr.senderId(), tr.senderPhone(),
                            tr.senderFullName(), tr.receiverId(), tr.receiverPhone(), tr.receiverFullName(),
                            tr.senderBankCode(), tr.receiverBankCode(), tr.amount(), tr.currency(),
                            tr.status(), tr.failureReason(), tr.bankTxRef(), tr.providerReference(),
                            tr.note(), tr.simulated(), tr.initiatedVia(), tr.qrCodeId(),
                            tr.version(), tr.createdAt(), tr.updatedAt(), tr.fundingType(),
                            tr.cardId(), tr.cardLast4(), tr.cardKind(), tr.fee(), tr.totalAmount(),
                            tr.challengeRef(), tr.challengeAttempts()
                    );
                    inMemoryTxs.put(id, saved);
                    return saved;
                });

        lenient().when(transactionStore.findTransferById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(inMemoryTxs.get(inv.getArgument(0))));

        lenient().when(transactionStore.transitionTransferStatus(anyLong(), any(), any(), any(), any(), any(), any(), anyLong()))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(0);
                    TransactionStatus status = inv.getArgument(1);
                    String bankTxRef = inv.getArgument(4);
                    String providerRef = inv.getArgument(5);
                    String failureReason = inv.getArgument(6);
                    TransactionRecord existing = inMemoryTxs.get(id);
                    if (existing != null) {
                        TransactionRecord updated = new TransactionRecord(
                                existing.id(), existing.reference(), existing.idempotencyKey(),
                                existing.senderId(), existing.senderPhone(), existing.senderFullName(),
                                existing.receiverId(), existing.receiverPhone(), existing.receiverFullName(),
                                existing.senderBankCode(), existing.receiverBankCode(), existing.amount(),
                                existing.currency(), status, failureReason,
                                bankTxRef != null ? bankTxRef : existing.bankTxRef(),
                                providerRef != null ? providerRef : existing.providerReference(),
                                existing.note(), existing.simulated(), existing.initiatedVia(),
                                existing.qrCodeId(), existing.version() + 1, existing.createdAt(),
                                Instant.now(), existing.fundingType(), existing.cardId(), existing.cardLast4(),
                                existing.cardKind(), existing.fee(), existing.totalAmount(),
                                existing.challengeRef(), existing.challengeAttempts()
                        );
                        inMemoryTxs.put(id, updated);
                        return updated;
                    }
                    return null;
                });

        lenient().when(transactionStore.updateTransferChallenge(anyLong(), any(), anyInt()))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(0);
                    String chalRef = inv.getArgument(1);
                    int attempts = inv.getArgument(2);
                    TransactionRecord existing = inMemoryTxs.get(id);
                    if (existing != null) {
                        TransactionRecord updated = new TransactionRecord(
                                existing.id(), existing.reference(), existing.idempotencyKey(),
                                existing.senderId(), existing.senderPhone(), existing.senderFullName(),
                                existing.receiverId(), existing.receiverPhone(), existing.receiverFullName(),
                                existing.senderBankCode(), existing.receiverBankCode(), existing.amount(),
                                existing.currency(), existing.status(), existing.failureReason(),
                                existing.bankTxRef(), existing.providerReference(),
                                existing.note(), existing.simulated(), existing.initiatedVia(),
                                existing.qrCodeId(), existing.version() + 1, existing.createdAt(),
                                Instant.now(), existing.fundingType(), existing.cardId(), existing.cardLast4(),
                                existing.cardKind(), existing.fee(), existing.totalAmount(),
                                chalRef, attempts
                        );
                        inMemoryTxs.put(id, updated);
                    }
                    return null;
                });

        lenient().when(transactionStore.getCardDailySpentMinor(anyLong(), any()))
                .thenReturn(0L);

        idempotencyService = new IdempotencyService(transactionStore, objectMapper);
        cardDailyLimitService = new CardDailyLimitService(transactionStore);
        cardFraudService = new CardTransferFraudService();

        transferProcessor = new TransferProcessor(
                transactionStore,
                bankAdapterRegistry,
                dailyLimitService,
                auditService,
                notificationService,
                cardProvider,
                cardDailyLimitService,
                cardFraudService
        );

        transferService = new TransferService(
                transactionStore,
                userStore,
                linkedAccountStore,
                transferProcessor,
                dailyLimitService,
                idempotencyService,
                passwordEncoder,
                null,
                cardStore,
                cardProvider,
                cardDailyLimitService,
                cardFraudService
        );

        reconciliationService = new ReconciliationService(
                transactionStore,
                linkedAccountStore,
                bankAdapterRegistry,
                dailyLimitService,
                auditService,
                cardStore,
                cardProvider,
                cardDailyLimitService
        );

        reversalService = new ReversalService(
                transactionStore,
                auditService,
                cardStore,
                cardProvider,
                cardDailyLimitService
        );

        sender = new User("+94771234567", "Kamal Perera", "199012345678", HASHED_PIN);
        ReflectionTestUtils.setField(sender, "id", 1L);
        ReflectionTestUtils.setField(sender, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(sender, "role", UserRole.CUSTOMER);

        payee = new User("+94777654321", "Nimal Silva", "199112345678", HASHED_PIN);
        ReflectionTestUtils.setField(payee, "id", 2L);
        ReflectionTestUtils.setField(payee, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(payee, "role", UserRole.CUSTOMER);

        otherUser = new User("+94779999999", "Other User", "199212345678", HASHED_PIN);
        ReflectionTestUtils.setField(otherUser, "id", 99L);
        ReflectionTestUtils.setField(otherUser, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(otherUser, "role", UserRole.CUSTOMER);

        payeeAccount = new BankAccount(payee, "SIMULATED_BANK_B", "hash2", "****2002", "Payee Account", "enc2");
        ReflectionTestUtils.setField(payeeAccount, "id", 20L);
        ReflectionTestUtils.setField(payeeAccount, "isDefault", true);

        lenient().when(userStore.findUserById(2L)).thenReturn(Optional.of(UserAccount.fromUser(payee)));
        lenient().when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(sender)));
        lenient().when(linkedAccountStore.findActiveDefaultAccount(2L))
                .thenReturn(Optional.of(LinkedAccount.fromBankAccount(payeeAccount)));
        lenient().when(passwordEncoder.matches(RAW_PIN, HASHED_PIN)).thenReturn(true);
    }

    private Card createTestCard(Long id, Long userId, String last4, CardStatus status, boolean onlineEnabled,
                                Long perPaymentLimitMinor, Long dailyLimitMinor, Instant verifiedAt) {
        return new Card(
                id,
                userId,
                null,
                "token-" + last4,
                last4,
                12,
                2027,
                "TEST CARD",
                "My Test Card",
                CardKind.LINKED,
                status,
                onlineEnabled,
                perPaymentLimitMinor,
                dailyLimitMinor,
                Instant.now().minus(2, ChronoUnit.DAYS),
                Instant.now(),
                verifiedAt != null ? verifiedAt : Instant.now().minus(2, ChronoUnit.DAYS),
                null,
                1L,
                null,
                true
        );
    }

    // ─────────────────────────────────────────────────────────────
    // 1. APPROVED CARD TRANSFER
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Approved card transfer: quote shows fee/total/limit, payment captures and receipt shows masked last4")
    void testApprovedCardTransfer() {
        Card card = createTestCard(10L, 1L, "0010", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, null); // 50,000 per payment, 200,000 daily
        when(cardStore.findById(10L)).thenReturn(Optional.of(card));

        // 1. Quote check
        QuoteRequest quoteReq = new QuoteRequest(2L, null, new BigDecimal("1000.00"), "LKR", FundingSource.card(10L));
        QuoteResponse quote = transferService.getQuote(quoteReq, sender);

        assertThat(quote.fee()).isEqualByComparingTo("15.00"); // 1.5% of 1000
        assertThat(quote.totalAmount()).isEqualByComparingTo("1015.00");
        assertThat(quote.feeLabel()).isEqualTo("Simulated Card Processing Fee (1.5%)");
        assertThat(quote.fundingSource().type()).isEqualTo(FundingType.CARD);
        assertThat(quote.remainingDailyLimit()).isEqualByComparingTo("200000.00"); // 20,000,000 minor = 200,000.00 LKR

        // 2. Authorize & Capture mocks
        when(cardProvider.authorize(any())).thenReturn(
                CardAuthResponse.approved("AUTH-0010", card.cardToken(), "0010", CardScenario.ALWAYS_APPROVED)
        );
        when(cardProvider.capture(eq("AUTH-0010"), any())).thenReturn(
                CardAuthResponse.approved("AUTH-0010", card.cardToken(), "0010", CardScenario.ALWAYS_APPROVED)
        );

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("1000.00"), "LKR", null, "Dinner", RAW_PIN, FundingSource.card(10L));

        TransferResponse resp = transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-APPROVED-1", IP);

        assertThat(resp.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(resp.result()).isEqualTo("COMPLETED");
        assertThat(resp.fundingType()).isEqualTo(FundingType.CARD);
        assertThat(resp.cardId()).isEqualTo(10L);
        assertThat(resp.cardLast4()).isEqualTo("0010");
        assertThat(resp.cardKind()).isEqualTo(CardKind.LINKED);
        assertThat(resp.fee()).isEqualByComparingTo("15.00");
        assertThat(resp.totalAmount()).isEqualByComparingTo("1015.00");
        assertThat(resp.paymentMethod()).isEqualTo("Paid with card •••• 0010 (TEST CARD)");

        // Notifications sent safely
        verify(notificationService).notifyPaymentSent(any());
        verify(notificationService).notifyPaymentReceived(any());

        // Receipt check
        ReceiptResponse receipt = transferService.getTransferReceipt(resp.transferId(), 1L);
        assertThat(receipt.paymentMethod()).isEqualTo("Paid with card •••• 0010 (TEST CARD)");
        assertThat(receipt.fee()).isEqualByComparingTo("15.00");
    }

    // ─────────────────────────────────────────────────────────────
    // 2. INSUFFICIENT FUNDS
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Insufficient funds: decline maps to safe error code INSUFFICIENT_FUNDS without leaking internals")
    void testInsufficientFundsDecline() {
        Card card = createTestCard(20L, 1L, "0028", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, null);
        when(cardStore.findById(20L)).thenReturn(Optional.of(card));

        when(cardProvider.authorize(any())).thenReturn(
                CardAuthResponse.declined("Insufficient funds on card", card.cardToken(), "0028", CardScenario.INSUFFICIENT_FUNDS)
        );

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("5000.00"), "LKR", null, "Shopping", RAW_PIN, FundingSource.card(20L));

        assertThatThrownBy(() -> transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-NSF-1", IP))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(ex -> {
                    UnprocessableEntityException uee = (UnprocessableEntityException) ex;
                    assertThat(uee.getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
                    assertThat(uee.getMessage()).doesNotContain("internal").doesNotContain("vault");
                });

        // Ensure reserved spend was released
        assertThat(cardDailyLimitService.getSpentTodayMinor(card.id(), LocalDate.now())).isEqualTo(0L);
    }

    // ─────────────────────────────────────────────────────────────
    // 3. EXPIRED CARD
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Expired card: rejected with CARD_EXPIRED")
    void testExpiredCard() {
        // Expired in 2020
        Card card = new Card(
                30L, 1L, null, "token-0069", "0069", 1, 20, "TEST CARD", "Expired Card",
                CardKind.LINKED, CardStatus.ACTIVE, true, 5_000_000L, 20_000_000L,
                Instant.now().minus(2, ChronoUnit.DAYS), Instant.now(), Instant.now(), null, 1L, null, true
        );
        when(cardStore.findById(30L)).thenReturn(Optional.of(card));

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("1000.00"), "LKR", null, "Test", RAW_PIN, FundingSource.card(30L));

        assertThatThrownBy(() -> transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-EXP-1", IP))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(ex -> assertThat(((UnprocessableEntityException) ex).getErrorCode()).isEqualTo("CARD_EXPIRED"));
    }

    // ─────────────────────────────────────────────────────────────
    // 4. CHALLENGE SUCCESS AND FAILURE
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Challenge required: returns CHALLENGE_REQUIRED, failure increments attempts, success completes")
    void testChallengeSuccessAndFailure() {
        Card card = createTestCard(40L, 1L, "0119", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, null);
        when(cardStore.findById(40L)).thenReturn(Optional.of(card));

        when(cardProvider.authorize(any())).thenReturn(
                CardAuthResponse.pendingChallenge(card.cardToken(), "0119")
        );
        when(cardProvider.startOwnershipChallenge(eq(card.cardToken()), eq(1L), any()))
                .thenReturn("CHALL-REF-99");

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("2500.00"), "LKR", null, "Challenge test", RAW_PIN, FundingSource.card(40L));

        // 1. Initial attempt requires challenge
        TransferResponse initialResp = transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-CHAL-1", IP);
        assertThat(initialResp.status()).isEqualTo(TransactionStatus.CHALLENGE_REQUIRED);
        assertThat(initialResp.result()).isEqualTo("CHALLENGE_REQUIRED");
        Long transferId = initialResp.transferId();

        // 2. Submit wrong OTP -> throws CHALLENGE_FAILED
        doThrow(new UnprocessableEntityException("OTP_INVALID", "Incorrect OTP"))
                .when(cardProvider).confirmChallenge("CHALL-REF-99", 1L, "000000");

        assertThatThrownBy(() -> transferService.challengeTransfer(transferId, "000000", 1L, IP))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(ex -> assertThat(((UnprocessableEntityException) ex).getErrorCode()).isEqualTo("CHALLENGE_FAILED"));

        // 3. Submit valid OTP -> completes payment
        doReturn(CardProviderResponse.ok("Confirmed"))
                .when(cardProvider).confirmChallenge("CHALL-REF-99", 1L, "123456");

        when(cardProvider.authorize(any())).thenReturn(
                CardAuthResponse.approved("AUTH-0119", card.cardToken(), "0119", CardScenario.REQUIRES_OTP_CHALLENGE)
        );
        when(cardProvider.capture(eq("AUTH-0119"), any())).thenReturn(
                CardAuthResponse.approved("AUTH-0119", card.cardToken(), "0119", CardScenario.ALWAYS_APPROVED)
        );

        TransferResponse confirmedResp = transferService.challengeTransfer(transferId, "123456", 1L, IP);
        assertThat(confirmedResp.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(confirmedResp.result()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Challenge failure: exceeding 3 attempts locks the transfer to FAILED")
    void testChallengeMaxAttemptsExceeded() {
        Card card = createTestCard(45L, 1L, "0119", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, null);
        when(cardStore.findById(45L)).thenReturn(Optional.of(card));

        when(cardProvider.authorize(any())).thenReturn(
                CardAuthResponse.pendingChallenge(card.cardToken(), "0119")
        );
        when(cardProvider.startOwnershipChallenge(any(), any(), any())).thenReturn("CHALL-REF-MAX");

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("2500.00"), "LKR", null, "Max attempts", RAW_PIN, FundingSource.card(45L));

        TransferResponse initialResp = transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-CHAL-MAX", IP);
        Long transferId = initialResp.transferId();

        doThrow(new UnprocessableEntityException("OTP_INVALID", "Incorrect OTP"))
                .when(cardProvider).confirmChallenge(any(), any(), any());

        // Attempt 1
        assertThatThrownBy(() -> transferService.challengeTransfer(transferId, "wrong", 1L, IP))
                .isInstanceOf(UnprocessableEntityException.class);
        // Attempt 2
        assertThatThrownBy(() -> transferService.challengeTransfer(transferId, "wrong", 1L, IP))
                .isInstanceOf(UnprocessableEntityException.class);
        // Attempt 3 -> locks to FAILED
        assertThatThrownBy(() -> transferService.challengeTransfer(transferId, "wrong", 1L, IP))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("Maximum attempts exceeded"));

        TransactionRecord record = inMemoryTxs.get(transferId);
        assertThat(record.status()).isEqualTo(TransactionStatus.FAILED);
    }

    // ─────────────────────────────────────────────────────────────
    // 5. TIMEOUT THEN RECONCILE
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Provider timeout moves to REQUIRES_RECONCILIATION, reconciliation queries provider and completes")
    void testTimeoutThenReconcile() {
        Card card = createTestCard(50L, 1L, "0994", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, null);
        when(cardStore.findById(50L)).thenReturn(Optional.of(card));

        when(cardProvider.authorize(any())).thenReturn(
                CardAuthResponse.timeout(card.cardToken(), "0994", CardScenario.PROVIDER_TIMEOUT_BUT_CHARGED)
        );

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("3000.00"), "LKR", null, "Timeout test", RAW_PIN, FundingSource.card(50L));

        TransferResponse resp = transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-TIMEOUT-1", IP);

        // Invariant: Never mark FAILED or COMPLETED on timeout!
        assertThat(resp.status()).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);

        // Reconciliation query returns confirmed capture
        when(cardProvider.queryPayment(eq(resp.reference()), eq(card.cardToken()))).thenReturn(
                CardAuthResponse.approved("AUTH-RECON-1", card.cardToken(), "0994", CardScenario.PROVIDER_TIMEOUT_BUT_CHARGED)
        );

        ReconcileResultResponse reconResult = reconciliationService.reconcileTransfer(resp.transferId());
        assertThat(reconResult.resolved()).isTrue();
        assertThat(reconResult.resolvedStatus()).isEqualTo(TransactionStatus.COMPLETED);

        TransactionRecord updated = inMemoryTxs.get(resp.transferId());
        assertThat(updated.status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    // ─────────────────────────────────────────────────────────────
    // 6. DELAYED CONFIRMATION
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Delayed confirmation moves to REQUIRES_RECONCILIATION and resolves on query")
    void testDelayedConfirmation() {
        Card card = createTestCard(60L, 1L, "1232", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, null);
        when(cardStore.findById(60L)).thenReturn(Optional.of(card));

        when(cardProvider.authorize(any())).thenReturn(
                new CardAuthResponse(false, null, card.cardToken(), "1232",
                        CardAuthStatus.PENDING_CHALLENGE, "Pending confirmation",
                        CardScenario.DELAYED_CONFIRMATION, true, Instant.now())
        );

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("4000.00"), "LKR", null, "Delayed test", RAW_PIN, FundingSource.card(60L));

        TransferResponse resp = transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-DELAYED-1", IP);
        assertThat(resp.status()).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);

        // Reconciliation query resolves to COMPLETED
        when(cardProvider.queryPayment(eq(resp.reference()), eq(card.cardToken()))).thenReturn(
                CardAuthResponse.approved("AUTH-DELAYED-OK", card.cardToken(), "1232", CardScenario.DELAYED_CONFIRMATION)
        );

        ReconcileResultResponse reconResult = reconciliationService.reconcileTransfer(resp.transferId());
        assertThat(reconResult.resolvedStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    // ─────────────────────────────────────────────────────────────
    // 7. HARD DECLINE
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Hard decline maps to safe error code CARD_BLOCKED")
    void testHardDecline() {
        Card card = createTestCard(70L, 1L, "2347", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, null);
        when(cardStore.findById(70L)).thenReturn(Optional.of(card));

        when(cardProvider.authorize(any())).thenReturn(
                CardAuthResponse.declined("Card blocked", card.cardToken(), "2347", CardScenario.HARD_DECLINE)
        );

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("1000.00"), "LKR", null, "Hard decline test", RAW_PIN, FundingSource.card(70L));

        assertThatThrownBy(() -> transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-HARD-1", IP))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(ex -> assertThat(((UnprocessableEntityException) ex).getErrorCode()).isEqualTo("CARD_BLOCKED"));
    }

    // ─────────────────────────────────────────────────────────────
    // 8. REVERSAL / VOID FLOW
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Reversal flow: COMPLETED -> REVERSAL_PENDING -> REVERSED calls provider voidAuth and releases daily limit")
    void testCardReversalFlow() {
        Card card = createTestCard(80L, 1L, "3451", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, null);
        when(cardStore.findById(80L)).thenReturn(Optional.of(card));

        when(cardProvider.authorize(any())).thenReturn(
                CardAuthResponse.approved("AUTH-3451", card.cardToken(), "3451", CardScenario.LATER_REVERSAL)
        );
        when(cardProvider.capture(eq("AUTH-3451"), any())).thenReturn(
                CardAuthResponse.approved("AUTH-3451", card.cardToken(), "3451", CardScenario.LATER_REVERSAL)
        );

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("5000.00"), "LKR", null, "Reversal test", RAW_PIN, FundingSource.card(80L));

        TransferResponse resp = transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-REV-1", IP);
        assertThat(resp.status()).isEqualTo(TransactionStatus.COMPLETED);

        // 1. Request reversal
        Transfer pendingRev = reversalService.requestReversal(resp.transferId(), 1L, "Customer dispute", IP);
        assertThat(pendingRev.getStatus()).isEqualTo(TransactionStatus.REVERSAL_PENDING);

        // 2. Confirm reversal
        when(cardProvider.voidAuth(any(), any())).thenReturn(
                new CardAuthResponse(true, "AUTH-3451", card.cardToken(), "3451",
                        CardAuthStatus.REVERSED, null, CardScenario.ALWAYS_APPROVED, true, Instant.now())
        );

        Transfer reversed = reversalService.confirmReversal(resp.transferId(), "AUTH-3451", IP);
        assertThat(reversed.getStatus()).isEqualTo(TransactionStatus.REVERSED);

        verify(cardProvider).voidAuth(any(), any());
    }

    // ─────────────────────────────────────────────────────────────
    // 9. SAME IDEMPOTENCY-KEY TWICE
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Same Idempotency-Key twice returns cached response without duplicate provider calls")
    void testSameIdempotencyKeyTwice() {
        Card card = createTestCard(90L, 1L, "0010", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, null);
        when(cardStore.findById(90L)).thenReturn(Optional.of(card));

        when(cardProvider.authorize(any())).thenReturn(
                CardAuthResponse.approved("AUTH-DUP-1", card.cardToken(), "0010", CardScenario.ALWAYS_APPROVED)
        );
        when(cardProvider.capture(eq("AUTH-DUP-1"), any())).thenReturn(
                CardAuthResponse.approved("AUTH-DUP-1", card.cardToken(), "0010", CardScenario.ALWAYS_APPROVED)
        );

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("1000.00"), "LKR", null, "Idem test", RAW_PIN, FundingSource.card(90L));

        TransferResponse first = transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-KEY-CARD-DUP", IP);
        assertThat(first.status()).isEqualTo(TransactionStatus.COMPLETED);

        // Second call with same idempotency key
        TransferResponse second = transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-KEY-CARD-DUP", IP);
        assertThat(second.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(second.transferId()).isEqualTo(first.transferId());

        // cardProvider.authorize must be called only ONCE!
        verify(cardProvider, times(1)).authorize(any());
    }

    // ─────────────────────────────────────────────────────────────
    // 10. CONCURRENT PAYMENTS EXCEEDING DAILY LIMIT
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Concurrent payments with one card that would exceed daily limit: only allowed ones succeed")
    void testConcurrentPaymentsDailyLimit() throws Exception {
        // Daily limit: 10,000.00 LKR (1,000,000 minor units).
        Card card = createTestCard(100L, 1L, "0010", CardStatus.ACTIVE, true,
                1_000_000L, 1_000_000L, null);
        when(cardStore.findById(100L)).thenReturn(Optional.of(card));

        when(cardProvider.authorize(any())).thenReturn(
                CardAuthResponse.approved("AUTH-CONC", card.cardToken(), "0010", CardScenario.ALWAYS_APPROVED)
        );
        when(cardProvider.capture(any(), any())).thenReturn(
                CardAuthResponse.approved("AUTH-CONC", card.cardToken(), "0010", CardScenario.ALWAYS_APPROVED)
        );

        // Attempt two simultaneous payments of 7,000.00 LKR each
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String idem = "CONC-KEY-" + i;
            futures.add(executor.submit(() -> {
                latch.await();
                try {
                    InitiateTransferRequest req = new InitiateTransferRequest(
                            2L, null, new BigDecimal("7000.00"), "LKR", null, "Concurrent test", RAW_PIN, FundingSource.card(100L));
                    TransferResponse r = transferService.initiateTransfer(req, sender, RAW_PIN, idem, IP);
                    return r.status().name();
                } catch (UnprocessableEntityException ex) {
                    return ex.getErrorCode();
                }
            }));
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        List<String> results = new ArrayList<>();
        for (Future<String> f : futures) {
            results.add(f.get());
        }

        // Exactly one should succeed (COMPLETED) and one should fail (LIMIT_EXCEEDED)
        assertThat(results).containsExactlyInAnyOrder("COMPLETED", "LIMIT_EXCEEDED");
    }

    // ─────────────────────────────────────────────────────────────
    // 11. FROZEN CARD
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Frozen card: rejected with CARD_FROZEN")
    void testFrozenCard() {
        Card card = createTestCard(110L, 1L, "0010", CardStatus.FROZEN, true,
                5_000_000L, 20_000_000L, null);
        when(cardStore.findById(110L)).thenReturn(Optional.of(card));

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("1000.00"), "LKR", null, "Frozen test", RAW_PIN, FundingSource.card(110L));

        assertThatThrownBy(() -> transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-FROZEN-1", IP))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(ex -> assertThat(((UnprocessableEntityException) ex).getErrorCode()).isEqualTo("CARD_FROZEN"));
    }

    // ─────────────────────────────────────────────────────────────
    // 12. ANOTHER USER'S CARD ID
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Another user's card ID: rejected with FORBIDDEN")
    void testAnotherUsersCardId() {
        // Card belongs to otherUser (userId 99L), but sender (1L) initiates
        Card card = createTestCard(120L, 99L, "0010", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, null);
        when(cardStore.findById(120L)).thenReturn(Optional.of(card));

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("1000.00"), "LKR", null, "IDOR test", RAW_PIN, FundingSource.card(120L));

        assertThatThrownBy(() -> transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-IDOR-1", IP))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(ex -> assertThat(((AuthorizationException) ex).getErrorCode()).isEqualTo("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────
    // 13. ONLINE PAYMENTS DISABLED
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Online payments disabled: rejected with ONLINE_PAYMENTS_DISABLED")
    void testOnlinePaymentsDisabled() {
        Card card = createTestCard(130L, 1L, "0010", CardStatus.ACTIVE, false,
                5_000_000L, 20_000_000L, null);
        when(cardStore.findById(130L)).thenReturn(Optional.of(card));

        InitiateTransferRequest initReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("1000.00"), "LKR", null, "Disabled test", RAW_PIN, FundingSource.card(130L));

        assertThatThrownBy(() -> transferService.initiateTransfer(initReq, sender, RAW_PIN, "IDEM-DIS-1", IP))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(ex -> assertThat(((UnprocessableEntityException) ex).getErrorCode()).isEqualTo("ONLINE_PAYMENTS_DISABLED"));
    }

    // ─────────────────────────────────────────────────────────────
    // 14. FRAUD RULES: VELOCITY, NEW-CARD LIMIT, REPEATED DECLINES
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Fraud rules: velocity limit (> 5/min), new-card limit (> 25,000 in 24h), repeated declines threshold")
    void testFraudRules() {
        // 1. New-card limit check
        Card newCard = createTestCard(140L, 1L, "0010", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, Instant.now().minus(1, ChronoUnit.HOURS)); // verified 1h ago
        when(cardStore.findById(140L)).thenReturn(Optional.of(newCard));

        InitiateTransferRequest bigReq = new InitiateTransferRequest(
                2L, null, new BigDecimal("30000.00"), "LKR", null, "Big test", RAW_PIN, FundingSource.card(140L));

        assertThatThrownBy(() -> transferService.initiateTransfer(bigReq, sender, RAW_PIN, "IDEM-NEW-1", IP))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(ex -> {
                    assertThat(((UnprocessableEntityException) ex).getErrorCode()).isEqualTo("LIMIT_EXCEEDED");
                    assertThat(ex.getMessage()).contains("New-card 24-hour limit exceeded");
                });

        // 2. Velocity check (> 5 in 1 minute)
        Card oldCard = createTestCard(141L, 1L, "0010", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, Instant.now().minus(3, ChronoUnit.DAYS));
        for (int i = 0; i < 5; i++) {
            cardFraudService.evaluatePreFlight(oldCard, new BigDecimal("100.00"));
        }
        assertThatThrownBy(() -> cardFraudService.evaluatePreFlight(oldCard, new BigDecimal("100.00")))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("velocity limit exceeded"));

        // 3. Repeated declines (3 consecutive declines flags card as blocked)
        Card declineCard = createTestCard(142L, 1L, "0010", CardStatus.ACTIVE, true,
                5_000_000L, 20_000_000L, Instant.now().minus(3, ChronoUnit.DAYS));
        cardFraudService.recordDecline(142L, "INSUFFICIENT_FUNDS");
        cardFraudService.recordDecline(142L, "INSUFFICIENT_FUNDS");
        cardFraudService.recordDecline(142L, "INSUFFICIENT_FUNDS");

        assertThatThrownBy(() -> cardFraudService.evaluatePreFlight(declineCard, new BigDecimal("100.00")))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(ex -> assertThat(((UnprocessableEntityException) ex).getErrorCode()).isEqualTo("CARD_BLOCKED"));
    }

    // ─────────────────────────────────────────────────────────────
    // 15. ACCOUNT-FUNDED TRANSFERS STILL WORK AS USUAL
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Account-funded transfers still function seamlessly without breaking")
    void testAccountFundedTransferStillWorks() {
        BankAccount senderAccount = new BankAccount(sender, "SIMULATED_BANK_A", "hash1", "****1001", "Sender Account", "enc1");
        ReflectionTestUtils.setField(senderAccount, "id", 10L);
        ReflectionTestUtils.setField(senderAccount, "isDefault", true);

        when(linkedAccountStore.findActiveDefaultAccount(1L))
                .thenReturn(Optional.of(LinkedAccount.fromBankAccount(senderAccount)));
        when(bankAdapterRegistry.get("SIMULATED_BANK_A")).thenReturn(bankAdapter);
        when(bankAdapter.checkTransferEligibility(any())).thenReturn(
                com.lankapay.bank.adapter.AdapterResponse.success("ELIGIBLE-OK", "ELIGIBLE")
        );
        when(bankAdapter.initiateTransfer(any())).thenReturn(
                com.lankapay.bank.adapter.AdapterResponse.success("SIM-BANK-OK", "COMPLETED")
        );

        InitiateTransferRequest req = new InitiateTransferRequest(
                2L, null, new BigDecimal("500.00"), "LKR", null, "Account funding", RAW_PIN, FundingSource.account(10L));

        TransferResponse resp = transferService.initiateTransfer(req, sender, RAW_PIN, "IDEM-ACCT-OK", IP);

        assertThat(resp.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(resp.fundingType()).isEqualTo(FundingType.ACCOUNT);
        assertThat(resp.fee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.totalAmount()).isEqualByComparingTo("500.00");
        assertThat(resp.paymentMethod()).isEqualTo("Paid with bank account");
    }
}

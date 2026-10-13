package com.lankapay.simvault.e2e;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardKind;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.card.service.CardRevealRateLimiter;
import com.lankapay.card.service.CardService;
import com.lankapay.card.service.CardSuspiciousPatternDetector;
import com.lankapay.card.web.dto.CreateCardRequest;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.identity.otp.OtpProvider;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.simvault.SimulatedCardVault;
import com.lankapay.transfer.domain.*;
import com.lankapay.transfer.service.*;
import com.lankapay.transfer.web.dto.*;
import com.lankapay.virtualcard.provider.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-End Demo Journey Test matching Section 24 demo script & threat model:
 *
 * 1. Add test card (Simulated Vault tokenization -> Link Card with consent).
 * 2. Verify with bank OTP (Card transitions PENDING_VERIFICATION -> ACTIVE).
 * 3. Pay from the card (Quote with simulated fee -> Initiate transfer COMPLETED).
 * 4. See the receipt (Card last4, simulated brand label, breakdown).
 * 5. Freeze the card (ACTIVE -> FROZEN).
 * 6. Attempt payment (Declined with CARD_FROZEN).
 * 7. Unfreeze the card (Step-up PIN -> ACTIVE).
 * 8. Remove the card (Step-up PIN -> REMOVED).
 * 9. Security Audit: Scan all log events, memory, and database stores to prove that
 *    NO PAN (Primary Account Number) or CVV was ever logged, cached, or persisted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("E2E Card Demo Journey & PII/Secret Scan Test")
public class CardDemoE2ETest {

    private static final String PAN_PREFIX = "990000000000";
    private static final String TEST_PAN   = PAN_PREFIX + "0010"; // Test Approved (ALWAYS_APPROVED)
    private static final String TEST_EXP_M = "12";
    private static final String TEST_EXP_Y = "27";
    private static final String TEST_CVV   = "111";
    private static final String TEST_NAME  = "Kasun Perera";
    private static final String RAW_PIN    = "123456";
    private static final String CLIENT_IP  = "192.168.1.100";

    // Regex matching any sequence of 13 to 19 digits (typical PAN length)
    private static final Pattern PAN_PATTERN = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|99[0-9]{11,17}|[1-9][0-9]{12,18})\\b");

    private ListAppender<ILoggingEvent> logAppender;
    private Logger rootLogger;

    private SimulatedCardVault vault;
    private CardService cardService;
    private TransferService transferService;

    // In-memory data stores
    private final Map<Long, Card> inMemoryCards = new ConcurrentHashMap<>();
    private final Map<Long, TransactionRecord> inMemoryTxs = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyRecordModel> inMemoryIdem = new ConcurrentHashMap<>();
    private final List<String> recordedAuditEvents = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong cardIdGen = new AtomicLong(10L);
    private final AtomicLong txIdGen = new AtomicLong(100L);

    @Mock OtpProvider otpProvider;
    @Mock CardStore cardStore;
    @Mock TransactionStore transactionStore;
    @Mock LinkedAccountStore linkedAccountStore;
    @Mock UserStore userStore;
    @Mock BankAdapterRegistry bankAdapterRegistry;
    @Mock NotificationService notificationService;
    @Mock AuditService auditService;

    private User sender;
    private User payee;
    private BankAccount payeeAccount;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // 1. Attach log appender to root logger to capture ALL log statements across all packages
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);

        inMemoryCards.clear();
        inMemoryTxs.clear();
        inMemoryIdem.clear();
        recordedAuditEvents.clear();

        passwordEncoder = new BCryptPasswordEncoder();
        String hashedPassword = passwordEncoder.encode(RAW_PIN);

        // 2. Setup users using User entity and ReflectionTestUtils (identical to CardTransferTest)
        sender = new User("+94771234567", "Kasun Perera", "199012345678", hashedPassword);
        ReflectionTestUtils.setField(sender, "id", 1L);
        ReflectionTestUtils.setField(sender, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(sender, "role", UserRole.CUSTOMER);

        payee = new User("+94777654321", "Nimal Silva", "199112345678", hashedPassword);
        ReflectionTestUtils.setField(payee, "id", 2L);
        ReflectionTestUtils.setField(payee, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(payee, "role", UserRole.CUSTOMER);

        payeeAccount = new BankAccount(payee, "SIMULATED_BANK_B", "hash2", "****2002", "Payee Account", "enc2");
        ReflectionTestUtils.setField(payeeAccount, "id", 20L);
        ReflectionTestUtils.setField(payeeAccount, "isDefault", true);

        when(userStore.findUserById(1L)).thenReturn(Optional.of(UserAccount.fromUser(sender)));
        when(userStore.findUserById(2L)).thenReturn(Optional.of(UserAccount.fromUser(payee)));

        when(linkedAccountStore.findActiveDefaultAccount(2L))
                .thenReturn(Optional.of(LinkedAccount.fromBankAccount(payeeAccount)));

        // 3. CardStore mock backing map
        when(cardStore.save(any(Card.class))).thenAnswer(inv -> {
            Card c = inv.getArgument(0);
            long id = c.id() != null ? c.id() : cardIdGen.incrementAndGet();
            Card saved = new Card(
                    id, c.userId(), c.linkedBankAccountId(), c.cardToken(), c.last4(),
                    c.expMonth(), c.expYear(), c.brandLabel(), c.nickname(), c.cardKind(),
                    c.status(), c.onlinePaymentsEnabled(), c.perPaymentLimitMinor(), c.dailyLimitMinor(),
                    c.createdAt() != null ? c.createdAt() : Instant.now(), Instant.now(),
                    c.verifiedAt(), c.removedAt(), (c.version() != null ? c.version() : 0L) + 1,
                    c.challengeRef(), c.simulated()
            );
            inMemoryCards.put(id, saved);
            return saved;
        });

        when(cardStore.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(inMemoryCards.get(inv.getArgument(0))));
        when(cardStore.findByIdAndUserId(anyLong(), anyLong())).thenAnswer(inv -> {
            Long cid = inv.getArgument(0);
            Long uid = inv.getArgument(1);
            Card c = inMemoryCards.get(cid);
            return (c != null && c.userId().equals(uid)) ? Optional.of(c) : Optional.empty();
        });

        // 4. TransactionStore mock backing map
        when(transactionStore.saveIdempotencyRecord(any())).thenAnswer(inv -> {
            IdempotencyRecordModel rec = inv.getArgument(0);
            inMemoryIdem.put(rec.idempotencyKey(), rec);
            return rec;
        });
        when(transactionStore.findIdempotencyRecord(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(inMemoryIdem.get(inv.getArgument(0))));
        when(transactionStore.findTransferByIdempotencyKey(anyString()))
                .thenAnswer(inv -> inMemoryTxs.values().stream()
                        .filter(tx -> inv.getArgument(0).equals(tx.idempotencyKey()))
                        .findFirst());
        when(transactionStore.createTransferWithIdempotency(any(), any(), any())).thenAnswer(inv -> {
            TransactionRecord tr = inv.getArgument(0);
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
        when(transactionStore.findTransferById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(inMemoryTxs.get(inv.getArgument(0))));
        when(transactionStore.getCardDailySpentMinor(anyLong(), any())).thenReturn(0L);

        when(transactionStore.transitionTransferStatus(anyLong(), any(), any(), any(), any(), any(), any(), anyLong()))
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

        // Audit service recorder
        doAnswer(inv -> {
            recordedAuditEvents.add(inv.getArgument(1) + ": " + inv.getArgument(4));
            return null;
        }).when(auditService).log(any(), any(), any(), any(), any(), any());

        // 5. Initialize SimulatedCardVault with test encryption key and dependencies
        String testKey = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        vault = new SimulatedCardVault(
                testKey, 15L, 3L, 5L, 5, 15L,
                otpProvider, userStore, auditService
        );

        // 6. Initialize CardService
        cardService = new CardService(
                cardStore,
                vault,
                linkedAccountStore,
                userStore,
                passwordEncoder,
                new CardRevealRateLimiter(5),
                new CardSuspiciousPatternDetector(notificationService, auditService),
                notificationService,
                auditService
        );

        // 7. Initialize TransferService with real limits & card fraud checking
        DailyLimitService dailyLimitService = new DailyLimitService(transactionStore);
        CardDailyLimitService cardDailyLimitService = new CardDailyLimitService(transactionStore);
        CardTransferFraudService cardFraudService = new CardTransferFraudService();
        IdempotencyService idempotencyService = new IdempotencyService(transactionStore, new ObjectMapper());

        TransferProcessor transferProcessor = new TransferProcessor(
                transactionStore,
                bankAdapterRegistry,
                dailyLimitService,
                auditService,
                notificationService,
                vault,
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
                vault,
                cardDailyLimitService,
                cardFraudService
        );
    }

    @AfterEach
    void tearDown() {
        if (rootLogger != null && logAppender != null) {
            rootLogger.detachAppender(logAppender);
        }
    }

    @Test
    @DisplayName("Complete Demo Flow: Add -> Verify OTP -> Pay -> Receipt -> Freeze -> Attempt (Declined) -> Unfreeze -> Remove -> Scan Logs & DB")
    void executeFullCardDemoJourneyAndVerifyZeroPanLeakage() {
        // =====================================================================
        // STEP 1: Add Test Card
        // =====================================================================
        TokenizeResponse tokenResult = vault.tokenize(new TokenizeRequest(
                TEST_PAN, TEST_EXP_M, TEST_EXP_Y, TEST_CVV, TEST_NAME, 1L, CLIENT_IP
        ));

        assertThat(tokenResult.cardToken()).isNotBlank();
        assertThat(tokenResult.last4()).isEqualTo("0010");

        CreateCardRequest linkRequest = new CreateCardRequest(
                tokenResult.cardToken(),
                "My Test Card",
                null, // standalone test card
                true, // consentGiven
                RAW_PIN
        );

        Card addedCard = cardService.linkCard(1L, linkRequest, RAW_PIN, CLIENT_IP);
        assertThat(addedCard.id()).isNotNull();
        assertThat(addedCard.last4()).isEqualTo("0010");
        assertThat(addedCard.status()).isEqualTo(CardStatus.PENDING_VERIFICATION);
        assertThat(addedCard.challengeRef()).isNotBlank();

        Long cardId = addedCard.id();

        // =====================================================================
        // STEP 2: Verify with Bank OTP
        // =====================================================================
        org.mockito.ArgumentCaptor<String> otpCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(otpProvider).send(any(), any(), otpCaptor.capture(), any(), anyLong());
        String sentOtp = otpCaptor.getValue();
        assertThat(sentOtp).matches("\\d{6}");

        Card verifiedCard = cardService.verifyCard(cardId, 1L, sentOtp, CLIENT_IP);
        assertThat(verifiedCard.status()).isEqualTo(CardStatus.ACTIVE);

        // =====================================================================
        // STEP 3: Pay from the Card
        // =====================================================================
        // 3a. Quote check
        QuoteRequest quoteRequest = new QuoteRequest(2L, null, new BigDecimal("1000.00"), "LKR", FundingSource.card(cardId));
        QuoteResponse quote = transferService.getQuote(quoteRequest, sender);

        assertThat(quote.fee()).isEqualByComparingTo("15.00"); // 1.5% simulated fee
        assertThat(quote.totalAmount()).isEqualByComparingTo("1015.00");
        assertThat(quote.feeLabel()).isEqualTo("Simulated Card Processing Fee (1.5%)");
        assertThat(quote.fundingSource().type()).isEqualTo(FundingType.CARD);

        // 3b. Initiate payment
        InitiateTransferRequest payRequest = new InitiateTransferRequest(
                2L, null, new BigDecimal("1000.00"), "LKR", null, "Demo Coffee", RAW_PIN, FundingSource.card(cardId)
        );

        TransferResponse transfer = transferService.initiateTransfer(payRequest, sender, RAW_PIN, "IDEM-DEMO-PAY-1", CLIENT_IP);

        assertThat(transfer.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transfer.fundingType()).isEqualTo(FundingType.CARD);
        assertThat(transfer.cardId()).isEqualTo(cardId);
        assertThat(transfer.cardLast4()).isEqualTo("0010");
        assertThat(transfer.cardKind()).isEqualTo(CardKind.LINKED);
        assertThat(transfer.fee()).isEqualByComparingTo("15.00");
        assertThat(transfer.totalAmount()).isEqualByComparingTo("1015.00");
        assertThat(transfer.paymentMethod()).isEqualTo("Paid with card •••• 0010 (TEST CARD)");

        // =====================================================================
        // STEP 4: See the Receipt
        // =====================================================================
        ReceiptResponse receipt = transferService.getTransferReceipt(transfer.transferId(), 1L);
        assertThat(receipt.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(receipt.amount()).isEqualByComparingTo("1000.00");
        assertThat(receipt.fee()).isEqualByComparingTo("15.00");
        assertThat(receipt.totalDebited()).isEqualByComparingTo("1015.00");
        assertThat(receipt.cardLast4()).isEqualTo("0010");
        assertThat(receipt.cardKind()).isEqualTo(CardKind.LINKED);
        assertThat(receipt.paymentMethod()).isEqualTo("Paid with card •••• 0010 (TEST CARD)");

        // =====================================================================
        // STEP 5: Freeze Card
        // =====================================================================
        Card frozenCard = cardService.freezeCard(cardId, 1L, CLIENT_IP);
        assertThat(frozenCard.status()).isEqualTo(CardStatus.FROZEN);

        // =====================================================================
        // STEP 6: Attempt Payment (Declined due to CARD_FROZEN)
        // =====================================================================
        InitiateTransferRequest frozenPayRequest = new InitiateTransferRequest(
                2L, null, new BigDecimal("500.00"), "LKR", null, "Should fail", RAW_PIN, FundingSource.card(cardId)
        );

        assertThatThrownBy(() -> transferService.initiateTransfer(frozenPayRequest, sender, RAW_PIN, "IDEM-FROZEN-FAIL", CLIENT_IP))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("Card is frozen");

        // =====================================================================
        // STEP 7: Unfreeze Card
        // =====================================================================
        Card unfrozenCard = cardService.unfreezeCard(cardId, 1L, RAW_PIN, null, CLIENT_IP);
        assertThat(unfrozenCard.status()).isEqualTo(CardStatus.ACTIVE);

        // =====================================================================
        // STEP 8: Remove Card
        // =====================================================================
        cardService.removeCard(cardId, 1L, RAW_PIN, CLIENT_IP);
        Card removedCard = inMemoryCards.get(cardId);
        assertThat(removedCard.status()).isEqualTo(CardStatus.REMOVED);

        // =====================================================================
        // STEP 9: Security Audit — Scan all logs and DB for PAN and CVV
        // =====================================================================
        // 9a. Verify logs
        List<String> logMessages = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertThat(logMessages).isNotEmpty();

        for (String msg : logMessages) {
            assertThat(msg)
                    .as("Log message must NOT contain plaintext PAN: %s", msg)
                    .doesNotContain(TEST_PAN);

            // Verify no 13-19 digit card pattern matches in log messages
            var matcher = PAN_PATTERN.matcher(msg);
            while (matcher.find()) {
                String candidate = matcher.group();
                assertThat(isLuhnValid(candidate))
                        .as("Log message must NOT contain any Luhn-valid card number: %s in '%s'", candidate, msg)
                        .isFalse();
            }

            // Verify CVV context does not leak "111"
            assertThat(msg)
                    .as("Log message must NOT contain raw CVV: %s", msg)
                    .doesNotContainPattern("(?i)cvv[\\s:=]+111");
        }

        // 9b. Verify database / stored Card records
        for (Card storedCard : inMemoryCards.values()) {
            assertThat(storedCard.last4()).isEqualTo("0010");
            assertThat(storedCard.cardToken()).doesNotContain(TEST_PAN);
            assertThat(storedCard.toString()).doesNotContain(TEST_PAN);
            assertThat(storedCard.toString()).doesNotContain(TEST_CVV);
        }

        // 9c. Verify database / stored Transaction records
        for (TransactionRecord tx : inMemoryTxs.values()) {
            assertThat(tx.cardLast4()).isEqualTo("0010");
            assertThat(tx.toString()).doesNotContain(TEST_PAN);
            assertThat(tx.toString()).doesNotContain(TEST_CVV);
        }

        // 9d. Verify recorded audit events
        for (String auditMsg : recordedAuditEvents) {
            assertThat(auditMsg).doesNotContain(TEST_PAN);
            assertThat(auditMsg).doesNotContain(TEST_CVV);
        }
    }

    private boolean isLuhnValid(String number) {
        int[] digits = new int[number.length()];
        for (int i = 0; i < number.length(); i++) {
            digits[i] = Character.getNumericValue(number.charAt(i));
        }
        int total = 0;
        boolean doubleDigit = false;
        for (int i = digits.length - 1; i >= 0; i--) {
            int d = digits[i];
            if (doubleDigit) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            total += d;
            doubleDigit = !doubleDigit;
        }
        return total % 10 == 0;
    }
}

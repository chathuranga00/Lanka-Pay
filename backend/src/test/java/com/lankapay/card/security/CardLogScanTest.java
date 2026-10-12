package com.lankapay.card.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.domain.BankAccountStatus;
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
import com.lankapay.card.web.dto.PatchCardRequest;
import com.lankapay.card.web.dto.VirtualCardIssueRequest;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.virtualcard.provider.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Security test scanning all log output during card lifecycle operations
 * to prove that NO PAN (Primary Account Number) or CVV ever appears in logs.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PCI-DSS Security: Log-scan test proving no PAN or CVV appears in logs")
class CardLogScanTest {

    private static final String TEST_PAN = "9900000000000010";
    private static final String TEST_CVV = "789";

    // Regex matching any sequence of 13 to 19 digits (typical PAN length)
    private static final Pattern PAN_PATTERN = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|99[0-9]{11,17}|[0-9]{13,19})\\b");

    // Regex specifically checking for CVV:xxx or raw cvv
    private static final Pattern CVV_PATTERN = Pattern.compile("(?i)(?:cvv|cvc|cvn|security[_-]?code)[\\s:=]+(?:'|\")?([0-9]{3,4})");

    private ListAppender<ILoggingEvent> listAppender;
    private Logger rootLogger;

    @Mock CardStore                      cardStore;
    @Mock CardProvider                   cardProvider;
    @Mock LinkedAccountStore             linkedAccountStore;
    @Mock UserStore                      userStore;
    @Mock PasswordEncoder                passwordEncoder;
    @Mock CardRevealRateLimiter          revealRateLimiter;
    @Mock CardSuspiciousPatternDetector  suspiciousPatternDetector;
    @Mock NotificationService            notificationService;
    @Mock AuditService                   auditService;

    CardService service;

    @BeforeEach
    void setUp() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);

        service = new CardService(
                cardStore,
                cardProvider,
                linkedAccountStore,
                userStore,
                passwordEncoder,
                revealRateLimiter,
                suspiciousPatternDetector,
                notificationService,
                auditService
        );
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    @DisplayName("Log-scan across all card lifecycle operations proves zero leaks of PAN or CVV")
    void testNoPanOrCvvInLogsAcrossAllOperations() {
        Long userId = 1L;
        Long cardId = 100L;
        String cardToken = "token-pci-test-456";

        UserAccount user = new UserAccount(userId, "+94771234567", "Nimal Silva", "199212345678",
                "hashedPin", UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now());
        when(userStore.findUserById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        // 1. Link card
        when(cardProvider.getTokenInfo(cardToken, userId)).thenReturn(
                new CardTokenInfo(cardToken, "0010", "12", "29", "TEST CARD", userId,
                        CardScenario.ALWAYS_APPROVED, false, true, Instant.now().plusSeconds(900), Instant.now()));
        when(cardProvider.startOwnershipChallenge(cardToken, userId, "127.0.0.1")).thenReturn("chall-123");
        when(cardStore.save(any())).thenAnswer(inv -> ((Card) inv.getArgument(0)).withId(cardId));

        service.linkCard(userId, new CreateCardRequest(cardToken, "Shopping Card", null, true, "123456"), null, "127.0.0.1");

        // 2. Verify card
        Card pendingCard = new Card(cardId, userId, null, cardToken, "0010", 12, 2029, "TEST CARD",
                "Shopping Card", CardKind.LINKED, CardStatus.PENDING_VERIFICATION, true, Card.DEFAULT_PER_PAYMENT_LIMIT_MINOR,
                Card.DEFAULT_DAILY_LIMIT_MINOR, Instant.now(), Instant.now(), null, null, 1L, "chall-123", true);
        when(cardStore.findByIdAndUserId(cardId, userId)).thenReturn(Optional.of(pendingCard));
        when(cardProvider.confirmChallenge("chall-123", userId, "112233"))
                .thenReturn(CardProviderResponse.ok("Confirmed"));

        service.verifyCard(cardId, userId, "112233", "127.0.0.1");

        // 3. Patch limits & freeze & unfreeze
        Card activeCard = pendingCard.withStatus(CardStatus.ACTIVE);
        when(cardStore.findByIdAndUserId(cardId, userId)).thenReturn(Optional.of(activeCard));

        service.patchCard(cardId, userId, new PatchCardRequest("New Nick", 2_000_000L, 10_000_000L, true, null), null, "127.0.0.1");
        service.freezeCard(cardId, userId, "127.0.0.1");

        Card frozenCard = activeCard.withStatus(CardStatus.FROZEN);
        when(cardStore.findByIdAndUserId(cardId, userId)).thenReturn(Optional.of(frozenCard));
        service.unfreezeCard(cardId, userId, "123456", null, "127.0.0.1");

        // 4. Issue virtual card and reveal details
        when(linkedAccountStore.findAccountByIdAndUserId(500L, userId)).thenReturn(
                Optional.of(new LinkedAccount(500L, userId, "SIM_BANK", "hash", "****1234", "Name",
                        "Label", true, true, "enc", BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now())));
        when(cardProvider.issueVirtualCard(userId)).thenReturn(
                new CardTokenInfo("virt-tok", "0010", "12", "29", "TEST CARD", userId,
                        CardScenario.ALWAYS_APPROVED, false, true, Instant.now(), Instant.now()));

        Card virtualCard = service.issueVirtualCard(userId, new VirtualCardIssueRequest(500L, "Virt", "123456"), null, "127.0.0.1");

        when(cardStore.findByIdAndUserId(cardId, userId)).thenReturn(Optional.of(virtualCard.withStatus(CardStatus.ACTIVE)));
        when(cardProvider.revealVirtualCard(virtualCard.cardToken(), userId)).thenReturn(
                new CardRevealDetails(virtualCard.cardToken(), TEST_PAN, TEST_CVV, "12", "29", "TEST CARD", 30, true));

        service.revealCard(cardId, userId, "123456", null, "127.0.0.1");

        // 5. Remove card
        service.removeCard(cardId, userId, "127.0.0.1");

        // Now assert that NONE of the logged messages contain the raw PAN or CVV
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).isNotEmpty();

        for (ILoggingEvent event : logs) {
            String formattedMessage = event.getFormattedMessage();

            // Strict check 1: Exact test PAN must never appear
            assertThat(formattedMessage)
                    .as("Log message must NOT contain raw PAN: %s", formattedMessage)
                    .doesNotContain(TEST_PAN);

            // Strict check 2: Regex PAN pattern must never match
            boolean containsPan = PAN_PATTERN.matcher(formattedMessage).find();
            assertThat(containsPan)
                    .as("Log message matched PAN regex: %s", formattedMessage)
                    .isFalse();

            // Strict check 3: CVV must not be logged
            boolean containsCvv = CVV_PATTERN.matcher(formattedMessage).find();
            assertThat(containsCvv)
                    .as("Log message matched CVV pattern: %s", formattedMessage)
                    .isFalse();
        }
    }
}

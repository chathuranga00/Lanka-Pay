package com.lankapay.card.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardKind;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.card.web.dto.CreateCardRequest;
import com.lankapay.card.web.dto.PatchCardRequest;
import com.lankapay.card.web.dto.VirtualCardIssueRequest;
import com.lankapay.common.exception.*;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.virtualcard.provider.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardService unit tests")
class CardServiceTest {

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

    static final Long   USER_ID       = 1L;
    static final Long   OTHER_USER_ID = 2L;
    static final Long   CARD_ID       = 100L;
    static final Long   BANK_ACCT_ID  = 500L;
    static final String CARD_TOKEN    = "test-token-123";
    static final String CHALLENGE_REF = "chall-ref-456";
    static final String CORRECT_PIN   = "123456";
    static final String WRONG_PIN     = "654321";
    static final String CORRECT_OTP   = "112233";
    static final String WRONG_OTP     = "999999";
    static final String IP            = "127.0.0.1";

    @BeforeEach
    void setUp() {
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

    UserAccount userAccount(Long id) {
        return new UserAccount(id, "+94771234567", "Kamal Perera", "199012345678", "$2a$12$hashedPassword",
                UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now());
    }

    LinkedAccount activeBankAccount(Long acctId, Long userId) {
        return new LinkedAccount(acctId, userId, "SIMULATED_BANK_A", "hash", "****1234",
                "Kamal Perera", "Savings", true, true, "enc", BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now());
    }

    Card sampleCard(Long id, Long userId, CardKind kind, CardStatus status) {
        return new Card(
                id,
                userId,
                BANK_ACCT_ID,
                CARD_TOKEN,
                "0010",
                12,
                2028,
                "TEST CARD",
                "My Card",
                kind,
                status,
                true,
                Card.DEFAULT_PER_PAYMENT_LIMIT_MINOR,
                Card.DEFAULT_DAILY_LIMIT_MINOR,
                Instant.now(),
                Instant.now(),
                status == CardStatus.ACTIVE ? Instant.now() : null,
                status == CardStatus.REMOVED ? Instant.now() : null,
                1L,
                CHALLENGE_REF,
                true
        );
    }

    // ── Scenario 1: Link success ───────────────────────────────

    @Nested
    @DisplayName("linkCard()")
    class LinkCardTests {

        @Test
        @DisplayName("1. Link success – initiates challenge, saves card as PENDING_VERIFICATION and records consent")
        void linkSuccess() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(passwordEncoder.matches(CORRECT_PIN, "$2a$12$hashedPassword")).thenReturn(true);
            when(cardProvider.getTokenInfo(CARD_TOKEN, USER_ID)).thenReturn(
                    new CardTokenInfo(CARD_TOKEN, "0010", "12", "28", "TEST CARD", USER_ID,
                            CardScenario.ALWAYS_APPROVED, false, true, Instant.now().plusSeconds(900), Instant.now()));
            when(linkedAccountStore.findAccountByIdAndUserId(BANK_ACCT_ID, USER_ID))
                    .thenReturn(Optional.of(activeBankAccount(BANK_ACCT_ID, USER_ID)));
            when(cardProvider.startOwnershipChallenge(CARD_TOKEN, USER_ID, IP)).thenReturn(CHALLENGE_REF);
            when(cardStore.save(any())).thenAnswer(inv -> ((Card) inv.getArgument(0)).withId(CARD_ID));

            CreateCardRequest request = new CreateCardRequest(CARD_TOKEN, "My Card", BANK_ACCT_ID, true, CORRECT_PIN);
            Card result = service.linkCard(USER_ID, request, null, IP);

            assertThat(result.id()).isEqualTo(CARD_ID);
            assertThat(result.status()).isEqualTo(CardStatus.PENDING_VERIFICATION);
            assertThat(result.challengeRef()).isEqualTo(CHALLENGE_REF);
            assertThat(result.last4()).isEqualTo("0010");

            verify(cardStore).saveConsent(eq(USER_ID), eq(CARD_ID), eq("LINK_CARD"), eq(true), eq(IP));
            verify(auditService).log(eq(USER_ID), eq("CARD_ADDED"), eq("CARD"), eq(CARD_ID), any(), eq(IP));
            verify(notificationService).notifyCardAdded(any());
        }

        @Test
        @DisplayName("Explicit consent is required – fails if false or missing")
        void explicitConsentRequired() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));

            CreateCardRequest request = new CreateCardRequest(CARD_TOKEN, "My Card", BANK_ACCT_ID, false, CORRECT_PIN);
            assertThatThrownBy(() -> service.linkCard(USER_ID, request, null, IP))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Explicit user consent is required");
        }

        @Test
        @DisplayName("4. Token of another user – refused by card provider")
        void tokenOfAnotherUserRefused() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(passwordEncoder.matches(CORRECT_PIN, "$2a$12$hashedPassword")).thenReturn(true);
            when(cardProvider.getTokenInfo(CARD_TOKEN, USER_ID))
                    .thenThrow(new AuthorizationException("CARD_TOKEN_WRONG_USER", "Card token does not belong to this user"));

            CreateCardRequest request = new CreateCardRequest(CARD_TOKEN, "My Card", null, true, CORRECT_PIN);
            assertThatThrownBy(() -> service.linkCard(USER_ID, request, null, IP))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("does not belong to this user");
        }

        @Test
        @DisplayName("5. Duplicate card – conflict error if active card exists")
        void duplicateCardRefused() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(passwordEncoder.matches(CORRECT_PIN, "$2a$12$hashedPassword")).thenReturn(true);
            when(cardProvider.getTokenInfo(CARD_TOKEN, USER_ID)).thenReturn(
                    new CardTokenInfo(CARD_TOKEN, "0010", "12", "28", "TEST CARD", USER_ID,
                            CardScenario.ALWAYS_APPROVED, false, true, Instant.now().plusSeconds(900), Instant.now()));
            when(cardProvider.startOwnershipChallenge(CARD_TOKEN, USER_ID, IP)).thenReturn(CHALLENGE_REF);
            when(cardStore.save(any())).thenThrow(new ConflictException("DUPLICATE_CARD", "An active card already exists with this token"));

            CreateCardRequest request = new CreateCardRequest(CARD_TOKEN, "My Card", null, true, CORRECT_PIN);
            assertThatThrownBy(() -> service.linkCard(USER_ID, request, null, IP))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already exists");
        }
    }

    // ── Scenario 2 & 3: OTP verification ───────────────────────

    @Nested
    @DisplayName("verifyCard()")
    class VerifyCardTests {

        @Test
        @DisplayName("OTP success – transitions to ACTIVE")
        void verifySuccess() {
            Card pendingCard = sampleCard(CARD_ID, USER_ID, CardKind.LINKED, CardStatus.PENDING_VERIFICATION);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(pendingCard));
            when(cardProvider.confirmChallenge(CHALLENGE_REF, USER_ID, CORRECT_OTP))
                    .thenReturn(CardProviderResponse.ok("Confirmed"));
            when(cardStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Card verified = service.verifyCard(CARD_ID, USER_ID, CORRECT_OTP, IP);

            assertThat(verified.status()).isEqualTo(CardStatus.ACTIVE);
            assertThat(verified.verifiedAt()).isNotNull();
            verify(auditService).log(eq(USER_ID), eq("CARD_VERIFIED"), eq("CARD"), eq(CARD_ID), any(), eq(IP));
            verify(notificationService).notifyCardVerified(any());
        }

        @Test
        @DisplayName("2. Wrong OTP – records failure and throws OtpException")
        void wrongOtpThrows() {
            Card pendingCard = sampleCard(CARD_ID, USER_ID, CardKind.LINKED, CardStatus.PENDING_VERIFICATION);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(pendingCard));
            when(cardProvider.confirmChallenge(CHALLENGE_REF, USER_ID, WRONG_OTP))
                    .thenThrow(OtpException.invalid());

            assertThatThrownBy(() -> service.verifyCard(CARD_ID, USER_ID, WRONG_OTP, IP))
                    .isInstanceOf(OtpException.class);

            verify(suspiciousPatternDetector).recordFailedVerification(any(), eq(CARD_ID), eq(IP));
        }

        @Test
        @DisplayName("3. OTP attempts limit and expiry – throws OtpException")
        void otpLimitAndExpiry() {
            Card pendingCard = sampleCard(CARD_ID, USER_ID, CardKind.LINKED, CardStatus.PENDING_VERIFICATION);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(pendingCard));
            when(cardProvider.confirmChallenge(CHALLENGE_REF, USER_ID, WRONG_OTP))
                    .thenThrow(OtpException.maxAttemptsReached());

            assertThatThrownBy(() -> service.verifyCard(CARD_ID, USER_ID, WRONG_OTP, IP))
                    .isInstanceOf(OtpException.class);

            verify(suspiciousPatternDetector).recordFailedVerification(any(), eq(CARD_ID), eq(IP));
        }
    }

    // ── Scenario 6: Freeze & Unfreeze ──────────────────────────

    @Nested
    @DisplayName("freezeCard() & unfreezeCard()")
    class FreezeUnfreezeTests {

        @Test
        @DisplayName("6a. Freeze active card succeeds without step-up")
        void freezeActiveCard() {
            Card activeCard = sampleCard(CARD_ID, USER_ID, CardKind.LINKED, CardStatus.ACTIVE);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(activeCard));
            when(cardStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Card frozen = service.freezeCard(CARD_ID, USER_ID, IP);

            assertThat(frozen.status()).isEqualTo(CardStatus.FROZEN);
            verify(auditService).log(eq(USER_ID), eq("CARD_FROZEN"), eq("CARD"), eq(CARD_ID), isNull(), eq(IP));
            verify(notificationService).notifyCardFrozen(any());
        }

        @Test
        @DisplayName("6b. Unfreeze requires valid step-up PIN")
        void unfreezeRequiresStepUp() {
            Card frozenCard = sampleCard(CARD_ID, USER_ID, CardKind.LINKED, CardStatus.FROZEN);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(frozenCard));

            // Without PIN -> throws STEP_UP_AUTH_REQUIRED
            assertThatThrownBy(() -> service.unfreezeCard(CARD_ID, USER_ID, null, null, IP))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Step-up authentication required");

            // With wrong PIN -> throws INVALID_PIN
            when(passwordEncoder.matches(WRONG_PIN, "$2a$12$hashedPassword")).thenReturn(false);
            assertThatThrownBy(() -> service.unfreezeCard(CARD_ID, USER_ID, WRONG_PIN, null, IP))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("Invalid PIN");

            // With correct PIN -> succeeds
            when(passwordEncoder.matches(CORRECT_PIN, "$2a$12$hashedPassword")).thenReturn(true);
            when(cardStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Card unfrozen = service.unfreezeCard(CARD_ID, USER_ID, CORRECT_PIN, null, IP);
            assertThat(unfrozen.status()).isEqualTo(CardStatus.ACTIVE);
            verify(notificationService).notifyCardUnfrozen(any());
        }
    }

    // ── Scenario 7: Limit raising rules ────────────────────────

    @Nested
    @DisplayName("patchCard() limit rules")
    class LimitRulesTests {

        @Test
        @DisplayName("7a. Lowering limits succeeds without step-up auth")
        void lowerLimitsWithoutStepUp() {
            Card activeCard = sampleCard(CARD_ID, USER_ID, CardKind.LINKED, CardStatus.ACTIVE);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(activeCard));
            when(cardStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Default limits are 5,000,000 / 20,000,000 minor units. Lower to 2,000,000 / 10,000,000
            PatchCardRequest request = new PatchCardRequest("New Nick", 2_000_000L, 10_000_000L, true, null);
            Card updated = service.patchCard(CARD_ID, USER_ID, request, null, IP);

            assertThat(updated.perPaymentLimitMinor()).isEqualTo(2_000_000L);
            assertThat(updated.dailyLimitMinor()).isEqualTo(10_000_000L);
            verify(passwordEncoder, never()).matches(any(), any());
            verify(notificationService).notifyCardLimitChanged(any());
        }

        @Test
        @DisplayName("7b. Raising limits requires step-up PIN")
        void raiseLimitsRequiresStepUp() {
            Card activeCard = sampleCard(CARD_ID, USER_ID, CardKind.LINKED, CardStatus.ACTIVE);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(activeCard));

            // Raising perPayment to 8,000,000 without PIN -> fails
            PatchCardRequest requestWithoutPin = new PatchCardRequest("New Nick", 8_000_000L, 20_000_000L, true, null);
            assertThatThrownBy(() -> service.patchCard(CARD_ID, USER_ID, requestWithoutPin, null, IP))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Step-up authentication required");

            // Raising with correct PIN -> succeeds
            when(passwordEncoder.matches(CORRECT_PIN, "$2a$12$hashedPassword")).thenReturn(true);
            when(cardStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PatchCardRequest requestWithPin = new PatchCardRequest("New Nick", 8_000_000L, 25_000_000L, true, CORRECT_PIN);
            Card updated = service.patchCard(CARD_ID, USER_ID, requestWithPin, null, IP);

            assertThat(updated.perPaymentLimitMinor()).isEqualTo(8_000_000L);
            assertThat(updated.dailyLimitMinor()).isEqualTo(25_000_000L);
        }

        @Test
        @DisplayName("7c. Limits exceeding system risk limits are rejected")
        void exceedRiskLimitsRejected() {
            Card activeCard = sampleCard(CARD_ID, USER_ID, CardKind.LINKED, CardStatus.ACTIVE);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(activeCard));

            // Exceeds MAX_PER_PAYMENT_LIMIT_MINOR (50,000,000)
            PatchCardRequest requestExceed = new PatchCardRequest(null, 60_000_000L, 80_000_000L, null, CORRECT_PIN);
            assertThatThrownBy(() -> service.patchCard(CARD_ID, USER_ID, requestExceed, null, IP))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("exceeds system maximum allowed risk limit");
        }
    }

    // ── Scenario 8: Reveal rules ───────────────────────────────

    @Nested
    @DisplayName("revealCard() rules")
    class RevealRulesTests {

        @Test
        @DisplayName("8a. Allowed for virtual cards with step-up auth")
        void revealAllowedForVirtual() {
            Card virtualCard = sampleCard(CARD_ID, USER_ID, CardKind.VIRTUAL, CardStatus.ACTIVE);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(virtualCard));
            when(passwordEncoder.matches(CORRECT_PIN, "$2a$12$hashedPassword")).thenReturn(true);
            String testPan = "990000000000" + "0010";
            when(cardProvider.revealVirtualCard(CARD_TOKEN, USER_ID)).thenReturn(
                    new CardRevealDetails(CARD_TOKEN, testPan, "123", "12", "29", "TEST CARD", 30, true));

            var response = service.revealCard(CARD_ID, USER_ID, CORRECT_PIN, null, IP);

            assertThat(response.pan()).isEqualTo(testPan);
            assertThat(response.cvv()).isEqualTo("123");
            assertThat(response.expiresInSeconds()).isEqualTo(30);

            verify(revealRateLimiter).checkLimit(USER_ID);
            verify(suspiciousPatternDetector).recordReveal(any(), eq(CARD_ID), eq(IP));
            verify(notificationService).notifyCardDetailsViewed(any());
            verify(auditService).log(eq(USER_ID), eq("CARD_DETAILS_VIEWED"), eq("CARD"), eq(CARD_ID), isNull(), eq(IP));
        }

        @Test
        @DisplayName("8b. Refused for linked cards with clear error")
        void revealRefusedForLinked() {
            Card linkedCard = sampleCard(CARD_ID, USER_ID, CardKind.LINKED, CardStatus.ACTIVE);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(linkedCard));
            when(passwordEncoder.matches(CORRECT_PIN, "$2a$12$hashedPassword")).thenReturn(true);

            assertThatThrownBy(() -> service.revealCard(CARD_ID, USER_ID, CORRECT_PIN, null, IP))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("permitted only for platform-issued virtual cards");
        }

        @Test
        @DisplayName("8c. Refused without step-up PIN")
        void revealRefusedWithoutStepUp() {
            Card virtualCard = sampleCard(CARD_ID, USER_ID, CardKind.VIRTUAL, CardStatus.ACTIVE);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(virtualCard));

            assertThatThrownBy(() -> service.revealCard(CARD_ID, USER_ID, null, null, IP))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Step-up authentication required");
        }

        @Test
        @DisplayName("8d. Rate limited requests are rejected")
        void revealRateLimited() {
            Card virtualCard = sampleCard(CARD_ID, USER_ID, CardKind.VIRTUAL, CardStatus.ACTIVE);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(virtualCard));
            when(passwordEncoder.matches(CORRECT_PIN, "$2a$12$hashedPassword")).thenReturn(true);
            doThrow(new RateLimitException("CARD_REVEAL_RATE_LIMITED", "Too many requests"))
                    .when(revealRateLimiter).checkLimit(USER_ID);

            assertThatThrownBy(() -> service.revealCard(CARD_ID, USER_ID, CORRECT_PIN, null, IP))
                    .isInstanceOf(RateLimitException.class);
        }
    }

    // ── Scenario 9: Removing a card ────────────────────────────

    @Nested
    @DisplayName("removeCard()")
    class RemoveCardTests {

        @Test
        @DisplayName("9. Removing card moves status to REMOVED and preserves records")
        void removeCardSuccess() {
            Card activeCard = sampleCard(CARD_ID, USER_ID, CardKind.LINKED, CardStatus.ACTIVE);
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(cardStore.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(activeCard));
            when(cardStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.removeCard(CARD_ID, USER_ID, IP);

            verify(cardStore).save(argThat(c -> c.status() == CardStatus.REMOVED && !c.onlinePaymentsEnabled()));
            verify(cardStore).saveConsent(eq(USER_ID), eq(CARD_ID), eq("REMOVE_CARD"), eq(true), eq(IP));
            verify(notificationService).notifyCardRemoved(any());
            verify(auditService).log(eq(USER_ID), eq("CARD_REMOVED"), eq("CARD"), eq(CARD_ID), isNull(), eq(IP));
        }
    }

    // ── Scenario 10: Object-level authorization ────────────────

    @Nested
    @DisplayName("Object-level authorization")
    class ObjectLevelAuthTests {

        @Test
        @DisplayName("10. Accessing another user's card returns 404 (ResourceNotFoundException)")
        void accessOtherUserCardReturns404() {
            when(userStore.findUserById(OTHER_USER_ID)).thenReturn(Optional.of(userAccount(OTHER_USER_ID)));
            // Scoped findByIdAndUserId returns empty for non-owned cards
            when(cardStore.findByIdAndUserId(CARD_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCard(CARD_ID, OTHER_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found");

            assertThatThrownBy(() -> service.freezeCard(CARD_ID, OTHER_USER_ID, IP))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found");

            assertThatThrownBy(() -> service.removeCard(CARD_ID, OTHER_USER_ID, IP))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found");
        }
    }

    // ── Virtual card issuance ──────────────────────────────────

    @Nested
    @DisplayName("issueVirtualCard()")
    class IssueVirtualCardTests {

        @Test
        @DisplayName("Issue virtual card tied to linked bank account")
        void issueVirtualCardSuccess() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(passwordEncoder.matches(CORRECT_PIN, "$2a$12$hashedPassword")).thenReturn(true);
            when(linkedAccountStore.findAccountByIdAndUserId(BANK_ACCT_ID, USER_ID))
                    .thenReturn(Optional.of(activeBankAccount(BANK_ACCT_ID, USER_ID)));
            when(cardProvider.issueVirtualCard(USER_ID)).thenReturn(
                    new CardTokenInfo("virt-token", "9910", "12", "29", "TEST CARD", USER_ID,
                            CardScenario.ALWAYS_APPROVED, false, true, Instant.now().plusSeconds(86400 * 365), Instant.now()));
            when(cardStore.save(any())).thenAnswer(inv -> ((Card) inv.getArgument(0)).withId(CARD_ID));

            VirtualCardIssueRequest request = new VirtualCardIssueRequest(BANK_ACCT_ID, "My Virtual", CORRECT_PIN);
            Card result = service.issueVirtualCard(USER_ID, request, null, IP);

            assertThat(result.id()).isEqualTo(CARD_ID);
            assertThat(result.cardKind()).isEqualTo(CardKind.VIRTUAL);
            assertThat(result.status()).isEqualTo(CardStatus.ACTIVE);
            assertThat(result.last4()).isEqualTo("9910");

            verify(cardStore).saveConsent(eq(USER_ID), eq(CARD_ID), eq("ISSUE_VIRTUAL_CARD"), eq(true), eq(IP));
            verify(notificationService).notifyCardAdded(any());
        }
    }

    // ── Expired card sweep ─────────────────────────────────────

    @Nested
    @DisplayName("expireOutdatedCards()")
    class ExpiredCardJobTests {

        @Test
        @DisplayName("Moves cards past expiry to EXPIRED status")
        void expireOutdatedCards() {
            Card expiredCard = sampleCard(CARD_ID, USER_ID, CardKind.LINKED, CardStatus.ACTIVE);
            when(cardStore.findCardsToProcessExpiration(2025, 10)).thenReturn(List.of(expiredCard));
            when(cardStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int count = service.expireOutdatedCards(LocalDate.of(2025, 10, 1));

            assertThat(count).isEqualTo(1);
            verify(cardStore).save(argThat(c -> c.status() == CardStatus.EXPIRED));
            verify(auditService).log(eq(USER_ID), eq("CARD_EXPIRED"), eq("CARD"), eq(CARD_ID), isNull(), eq("SYSTEM"));
        }
    }
}

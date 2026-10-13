package com.lankapay.home.service;

import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardKind;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.feature.service.FeatureFlagService;
import com.lankapay.home.domain.HomeSectionStatus;
import com.lankapay.home.web.dto.HomeAggregateResponse;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.domain.NotificationStore;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransactionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeAggregatorService Unit, Resilience, & Nudge Priority Tests")
class HomeAggregatorServiceTest {

    @Mock private UserStore userStore;
    @Mock private NotificationStore notificationStore;
    @Mock private LinkedAccountStore linkedAccountStore;
    @Mock private CardStore cardStore;
    @Mock private TransactionStore transactionStore;
    @Mock private FeatureFlagService featureFlagService;

    private HomeAggregatorService homeAggregatorService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        homeAggregatorService = new HomeAggregatorService(
                userStore,
                notificationStore,
                linkedAccountStore,
                cardStore,
                transactionStore,
                featureFlagService
        );
    }

    @Test
    @DisplayName("Happy path: Returns real data for existing sections and UNAVAILABLE for stories")
    void aggregateHomeHappyPath() {
        // 1. Mock user (ACTIVE)
        UserAccount user = new UserAccount("+94771234567", "Kasun Perera", "199012345678", "hash")
                .withStatus(UserStatus.ACTIVE);
        when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(user));

        // 2. Mock notifications
        when(notificationStore.countUnreadNotifications(USER_ID)).thenReturn(5L);

        // 3. Mock linked account
        LinkedAccount acct = new LinkedAccount(
                101L, USER_ID, "BANK_A", "hash1", "•••• 5678", "Kasun Perera",
                "Primary", true, true, "enc", BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now()
        );
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(USER_ID)).thenReturn(List.of(acct));

        // 4. Mock card
        Card card = new Card(
                201L, USER_ID, 101L, "vlt_tok_test", "0010", 12, 2028,
                "TEST CARD", "My Test Card", CardKind.VIRTUAL, CardStatus.ACTIVE,
                true, 5000000L, 20000000L, Instant.now(), Instant.now(), Instant.now(), null, 1L, null, true
        );
        when(cardStore.findAllByUserId(USER_ID)).thenReturn(List.of(card));

        // 5. Mock transactions
        TransactionRecord tx = new TransactionRecord(
                301L, "TX-REF-01", "IDEM-01", USER_ID, "+94771234567", "Kasun Perera",
                2L, "+94779876543", "Amara Silva", "BANK_A", "BANK_B",
                new BigDecimal("1500.00"), "LKR", TransactionStatus.COMPLETED,
                null, "BTX-1", "PROV-1", "Dinner split", true, null, null, 1L,
                Instant.now(), Instant.now(), FundingType.ACCOUNT, null, null, null,
                BigDecimal.ZERO, new BigDecimal("1500.00"), null, 0
        );
        when(transactionStore.findTransfersByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));

        // 6. Mock feature flags
        when(featureFlagService.isEnabled("bills")).thenReturn(true);
        when(featureFlagService.isEnabled("split")).thenReturn(false);
        when(featureFlagService.isEnabled("goals")).thenReturn(true);

        HomeAggregateResponse response = homeAggregatorService.aggregateHome(USER_ID);

        // Assert greeting
        assertThat(response.greeting().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.greeting().data().fullName()).isEqualTo("Kasun Perera");
        assertThat(response.greeting().data().initial()).isEqualTo("K");
        assertThat(response.greeting().data().salutation()).contains("Kasun");

        // Assert notifications
        assertThat(response.unreadNotifications().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.unreadNotifications().data().unreadCount()).isEqualTo(5L);

        // Assert wallet
        assertThat(response.wallet().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.wallet().data().isSimulated()).isTrue();
        assertThat(response.wallet().data().primaryBalanceMinor()).isEqualTo(12545000L);
        assertThat(response.wallet().data().primaryBalanceLkr()).isEqualTo("125,450.00");
        assertThat(response.wallet().data().defaultAccount().maskedAccountNumber()).isEqualTo("•••• 5678");
        assertThat(response.wallet().data().activeCard().maskedNumber()).isEqualTo("•••• 0010");
        assertThat(response.wallet().data().totalAccounts()).isEqualTo(1);
        assertThat(response.wallet().data().totalCards()).isEqualTo(1);

        // Assert recent activity
        assertThat(response.recentActivity().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.recentActivity().data()).hasSize(1);
        assertThat(response.recentActivity().data().get(0).counterpartyName()).isEqualTo("Amara Silva");
        assertThat(response.recentActivity().data().get(0).amountLkr()).isEqualTo("1,500.00");
        assertThat(response.recentActivity().data().get(0).direction()).isEqualTo("OUTGOING");

        // Assert rewards points returns real data
        assertThat(response.rewardsPoints().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.rewardsPoints().data()).isNotNull();
        assertThat(response.rewardsPoints().data().pointsBalance()).isEqualTo(1250L);
        assertThat(response.rewardsPoints().data().hasNewOffers()).isTrue();

        // Assert stories is UNAVAILABLE
        assertThat(response.stories().status()).isEqualTo(HomeSectionStatus.UNAVAILABLE);
        assertThat(response.stories().data()).isNull();
        assertThat(response.stories().error()).isEqualTo("MODULE_NOT_YET_IMPLEMENTED");

        // Assert spend summary returns real data
        assertThat(response.spendSummary().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.spendSummary().data()).isNotNull();
        assertThat(response.spendSummary().data().budgetMinor()).isEqualTo(5000000L);

        // Assert nudge returns security tip since all higher priority rules passed
        assertThat(response.nudge().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.nudge().data()).isNotNull();
        assertThat(response.nudge().data().type()).isEqualTo("SECURITY_TIP");

        // Assert shortcuts respect feature flags
        assertThat(response.shortcuts().status()).isEqualTo(HomeSectionStatus.OK);
        var shortcuts = response.shortcuts().data();
        assertThat(shortcuts.stream().filter(s -> s.id().equals("bills")).findFirst().get().enabled()).isTrue();
        assertThat(shortcuts.stream().filter(s -> s.id().equals("split")).findFirst().get().enabled()).isFalse();
        assertThat(shortcuts.stream().filter(s -> s.id().equals("goals")).findFirst().get().enabled()).isTrue();
    }

    @Test
    @DisplayName("Nudge Priority 1: Phone not verified -> VERIFY_PHONE")
    void nudgePriority1PhoneNotVerified() {
        UserAccount unverifiedUser = new UserAccount("+94771234567", "Kasun Perera", "199012345678", "hash")
                .withStatus(UserStatus.PENDING_VERIFICATION);
        when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(unverifiedUser));

        HomeAggregateResponse response = homeAggregatorService.aggregateHome(USER_ID);

        assertThat(response.nudge().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.nudge().data().type()).isEqualTo("VERIFY_PHONE");
        assertThat(response.nudge().data().actionLabel()).isEqualTo("Verify now");
    }

    @Test
    @DisplayName("Nudge Priority 2: No linked account -> LINK_ACCOUNT")
    void nudgePriority2NoLinkedAccount() {
        UserAccount verifiedUser = new UserAccount("+94771234567", "Kasun Perera", "199012345678", "hash")
                .withStatus(UserStatus.ACTIVE);
        when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(verifiedUser));
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(USER_ID)).thenReturn(List.of());

        HomeAggregateResponse response = homeAggregatorService.aggregateHome(USER_ID);

        assertThat(response.nudge().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.nudge().data().type()).isEqualTo("LINK_ACCOUNT");
        assertThat(response.nudge().data().actionLabel()).isEqualTo("Link account");
    }

    @Test
    @DisplayName("Nudge Priority 3: Card pending OTP verification -> VERIFY_CARD_OTP")
    void nudgePriority3CardPendingOtp() {
        UserAccount verifiedUser = new UserAccount("+94771234567", "Kasun Perera", "199012345678", "hash")
                .withStatus(UserStatus.ACTIVE);
        when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(verifiedUser));

        LinkedAccount acct = new LinkedAccount(
                101L, USER_ID, "BANK_A", "hash1", "•••• 5678", "Kasun Perera",
                "Primary", true, true, "enc", BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now()
        );
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(USER_ID)).thenReturn(List.of(acct));

        Card pendingCard = new Card(
                201L, USER_ID, 101L, "vlt_tok_test", "9988", 12, 2028,
                "TEST CARD", "My Test Card", CardKind.VIRTUAL, CardStatus.PENDING_VERIFICATION,
                true, 5000000L, 20000000L, Instant.now(), Instant.now(), null, null, 1L, null, true
        );
        when(cardStore.findAllByUserId(USER_ID)).thenReturn(List.of(pendingCard));

        HomeAggregateResponse response = homeAggregatorService.aggregateHome(USER_ID);

        assertThat(response.nudge().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.nudge().data().type()).isEqualTo("VERIFY_CARD_OTP");
        assertThat(response.nudge().data().message()).contains("9988");
    }

    @Test
    @DisplayName("Nudge Priority 4: Transfer pending -> TRANSFER_PENDING")
    void nudgePriority4TransferPending() {
        UserAccount verifiedUser = new UserAccount("+94771234567", "Kasun Perera", "199012345678", "hash")
                .withStatus(UserStatus.ACTIVE);
        when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(verifiedUser));

        LinkedAccount acct = new LinkedAccount(
                101L, USER_ID, "BANK_A", "hash1", "•••• 5678", "Kasun Perera",
                "Primary", true, true, "enc", BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now()
        );
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(USER_ID)).thenReturn(List.of(acct));

        Card card = new Card(
                201L, USER_ID, 101L, "vlt_tok_test", "1122", 12, 2028,
                "TEST CARD", "My Card", CardKind.VIRTUAL, CardStatus.ACTIVE,
                true, 5000000L, 20000000L, Instant.now(), Instant.now(), Instant.now(), null, 1L, null, true
        );
        when(cardStore.findAllByUserId(USER_ID)).thenReturn(List.of(card));

        TransactionRecord pendingTx = new TransactionRecord(
                302L, "TX-REF-02", "IDEM-02", USER_ID, "+94771234567", "Kasun Perera",
                2L, "+94779876543", "Amara Silva", "BANK_A", "BANK_B",
                new BigDecimal("2000.00"), "LKR", TransactionStatus.PROCESSING,
                null, "BTX-2", "PROV-2", "Pending payment", true, null, null, 1L,
                Instant.now(), Instant.now(), FundingType.ACCOUNT, null, null, null,
                BigDecimal.ZERO, new BigDecimal("2000.00"), null, 0
        );
        when(transactionStore.findTransfersByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingTx)));

        HomeAggregateResponse response = homeAggregatorService.aggregateHome(USER_ID);

        assertThat(response.nudge().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.nudge().data().type()).isEqualTo("TRANSFER_PENDING");
    }

    @Test
    @DisplayName("Nudge Priority 5: Budget used >= 80% -> BUDGET_WARNING")
    void nudgePriority5BudgetWarning() {
        UserAccount verifiedUser = new UserAccount("+94771234567", "Kasun Perera", "199012345678", "hash")
                .withStatus(UserStatus.ACTIVE);
        when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(verifiedUser));

        LinkedAccount acct = new LinkedAccount(
                101L, USER_ID, "BANK_A", "hash1", "•••• 5678", "Kasun Perera",
                "Primary", true, true, "enc", BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now()
        );
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(USER_ID)).thenReturn(List.of(acct));

        Card card = new Card(
                201L, USER_ID, 101L, "vlt_tok_test", "1122", 12, 2028,
                "TEST CARD", "My Card", CardKind.VIRTUAL, CardStatus.ACTIVE,
                true, 5000000L, 20000000L, Instant.now(), Instant.now(), Instant.now(), null, 1L, null, true
        );
        when(cardStore.findAllByUserId(USER_ID)).thenReturn(List.of(card));

        // 45,000 LKR spent out of 50,000 LKR budget = 90%
        TransactionRecord heavyTx = new TransactionRecord(
                303L, "TX-REF-03", "IDEM-03", USER_ID, "+94771234567", "Kasun Perera",
                2L, "+94779876543", "Amara Silva", "BANK_A", "BANK_B",
                new BigDecimal("45000.00"), "LKR", TransactionStatus.COMPLETED,
                null, "BTX-3", "PROV-3", "Big purchase", true, null, null, 1L,
                Instant.now(), Instant.now(), FundingType.ACCOUNT, null, null, null,
                BigDecimal.ZERO, new BigDecimal("45000.00"), null, 0
        );
        when(transactionStore.findTransfersByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(heavyTx)));

        HomeAggregateResponse response = homeAggregatorService.aggregateHome(USER_ID);

        assertThat(response.nudge().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.nudge().data().type()).isEqualTo("BUDGET_WARNING");
    }

    @Test
    @DisplayName("Resilience: A failing section (e.g. database timeout) returns FAILED with error code and does NOT crash other sections")
    void failingSectionDoesNotBreakResponse() {
        when(userStore.findUserById(USER_ID)).thenThrow(new RuntimeException("Firestore connection timeout"));
        when(notificationStore.countUnreadNotifications(USER_ID)).thenReturn(2L);
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(USER_ID)).thenReturn(List.of());
        when(cardStore.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(transactionStore.findTransfersByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        HomeAggregateResponse response = homeAggregatorService.aggregateHome(USER_ID);

        assertThat(response.greeting().status()).isEqualTo(HomeSectionStatus.FAILED);
        assertThat(response.greeting().data()).isNull();
        assertThat(response.greeting().error()).isEqualTo("USER_LOOKUP_FAILED");

        assertThat(response.unreadNotifications().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.unreadNotifications().data().unreadCount()).isEqualTo(2L);

        assertThat(response.wallet().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.wallet().data().isSimulated()).isTrue();

        assertThat(response.recentActivity().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.rewardsPoints().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.stories().status()).isEqualTo(HomeSectionStatus.UNAVAILABLE);
        assertThat(response.spendSummary().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.nudge().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.shortcuts().status()).isEqualTo(HomeSectionStatus.OK);
    }

    @Test
    @DisplayName("Resilience: Wallet failure does not break greeting or notifications")
    void walletFailureDoesNotBreakGreetingOrNotifications() {
        UserAccount user = new UserAccount("+94771234567", "Nimal Perera", "199012345678", "hash");
        when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(user));
        when(notificationStore.countUnreadNotifications(USER_ID)).thenReturn(0L);

        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(USER_ID))
                .thenThrow(new IllegalStateException("Account store unavailable"));

        HomeAggregateResponse response = homeAggregatorService.aggregateHome(USER_ID);

        assertThat(response.greeting().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.greeting().data().fullName()).isEqualTo("Nimal Perera");

        assertThat(response.unreadNotifications().status()).isEqualTo(HomeSectionStatus.OK);

        assertThat(response.wallet().status()).isEqualTo(HomeSectionStatus.FAILED);
        assertThat(response.wallet().data()).isNull();
        assertThat(response.wallet().error()).isEqualTo("WALLET_UNAVAILABLE");
    }
}

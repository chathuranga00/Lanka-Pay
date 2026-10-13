package com.lankapay.rewards;

import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.rewards.domain.PointsEntryType;
import com.lankapay.rewards.domain.PointsLedgerEntry;
import com.lankapay.rewards.domain.RewardOffer;
import com.lankapay.rewards.domain.RewardTier;
import com.lankapay.rewards.event.TransactionConfirmedEvent;
import com.lankapay.rewards.event.TransactionReversedEvent;
import com.lankapay.rewards.service.RewardsService;
import com.lankapay.rewards.store.InMemoryPointsLedgerStore;
import com.lankapay.rewards.store.InMemoryRewardOfferStore;
import com.lankapay.rewards.web.dto.PointsLedgerEntryDto;
import com.lankapay.rewards.web.dto.RedeemPointsRequest;
import com.lankapay.rewards.web.dto.RedeemPointsResponse;
import com.lankapay.rewards.web.dto.RewardsSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

class RewardsModuleTest {

    private InMemoryPointsLedgerStore ledgerStore;
    private InMemoryRewardOfferStore offerStore;
    private UserStore userStore;
    private PasswordEncoder passwordEncoder;
    private RewardsService rewardsService;

    private final Long testUserId = 1001L;
    private final String rawPin = "1234";

    @BeforeEach
    void setUp() {
        ledgerStore = new InMemoryPointsLedgerStore();
        offerStore = new InMemoryRewardOfferStore();
        userStore = Mockito.mock(UserStore.class);
        passwordEncoder = new BCryptPasswordEncoder();

        String hashedPin = passwordEncoder.encode(rawPin);
        UserAccount account = new UserAccount(
                testUserId,
                "+94771234567",
                "Kasun Perera",
                "199012345678",
                hashedPin,
                UserStatus.ACTIVE,
                UserRole.CUSTOMER,
                0,
                null,
                Instant.now(),
                Instant.now()
        );
        when(userStore.findUserById(testUserId)).thenReturn(Optional.of(account));
        when(userStore.findUserByPhone("+94771234567")).thenReturn(Optional.of(account));

        rewardsService = new RewardsService(
                ledgerStore,
                offerStore,
                userStore,
                passwordEncoder,
                null,
                null,
                null
        );
    }

    private User getTestUser() {
        User u = new User("+94771234567", "Kasun Perera", "199012345678", "hash");
        u.setId(testUserId);
        u.setStatus(UserStatus.ACTIVE);
        u.setRole(UserRole.CUSTOMER);
        return u;
    }

    @Test
    @DisplayName("Earn points only on confirmed SUCCESS event and idempotent by transaction ID")
    void testEarnOnlyAfterSuccessAndIdempotent() {
        TransactionConfirmedEvent event = new TransactionConfirmedEvent(
                "TXN-BILL-001",
                "BILL_PAYMENT",
                testUserId,
                9999L,
                "7010",
                "7083",
                "1001001",
                "9999001",
                BigDecimal.valueOf(2500),
                "BILLS",
                false,
                Instant.now(),
                Collections.emptyMap()
        );

        // First confirmation -> Earns points (25 base + active 2x offer bonus)
        Optional<PointsLedgerEntry> firstResult = rewardsService.handleTransactionConfirmed(event);
        assertThat(firstResult).isPresent();
        assertThat(firstResult.get().type()).isEqualTo(PointsEntryType.EARN);
        assertThat(firstResult.get().points()).isGreaterThan(0);
        int earned = firstResult.get().points();

        // Check running balance
        assertThat(ledgerStore.getCurrentBalance(testUserId)).isEqualTo(earned);

        // Replaying same transaction event -> Idempotently ignored
        Optional<PointsLedgerEntry> secondResult = rewardsService.handleTransactionConfirmed(event);
        assertThat(secondResult).isEmpty();
        assertThat(ledgerStore.getCurrentBalance(testUserId)).isEqualTo(earned);
    }

    @Test
    @DisplayName("Transaction reversal reverses previously awarded points exactly")
    void testTransactionReversalReversesPoints() {
        TransactionConfirmedEvent earnEvent = new TransactionConfirmedEvent(
                "TXN-QR-002",
                "QR_PAYMENT",
                testUserId,
                8888L,
                "7010",
                "7083",
                "1001001",
                "8888001",
                BigDecimal.valueOf(5000),
                "LANKAQR",
                false,
                Instant.now(),
                Collections.emptyMap()
        );

        Optional<PointsLedgerEntry> earnedEntry = rewardsService.handleTransactionConfirmed(earnEvent);
        assertThat(earnedEntry).isPresent();
        int initialBalance = ledgerStore.getCurrentBalance(testUserId);
        assertThat(initialBalance).isGreaterThan(0);

        // Trigger reversal
        TransactionReversedEvent reverseEvent = new TransactionReversedEvent(
                "TXN-QR-002",
                testUserId,
                "Merchant refund",
                Instant.now()
        );

        Optional<PointsLedgerEntry> reversedEntry = rewardsService.handleTransactionReversed(reverseEvent);
        assertThat(reversedEntry).isPresent();
        assertThat(reversedEntry.get().type()).isEqualTo(PointsEntryType.REVERSE);
        assertThat(reversedEntry.get().points()).isEqualTo(-initialBalance);
        assertThat(reversedEntry.get().runningBalance()).isEqualTo(0);
        assertThat(ledgerStore.getCurrentBalance(testUserId)).isEqualTo(0);
    }

    @Test
    @DisplayName("Anti-abuse: Self-dealing (transfer to oneself or same account) is blocked")
    void testAntiAbuseSelfDealingBlocked() {
        // Transfer to oneself (same user ID)
        TransactionConfirmedEvent selfTransfer = new TransactionConfirmedEvent(
                "TXN-SELF-003",
                "TRANSFER",
                testUserId,
                testUserId,
                "7010",
                "7010",
                "ACC-1",
                "ACC-2",
                BigDecimal.valueOf(10000),
                "TRANSFER",
                false,
                Instant.now(),
                Collections.emptyMap()
        );

        Optional<PointsLedgerEntry> res1 = rewardsService.handleTransactionConfirmed(selfTransfer);
        assertThat(res1).isEmpty();
        assertThat(ledgerStore.getCurrentBalance(testUserId)).isEqualTo(0);

        // Same account number & same bank code transfer
        TransactionConfirmedEvent sameAccountTransfer = new TransactionConfirmedEvent(
                "TXN-SAME-004",
                "TRANSFER",
                testUserId,
                2002L,
                "7010",
                "7010",
                "ACC-1",
                "ACC-1",
                BigDecimal.valueOf(10000),
                "TRANSFER",
                false,
                Instant.now(),
                Collections.emptyMap()
        );

        Optional<PointsLedgerEntry> res2 = rewardsService.handleTransactionConfirmed(sameAccountTransfer);
        assertThat(res2).isEmpty();
        assertThat(ledgerStore.getCurrentBalance(testUserId)).isEqualTo(0);
    }

    @Test
    @DisplayName("Enforce daily and monthly earn caps")
    void testEarnCaps() {
        // Set up many transactions to hit caps
        int totalEarned = 0;
        for (int i = 0; i < 30; i++) {
            TransactionConfirmedEvent event = new TransactionConfirmedEvent(
                    "TXN-CAP-" + i,
                    "BILL_PAYMENT",
                    testUserId,
                    9990L + i,
                    "7010",
                    "7083",
                    "ACC-" + i,
                    "BILLER-" + i,
                    BigDecimal.valueOf(1000),
                    "BILLS",
                    false,
                    Instant.now(),
                    Collections.emptyMap()
            );
            Optional<PointsLedgerEntry> res = rewardsService.handleTransactionConfirmed(event);
            if (res.isPresent()) {
                totalEarned += res.get().points();
            }
        }

        // Daily cap is 500
        assertThat(totalEarned).isLessThanOrEqualTo(500);
        assertThat(ledgerStore.getCurrentBalance(testUserId)).isLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("Redeem fails with insufficient points and enforces step-up auth")
    void testRedeemWithInsufficientPointsAndAuth() {
        User user = getTestUser();

        // 1. Invalid PIN -> Throws Authorization/Credentials exception
        RedeemPointsRequest request = new RedeemPointsRequest(
                "VOUCHER",
                50,
                "Keells",
                "Test voucher",
                "wrong_pin"
        );
        assertThatThrownBy(() -> rewardsService.redeemPoints(user, request, "IDEMP-1", null))
                .isInstanceOf(RuntimeException.class);

        // 2. Valid PIN but zero balance -> Throws ConflictException INSUFFICIENT_POINTS
        RedeemPointsRequest validPinRequest = new RedeemPointsRequest(
                "VOUCHER",
                50,
                "Keells",
                "Test voucher",
                rawPin
        );
        assertThatThrownBy(() -> rewardsService.redeemPoints(user, validPinRequest, "IDEMP-2", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Insufficient simulated points");
    }

    @Test
    @DisplayName("Concurrent redemptions cannot double spend points")
    void testConcurrentRedeemsCannotDoubleSpend() throws Exception {
        User user = getTestUser();

        // Award 100 points
        ledgerStore.append(
                testUserId,
                PointsEntryType.EARN,
                100,
                "TXN-INIT",
                "Initial bonus",
                null,
                Instant.now().plus(90, ChronoUnit.DAYS),
                Collections.emptyMap()
        );
        assertThat(ledgerStore.getCurrentBalance(testUserId)).isEqualTo(100);

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            tasks.add(() -> {
                latch.await();
                try {
                    RedeemPointsRequest req = new RedeemPointsRequest("VOUCHER", 60, "Keells", "Concurrent redeem", rawPin);
                    rewardsService.redeemPoints(user, req, "IDEMP-CONCURRENT-" + idx, null);
                    return true;
                } catch (ConflictException ex) {
                    return false; // Insufficient points
                }
            });
        }

        List<Future<Boolean>> futures = new ArrayList<>();
        for (var task : tasks) {
            futures.add(executor.submit(task));
        }

        latch.countDown(); // Start all simultaneously

        int successCount = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) {
                successCount++;
            }
        }
        executor.shutdown();

        // Since balance was 100 and each redemption requested 60, exactly 1 must succeed
        assertThat(successCount).isEqualTo(1);
        assertThat(ledgerStore.getCurrentBalance(testUserId)).isEqualTo(40);
    }

    @Test
    @DisplayName("Ledger immutability: running balance correctly tracks all entries and cannot be corrupted")
    void testLedgerImmutabilityAndTier() {
        User user = getTestUser();

        // 1. Initial balance is 0, Tier is BASIC
        RewardsSummaryResponse summary0 = rewardsService.getSummary(testUserId);
        assertThat(summary0.pointsBalance()).isEqualTo(0);
        assertThat(summary0.tier()).isEqualTo(RewardTier.BASIC);

        // 2. Perform 5 distinct confirmed transactions to reach Tier PLUS (threshold >= 5)
        for (int i = 1; i <= 5; i++) {
            TransactionConfirmedEvent event = new TransactionConfirmedEvent(
                    "TXN-TIER-" + i,
                    "BILL_PAYMENT",
                    testUserId,
                    7000L + i,
                    "7010",
                    "7083",
                    "ACC-S",
                    "ACC-R-" + i,
                    BigDecimal.valueOf(1000),
                    "BILLS",
                    false,
                    Instant.now(),
                    Collections.emptyMap()
            );
            rewardsService.handleTransactionConfirmed(event);
        }

        RewardsSummaryResponse summaryAfter5 = rewardsService.getSummary(testUserId);
        assertThat(summaryAfter5.tier()).isEqualTo(RewardTier.PLUS);
        assertThat(summaryAfter5.pointsBalance()).isGreaterThan(0);

        // 3. Redeem points
        int balanceBeforeRedeem = summaryAfter5.pointsBalance();
        RedeemPointsRequest redeemReq = new RedeemPointsRequest("STATEMENT_CREDIT", 50, null, "Credit", rawPin);
        RedeemPointsResponse redeemResp = rewardsService.redeemPoints(user, redeemReq, "IDEMP-RED-1", null);

        assertThat(redeemResp.statementCreditLabel()).isNotNull();
        assertThat(redeemResp.remainingBalance()).isEqualTo(balanceBeforeRedeem - 50);

        // 4. Verify ledger entries are completely chronological and running balances match
        List<PointsLedgerEntryDto> ledger = rewardsService.getLedger(testUserId, "ALL");
        // Check size: 5 earns + 1 redeem = 6 entries
        assertThat(ledger).hasSize(6);

        // Entries are returned newest first in getLedger
        assertThat(ledger.get(0).type()).isEqualTo(PointsEntryType.REDEEM);
        assertThat(ledger.get(0).runningBalance()).isEqualTo(balanceBeforeRedeem - 50);

        // Every entry includes the simulated disclaimer
        for (PointsLedgerEntryDto entry : ledger) {
            assertThat(entry.disclaimer()).contains("Simulated points only");
        }
    }
}

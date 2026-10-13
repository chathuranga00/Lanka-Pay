package com.lankapay.home;

import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardKind;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.feature.config.FeatureProperties;
import com.lankapay.feature.service.FeatureFlagService;
import com.lankapay.home.domain.HomeSectionStatus;
import com.lankapay.home.service.HomeAggregatorService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeAggregatorPerformanceTest {

    @Mock private UserStore userStore;
    @Mock private NotificationStore notificationStore;
    @Mock private LinkedAccountStore linkedAccountStore;
    @Mock private CardStore cardStore;
    @Mock private TransactionStore transactionStore;

    private FeatureFlagService featureFlagService;
    private HomeAggregatorService homeAggregatorService;

    private static final Long USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        FeatureProperties properties = new FeatureProperties();
        featureFlagService = new FeatureFlagService(properties, null);

        homeAggregatorService = new HomeAggregatorService(
                userStore,
                notificationStore,
                linkedAccountStore,
                cardStore,
                transactionStore,
                featureFlagService,
                null
        );

        // Seed realistic mocked data
        UserAccount user = new UserAccount("+94771234567", "Kasun Perera", "199012345678", "bcryptHash")
                .withStatus(UserStatus.ACTIVE);
        when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(user));

        when(notificationStore.countUnreadNotifications(USER_ID)).thenReturn(3L);

        List<LinkedAccount> accounts = List.of(
                new LinkedAccount(1L, USER_ID, "BANK_A", "h1", "•••• 1111", "Kasun Perera", "Primary", true, true, "enc", BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now()),
                new LinkedAccount(2L, USER_ID, "BANK_B", "h2", "•••• 2222", "Kasun Perera", "Savings", false, false, "enc", BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now())
        );
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(USER_ID)).thenReturn(accounts);

        List<Card> cards = List.of(
                new Card(1L, USER_ID, 1L, "tok_1", "0010", 12, 2028, "TEST CARD", "Platinum Card", CardKind.VIRTUAL, CardStatus.ACTIVE, true, 5000000L, 20000000L, Instant.now(), Instant.now(), Instant.now(), null, 1L, null, true)
        );
        when(cardStore.findAllByUserId(USER_ID)).thenReturn(cards);

        List<TransactionRecord> txList = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            txList.add(new TransactionRecord(
                    (long) (i + 1), "TX-" + i, "IDEM-" + i, USER_ID, "+94771234567", "Kasun Perera",
                    2L, "+94779876543", "Recipient " + i, "BANK_A", "BANK_B",
                    new BigDecimal("1250.00"), "LKR", TransactionStatus.COMPLETED,
                    null, "BTX-" + i, "PROV-" + i, "Purchase", true, null, null, 1L,
                    Instant.now().minusSeconds(i * 3600), Instant.now().minusSeconds(i * 3600),
                    FundingType.ACCOUNT, null, null, null, BigDecimal.ZERO, new BigDecimal("1250.00"), null, 0
            ));
        }
        when(transactionStore.findTransfersByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(txList));
    }

    @Test
    @DisplayName("Performance SLA: Single aggregation responds in under 500 ms (target SLA)")
    void testSingleRequestLatencyWithinSla() {
        long start = System.nanoTime();
        HomeAggregateResponse response = homeAggregatorService.aggregateHome(USER_ID);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMs).isLessThan(500);
        assertThat(response.greeting().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.greeting().data().fullName()).isEqualTo("Kasun Perera");
        assertThat(response.wallet().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.recentActivity().status()).isEqualTo(HomeSectionStatus.OK);
    }

    @Test
    @DisplayName("Load test: 100 concurrent requests across 10 threads respond within SLA (<500ms p95)")
    void testConcurrentAggregationLoad() throws Exception {
        int totalRequests = 100;
        int threadPoolSize = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        List<Future<Long>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        long overallStart = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            futures.add(executor.submit(() -> {
                long reqStart = System.nanoTime();
                HomeAggregateResponse response = homeAggregatorService.aggregateHome(USER_ID);
                long reqElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - reqStart);
                if (response.greeting().status() == HomeSectionStatus.OK &&
                        response.wallet().status() == HomeSectionStatus.OK) {
                    successCount.incrementAndGet();
                }
                return reqElapsedMs;
            }));
        }

        List<Long> latencies = new ArrayList<>();
        for (Future<Long> f : futures) {
            latencies.add(f.get(5, TimeUnit.SECONDS));
        }
        executor.shutdown();

        Collections.sort(latencies);
        int p95Index = (int) Math.ceil(0.95 * latencies.size()) - 1;
        long p95Latency = latencies.get(p95Index);
        long maxLatency = latencies.get(latencies.size() - 1);

        assertThat(successCount.get()).isEqualTo(totalRequests);
        assertThat(p95Latency).isLessThan(500);
        assertThat(maxLatency).isLessThan(1000);
    }

    @Test
    @DisplayName("Resilience: Downstream section errors isolate without degrading other sections")
    void testSectionFailureIsolation() {
        // Cause account store error
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(USER_ID))
                .thenThrow(new RuntimeException("Bank gateway database connection timed out"));

        HomeAggregateResponse response = homeAggregatorService.aggregateHome(USER_ID);

        // Wallet fails gracefully
        assertThat(response.wallet().status()).isEqualTo(HomeSectionStatus.FAILED);
        // But Greeting and RecentActivity still succeed!
        assertThat(response.greeting().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(response.greeting().data().fullName()).isEqualTo("Kasun Perera");
        assertThat(response.recentActivity().status()).isEqualTo(HomeSectionStatus.OK);
    }
}

package com.lankapay.insights;

import com.lankapay.analytics.domain.Budget;
import com.lankapay.analytics.domain.BudgetStore;
import com.lankapay.analytics.service.AnalyticsService;
import com.lankapay.analytics.web.dto.AnalyticsSummaryResponse;
import com.lankapay.analytics.web.dto.CategoryTotalDto;
import com.lankapay.analytics.web.dto.MonthOverMonthDto;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.insights.domain.InsightItem;
import com.lankapay.insights.domain.InsightType;
import com.lankapay.insights.service.InsightService;
import com.lankapay.insights.store.InMemoryInsightDismissalStore;
import com.lankapay.rewards.domain.PointsLedgerStore;
import com.lankapay.scheduled.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class InsightModuleTest {

    private AnalyticsService analyticsService;
    private BudgetStore budgetStore;
    private ScheduledPaymentStore scheduledPaymentStore;
    private CardStore cardStore;
    private LinkedAccountStore linkedAccountStore;
    private PointsLedgerStore pointsLedgerStore;
    private InMemoryInsightDismissalStore dismissalStore;
    private InsightService insightService;

    @BeforeEach
    void setUp() {
        analyticsService = Mockito.mock(AnalyticsService.class);
        budgetStore = Mockito.mock(BudgetStore.class);
        scheduledPaymentStore = Mockito.mock(ScheduledPaymentStore.class);
        cardStore = Mockito.mock(CardStore.class);
        linkedAccountStore = Mockito.mock(LinkedAccountStore.class);
        pointsLedgerStore = Mockito.mock(PointsLedgerStore.class);
        dismissalStore = new InMemoryInsightDismissalStore();

        insightService = new InsightService(
                analyticsService,
                budgetStore,
                scheduledPaymentStore,
                cardStore,
                linkedAccountStore,
                pointsLedgerStore,
                dismissalStore
        );
    }

    @Test
    @DisplayName("New user with no data receives empty insights list (neutral, no crash)")
    void testNoDataReturnsEmptyList() {
        Long userId = 99L;
        when(cardStore.findAllByUserId(userId)).thenReturn(Collections.emptyList());
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(userId)).thenReturn(Collections.emptyList());
        when(scheduledPaymentStore.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(analyticsService.getSummary(eq(userId), any())).thenReturn(null);
        when(pointsLedgerStore.getExpiringSoonPoints(eq(userId), any(), any())).thenReturn(0);

        List<InsightItem> insights = insightService.getInsights(userId);
        assertThat(insights).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Rule 1: Spending change evaluates correctly and outputs neutral factual text")
    void testSpendingChangeRule() {
        Long userId = 1L;
        MonthOverMonthDto mom = new MonthOverMonthDto(
                1500000L, BigDecimal.valueOf(15000.0),
                1000000L, BigDecimal.valueOf(10000.0),
                500000L, BigDecimal.valueOf(5000.0),
                50.0, true
        );
        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                "2026-10", 1500000L, BigDecimal.valueOf(15000.0),
                0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0,
                Collections.emptyList(), Collections.emptyList(), mom,
                null, null, null, null, null
        );
        when(analyticsService.getSummary(eq(userId), any())).thenReturn(summary);

        List<InsightItem> insights = insightService.getInsights(userId);
        assertThat(insights).anyMatch(i -> i.type() == InsightType.SPENDING_CHANGE
                && i.text().contains("higher than last month")
                && !i.text().contains("should") // factual, never financial advice
        );
    }

    @Test
    @DisplayName("Rule 2: Category surge evaluates when category share >= 25%")
    void testCategorySurgeRule() {
        Long userId = 2L;
        CategoryTotalDto diningCat = new CategoryTotalDto("DINING", "Dining", "#FF5722", 800000L, BigDecimal.valueOf(8000.0), 4, 40.0);
        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                "2026-10", 2000000L, BigDecimal.valueOf(20000.0),
                0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0,
                List.of(diningCat), Collections.emptyList(), null,
                null, null, null, null, null
        );
        when(analyticsService.getSummary(eq(userId), any())).thenReturn(summary);

        List<InsightItem> insights = insightService.getInsights(userId);
        assertThat(insights).anyMatch(i -> i.type() == InsightType.CATEGORY_SURGE
                && i.text().contains("DINING accounted for 40.0% of your total expenses")
        );
    }

    @Test
    @DisplayName("Rule 3: Budget limit evaluates when used percentage >= 80%")
    void testBudgetLimitRule() {
        Long userId = 3L;
        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                "2026-10", 4500000L, BigDecimal.valueOf(45000.0),
                0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0,
                Collections.emptyList(), Collections.emptyList(), null,
                5000000L, BigDecimal.valueOf(50000.0), 90.0,
                500000L, BigDecimal.valueOf(5000.0)
        );
        when(analyticsService.getSummary(eq(userId), any())).thenReturn(summary);

        List<InsightItem> insights = insightService.getInsights(userId);
        assertThat(insights).anyMatch(i -> i.type() == InsightType.BUDGET_LIMIT
                && i.text().contains("90.0% of your monthly budget limit")
        );
    }

    @Test
    @DisplayName("Rule 4: Upcoming scheduled payment within 7 days is surfaced")
    void testUpcomingPaymentRule() {
        Long userId = 4L;
        ScheduledPayment payment = new ScheduledPayment(
                10L, userId, ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("CEB", "Electricity Board", "Monthly bill"),
                250000L, "LKR", false, null, ScheduleFrequency.MONTHLY,
                LocalDate.now(), null, Instant.now().plus(3, ChronoUnit.DAYS),
                ScheduleStatus.ACTIVE, 0, null, 1L, Instant.now(), Instant.now()
        );
        when(scheduledPaymentStore.findByUserId(userId)).thenReturn(List.of(payment));

        List<InsightItem> insights = insightService.getInsights(userId);
        assertThat(insights).anyMatch(i -> i.type() == InsightType.UPCOMING_PAYMENT
                && i.text().contains("Electricity Board")
                && i.actionDeepLink().equals("/scheduled")
        );
    }

    @Test
    @DisplayName("Rule 5: Account or card attention required when card is frozen")
    void testCardFrozenAttentionRule() {
        Long userId = 5L;
        Card frozenCard = new Card(
                userId, 1L, "tok_123", "4321",
                12, 2028, "VISA", "My Card",
                com.lankapay.card.domain.CardKind.LINKED, CardStatus.FROZEN, null
        );
        when(cardStore.findAllByUserId(userId)).thenReturn(List.of(frozenCard));

        List<InsightItem> insights = insightService.getInsights(userId);
        assertThat(insights).anyMatch(i -> i.type() == InsightType.ACCOUNT_ATTENTION
                && i.text().contains("4321 is currently frozen")
        );
    }

    @Test
    @DisplayName("Rule 6: Expiring rewards points evaluated when points expiring in 30 days")
    void testExpiringRewardsRule() {
        Long userId = 6L;
        when(pointsLedgerStore.getExpiringSoonPoints(eq(userId), any(), any())).thenReturn(350);

        List<InsightItem> insights = insightService.getInsights(userId);
        assertThat(insights).anyMatch(i -> i.type() == InsightType.EXPIRING_REWARDS
                && i.text().contains("350 reward points expiring")
        );
    }

    @Test
    @DisplayName("Dismissal removes the dismissed insight type from returned list")
    void testDismissalHidesInsightType() {
        Long userId = 7L;
        when(pointsLedgerStore.getExpiringSoonPoints(eq(userId), any(), any())).thenReturn(500);

        // Before dismissal
        List<InsightItem> before = insightService.getInsights(userId);
        assertThat(before).anyMatch(i -> i.type() == InsightType.EXPIRING_REWARDS);

        // Dismiss type
        insightService.dismissType(userId, InsightType.EXPIRING_REWARDS);

        // After dismissal
        List<InsightItem> after = insightService.getInsights(userId);
        assertThat(after).noneMatch(i -> i.type() == InsightType.EXPIRING_REWARDS);

        // Restoring type
        insightService.undismissType(userId, InsightType.EXPIRING_REWARDS);
        List<InsightItem> restored = insightService.getInsights(userId);
        assertThat(restored).anyMatch(i -> i.type() == InsightType.EXPIRING_REWARDS);
    }
}

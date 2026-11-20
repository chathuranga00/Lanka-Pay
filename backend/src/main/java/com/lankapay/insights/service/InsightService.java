package com.lankapay.insights.service;

import com.lankapay.analytics.domain.BudgetStore;
import com.lankapay.analytics.service.AnalyticsService;
import com.lankapay.analytics.web.dto.AnalyticsSummaryResponse;
import com.lankapay.analytics.web.dto.CategoryTotalDto;
import com.lankapay.analytics.web.dto.MonthOverMonthDto;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.insights.domain.InsightDismissalStore;
import com.lankapay.insights.domain.InsightItem;
import com.lankapay.insights.domain.InsightType;
import com.lankapay.rewards.domain.PointsLedgerStore;
import com.lankapay.scheduled.domain.ScheduleStatus;
import com.lankapay.scheduled.domain.ScheduledPayment;
import com.lankapay.scheduled.domain.ScheduledPaymentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Core engine for computing rule-based financial insights.
 * Strict Invariant: Neutral facts only; never financial, investment, or legal advice.
 * Returns at most 3 insights, respecting user dismissals.
 */
@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);
    private static final ZoneId SRI_LANKA_ZONE = ZoneId.of("Asia/Colombo");

    private final AnalyticsService analyticsService;
    private final BudgetStore budgetStore;
    private final ScheduledPaymentStore scheduledPaymentStore;
    private final CardStore cardStore;
    private final LinkedAccountStore linkedAccountStore;
    private final PointsLedgerStore pointsLedgerStore;
    private final InsightDismissalStore dismissalStore;

    public InsightService(
            AnalyticsService analyticsService,
            BudgetStore budgetStore,
            ScheduledPaymentStore scheduledPaymentStore,
            CardStore cardStore,
            LinkedAccountStore linkedAccountStore,
            PointsLedgerStore pointsLedgerStore,
            InsightDismissalStore dismissalStore) {
        this.analyticsService = analyticsService;
        this.budgetStore = budgetStore;
        this.scheduledPaymentStore = scheduledPaymentStore;
        this.cardStore = cardStore;
        this.linkedAccountStore = linkedAccountStore;
        this.pointsLedgerStore = pointsLedgerStore;
        this.dismissalStore = dismissalStore;
    }

    /**
     * Compute at most 3 factual insights for the user.
     */
    public List<InsightItem> getInsights(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        Set<InsightType> dismissed = dismissalStore.getDismissedTypes(userId);
        List<InsightItem> candidates = new ArrayList<>();

        // Priority 1: Account or card needing attention
        if (!dismissed.contains(InsightType.ACCOUNT_ATTENTION)) {
            evaluateAccountAttention(userId).ifPresent(candidates::add);
        }

        // Priority 2: Budget limit nearing/exceeded
        if (!dismissed.contains(InsightType.BUDGET_LIMIT)) {
            evaluateBudgetLimit(userId).ifPresent(candidates::add);
        }

        // Priority 3: Upcoming scheduled payments
        if (!dismissed.contains(InsightType.UPCOMING_PAYMENT)) {
            evaluateUpcomingPayment(userId).ifPresent(candidates::add);
        }

        // Priority 4: Spending increase/decrease vs last month
        if (!dismissed.contains(InsightType.SPENDING_CHANGE)) {
            evaluateSpendingChange(userId).ifPresent(candidates::add);
        }

        // Priority 5: Category surge / high share
        if (!dismissed.contains(InsightType.CATEGORY_SURGE)) {
            evaluateCategorySurge(userId).ifPresent(candidates::add);
        }

        // Priority 6: Expiring rewards points
        if (!dismissed.contains(InsightType.EXPIRING_REWARDS)) {
            evaluateExpiringRewards(userId).ifPresent(candidates::add);
        }

        // Enforce maximum of 3 insights
        if (candidates.size() > 3) {
            return candidates.subList(0, 3);
        }
        return candidates;
    }

    public void dismissType(Long userId, InsightType type) {
        dismissalStore.dismissType(userId, type);
    }

    public void undismissType(Long userId, InsightType type) {
        dismissalStore.undismissType(userId, type);
    }

    public Set<InsightType> getDismissedTypes(Long userId) {
        return dismissalStore.getDismissedTypes(userId);
    }

    // -------------------------------------------------------------
    // Evaluation Rules (Neutral Facts Only)
    // -------------------------------------------------------------

    private Optional<InsightItem> evaluateAccountAttention(Long userId) {
        try {
            if (cardStore != null) {
                List<Card> cards = cardStore.findAllByUserId(userId);
                if (cards != null) {
                    for (Card card : cards) {
                        if (card.isRemoved()) continue;

                        if (card.status() == CardStatus.FROZEN) {
                            return Optional.of(new InsightItem(
                                    "ins_card_frozen_" + card.id(),
                                    InsightType.ACCOUNT_ATTENTION,
                                    "Card Currently Frozen",
                                    "Your card ending in " + card.last4() + " is currently frozen.",
                                    "/wallet/cards"
                            ));
                        }

                        // Check expiration
                        LocalDate now = LocalDate.now(SRI_LANKA_ZONE);
                        if (card.expYear() != null && card.expMonth() != null) {
                            int currentYear = now.getYear();
                            int currentMonth = now.getMonthValue();
                            if (card.expYear() == currentYear && card.expMonth() <= currentMonth + 1) {
                                return Optional.of(new InsightItem(
                                        "ins_card_exp_" + card.id(),
                                        InsightType.ACCOUNT_ATTENTION,
                                        "Card Expiring Soon",
                                        "Your card ending in " + card.last4() + " expires in " + card.expMonth() + "/" + card.expYear() + ".",
                                        "/wallet/cards"
                                ));
                            }
                        }
                    }
                }
            }

            if (linkedAccountStore != null) {
                List<LinkedAccount> accounts = linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(userId);
                if (accounts != null) {
                    for (LinkedAccount acc : accounts) {
                        if (!acc.isActive() || acc.status() == BankAccountStatus.REMOVED) {
                            return Optional.of(new InsightItem(
                                    "ins_acc_inactive_" + acc.id(),
                                    InsightType.ACCOUNT_ATTENTION,
                                    "Bank Account Inactive",
                                    "Your linked account " + acc.maskedNumber() + " is inactive or disconnected.",
                                    "/wallet/accounts"
                            ));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Error checking account attention for userId {}: {}", userId, ex.getMessage());
        }
        return Optional.empty();
    }

    private Optional<InsightItem> evaluateBudgetLimit(Long userId) {
        try {
            if (analyticsService == null) return Optional.empty();
            String currentMonth = YearMonth.now(SRI_LANKA_ZONE).toString();
            AnalyticsSummaryResponse summary = analyticsService.getSummary(userId, currentMonth);

            if (summary != null && summary.budgetTotalMinor() != null && summary.budgetTotalMinor() > 0) {
                Double usedPct = summary.budgetUsedPercentage();
                if (usedPct != null && usedPct >= 80.0) {
                    BigDecimal totalAmount = summary.budgetTotalAmount() != null
                            ? summary.budgetTotalAmount()
                            : BigDecimal.valueOf(summary.budgetTotalMinor() / 100.0);
                    return Optional.of(new InsightItem(
                            "ins_budget_" + currentMonth,
                            InsightType.BUDGET_LIMIT,
                            "Budget Nearing Limit",
                            String.format(Locale.US, "You have used %.1f%% of your monthly budget limit of LKR %,.2f.", usedPct, totalAmount.doubleValue()),
                            "/analytics"
                    ));
                }
            }
        } catch (Exception ex) {
            log.debug("Error checking budget limit for userId {}: {}", userId, ex.getMessage());
        }
        return Optional.empty();
    }

    private Optional<InsightItem> evaluateUpcomingPayment(Long userId) {
        try {
            if (scheduledPaymentStore == null) return Optional.empty();
            List<ScheduledPayment> payments = scheduledPaymentStore.findByUserId(userId);
            if (payments == null || payments.isEmpty()) return Optional.empty();

            Instant now = Instant.now();
            Instant oneWeekAhead = now.plus(7, ChronoUnit.DAYS);

            for (ScheduledPayment payment : payments) {
                if (payment.status() == ScheduleStatus.ACTIVE && payment.nextRunAt() != null) {
                    if (payment.nextRunAt().isAfter(now) && payment.nextRunAt().isBefore(oneWeekAhead)) {
                        double amount = payment.amountMinor() != null ? payment.amountMinor() / 100.0 : 0.0;
                        String payeeName = (payment.payee() != null && payment.payee().name() != null)
                                ? payment.payee().name()
                                : "scheduled recipient";
                        return Optional.of(new InsightItem(
                                "ins_scheduled_" + payment.id(),
                                InsightType.UPCOMING_PAYMENT,
                                "Upcoming Scheduled Payment",
                                String.format(Locale.US, "You have a scheduled payment of LKR %,.2f to %s due in the next 7 days.", amount, payeeName),
                                "/scheduled"
                        ));
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Error checking upcoming payments for userId {}: {}", userId, ex.getMessage());
        }
        return Optional.empty();
    }

    private Optional<InsightItem> evaluateSpendingChange(Long userId) {
        try {
            if (analyticsService == null) return Optional.empty();
            String currentMonth = YearMonth.now(SRI_LANKA_ZONE).toString();
            AnalyticsSummaryResponse summary = analyticsService.getSummary(userId, currentMonth);

            if (summary != null && summary.monthOverMonth() != null) {
                MonthOverMonthDto mom = summary.monthOverMonth();
                if (mom.previousMonthSpentMinor() != null && mom.previousMonthSpentMinor() > 0) {
                    double changePct = mom.percentageChange();
                    if (Math.abs(changePct) >= 5.0) {
                        double currentSpent = mom.currentMonthSpent() != null ? mom.currentMonthSpent().doubleValue() : 0.0;
                        if (mom.increased()) {
                            return Optional.of(new InsightItem(
                                    "ins_spend_inc_" + currentMonth,
                                    InsightType.SPENDING_CHANGE,
                                    "Spending Increased vs Last Month",
                                    String.format(Locale.US, "Your spending of LKR %,.2f this month is %.1f%% higher than last month at this time.", currentSpent, changePct),
                                    "/analytics"
                            ));
                        } else {
                            return Optional.of(new InsightItem(
                                    "ins_spend_dec_" + currentMonth,
                                    InsightType.SPENDING_CHANGE,
                                    "Spending Decreased vs Last Month",
                                    String.format(Locale.US, "Your spending of LKR %,.2f this month is %.1f%% lower than last month at this time.", currentSpent, Math.abs(changePct)),
                                    "/analytics"
                            ));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Error checking spending change for userId {}: {}", userId, ex.getMessage());
        }
        return Optional.empty();
    }

    private Optional<InsightItem> evaluateCategorySurge(Long userId) {
        try {
            if (analyticsService == null) return Optional.empty();
            String currentMonth = YearMonth.now(SRI_LANKA_ZONE).toString();
            AnalyticsSummaryResponse summary = analyticsService.getSummary(userId, currentMonth);

            if (summary != null && summary.spentMinor() != null && summary.spentMinor() > 0 && summary.byCategoryTotals() != null) {
                for (CategoryTotalDto cat : summary.byCategoryTotals()) {
                    if (cat.percentage() >= 25.0) {
                        return Optional.of(new InsightItem(
                                "ins_cat_surge_" + currentMonth + "_" + cat.category(),
                                InsightType.CATEGORY_SURGE,
                                "High Category Expense Share",
                                String.format(Locale.US, "%s accounted for %.1f%% of your total expenses this month.", cat.category(), cat.percentage()),
                                "/analytics"
                        ));
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Error checking category surge for userId {}: {}", userId, ex.getMessage());
        }
        return Optional.empty();
    }

    private Optional<InsightItem> evaluateExpiringRewards(Long userId) {
        try {
            if (pointsLedgerStore == null) return Optional.empty();
            Instant now = Instant.now();
            Instant in30Days = now.plus(30, ChronoUnit.DAYS);
            int expiring = pointsLedgerStore.getExpiringSoonPoints(userId, now, in30Days);

            if (expiring > 0) {
                return Optional.of(new InsightItem(
                        "ins_rewards_expiring_" + userId,
                        InsightType.EXPIRING_REWARDS,
                        "Reward Points Expiring Soon",
                        String.format(Locale.US, "You have %,d reward points expiring within the next 30 days.", expiring),
                        "/rewards"
                ));
            }
        } catch (Exception ex) {
            log.debug("Error checking expiring rewards for userId {}: {}", userId, ex.getMessage());
        }
        return Optional.empty();
    }
}

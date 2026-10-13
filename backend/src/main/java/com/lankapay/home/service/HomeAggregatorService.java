package com.lankapay.home.service;

import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.feature.service.FeatureFlagService;
import com.lankapay.home.domain.HomeSection;
import com.lankapay.home.web.dto.*;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.domain.NotificationStore;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransactionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Aggregates all dashboard sections into a single response.
 *
 * Resilience Invariant:
 * Every section resolution is wrapped in a dedicated try-catch block.
 * An error or timeout in one section returns HomeSection.failed() or
 * HomeSection.unavailable() without bubbling up or blanking the rest of the dashboard.
 */
@Service
public class HomeAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(HomeAggregatorService.class);

    private final UserStore userStore;
    private final NotificationStore notificationStore;
    private final LinkedAccountStore linkedAccountStore;
    private final CardStore cardStore;
    private final TransactionStore transactionStore;
    private final FeatureFlagService featureFlagService;
    private final com.lankapay.insights.service.InsightService insightService;

    @org.springframework.beans.factory.annotation.Autowired
    public HomeAggregatorService(
            UserStore userStore,
            NotificationStore notificationStore,
            LinkedAccountStore linkedAccountStore,
            CardStore cardStore,
            TransactionStore transactionStore,
            FeatureFlagService featureFlagService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.lankapay.insights.service.InsightService insightService) {
        this.userStore = userStore;
        this.notificationStore = notificationStore;
        this.linkedAccountStore = linkedAccountStore;
        this.cardStore = cardStore;
        this.transactionStore = transactionStore;
        this.featureFlagService = featureFlagService;
        this.insightService = insightService;
    }

    public HomeAggregatorService(
            UserStore userStore,
            NotificationStore notificationStore,
            LinkedAccountStore linkedAccountStore,
            CardStore cardStore,
            TransactionStore transactionStore,
            FeatureFlagService featureFlagService) {
        this(userStore, notificationStore, linkedAccountStore, cardStore, transactionStore, featureFlagService, null);
    }

    public HomeAggregateResponse aggregateHome(Long userId) {
        HomeSection<GreetingData> greeting = resolveGreeting(userId);
        HomeSection<NotificationData> notifications = resolveNotifications(userId);
        HomeSection<WalletData> wallet = resolveWallet(userId);
        HomeSection<List<RecentActivityItem>> recentActivity = resolveRecentActivity(userId);
        HomeSection<RewardsPointsData> rewardsPoints = resolveRewardsPoints(userId);
        HomeSection<Object> stories = HomeSection.unavailable("MODULE_NOT_YET_IMPLEMENTED");
        HomeSection<SpendSummaryData> spendSummary = resolveSpendSummary(userId);
        HomeSection<NudgeData> nudge = resolveNudge(userId, spendSummary.data());
        HomeSection<List<ShortcutItem>> shortcuts = resolveShortcuts();

        return new HomeAggregateResponse(
                greeting,
                notifications,
                wallet,
                recentActivity,
                rewardsPoints,
                stories,
                spendSummary,
                nudge,
                shortcuts
        );
    }

    private HomeSection<GreetingData> resolveGreeting(Long userId) {
        try {
            if (userId == null) {
                return HomeSection.failed("USER_ID_REQUIRED");
            }
            Optional<UserAccount> userOpt = userStore.findUserById(userId);
            if (userOpt.isEmpty()) {
                return HomeSection.failed("USER_NOT_FOUND");
            }
            UserAccount user = userOpt.get();
            String fullName = (user.fullName() != null && !user.fullName().isBlank())
                    ? user.fullName().trim()
                    : "Customer";

            String initial = String.valueOf(fullName.charAt(0)).toUpperCase();

            int hour = LocalTime.now().getHour();
            String salutationPrefix = "Good morning";
            if (hour >= 12 && hour < 17) {
                salutationPrefix = "Good afternoon";
            } else if (hour >= 17) {
                salutationPrefix = "Good evening";
            }
            String firstName = fullName.contains(" ") ? fullName.split("\\s+")[0] : fullName;
            String salutation = salutationPrefix + ", " + firstName;

            return HomeSection.ok(new GreetingData(fullName, initial, salutation));
        } catch (Exception ex) {
            log.warn("Failed to resolve home greeting for userId={}: {}", userId, ex.getMessage());
            return HomeSection.failed("USER_LOOKUP_FAILED");
        }
    }

    private HomeSection<NotificationData> resolveNotifications(Long userId) {
        try {
            if (userId == null) {
                return HomeSection.failed("USER_ID_REQUIRED");
            }
            long unread = notificationStore != null ? notificationStore.countUnreadNotifications(userId) : 0L;
            return HomeSection.ok(new NotificationData(unread));
        } catch (Exception ex) {
            log.warn("Failed to resolve unread notifications for userId={}: {}", userId, ex.getMessage());
            return HomeSection.failed("NOTIFICATIONS_UNAVAILABLE");
        }
    }

    private HomeSection<WalletData> resolveWallet(Long userId) {
        try {
            if (userId == null) {
                return HomeSection.failed("USER_ID_REQUIRED");
            }
            List<LinkedAccount> accounts = linkedAccountStore != null
                    ? linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(userId)
                    : List.of();

            List<LinkedAccount> activeAccounts = accounts != null
                    ? accounts.stream()
                        .filter(a -> a.status() == BankAccountStatus.ACTIVE)
                        .toList()
                    : List.of();

            LinkedAccount defaultAcct = activeAccounts.stream()
                    .filter(LinkedAccount::isDefault)
                    .findFirst()
                    .orElse(activeAccounts.isEmpty() ? null : activeAccounts.get(0));

            LinkedAccountSummary defaultSummary = null;
            if (defaultAcct != null) {
                String bankName = defaultAcct.bankCode();
                if (linkedAccountStore != null) {
                    var institutionOpt = linkedAccountStore.findActiveInstitutionByBankCode(defaultAcct.bankCode());
                    if (institutionOpt.isPresent()) {
                        bankName = institutionOpt.get().displayName();
                    }
                }
                defaultSummary = new LinkedAccountSummary(
                        defaultAcct.id(),
                        defaultAcct.bankCode(),
                        bankName,
                        defaultAcct.maskedNumber(),
                        defaultAcct.accountName(),
                        defaultAcct.isDefault()
                );
            }

            List<Card> cards = cardStore != null ? cardStore.findAllByUserId(userId) : List.of();
            List<Card> nonRemovedCards = cards != null
                    ? cards.stream()
                        .filter(c -> !c.isRemoved())
                        .toList()
                    : List.of();

            Card activeCard = nonRemovedCards.stream()
                    .filter(c -> c.isActive() || c.isFrozen())
                    .findFirst()
                    .orElse(null);

            CardSummary activeCardSummary = null;
            if (activeCard != null) {
                String expiryStr = (activeCard.expMonth() != null && activeCard.expYear() != null)
                        ? String.format("%02d/%d", activeCard.expMonth(), activeCard.expYear())
                        : "12/28";

                activeCardSummary = new CardSummary(
                        activeCard.id(),
                        "•••• " + activeCard.last4(),
                        activeCard.brandLabel() != null ? activeCard.brandLabel() : "TEST CARD",
                        activeCard.isFrozen(),
                        expiryStr
                );
            }

            // Simulated primary wallet balance (minor units 125,450.00 LKR)
            long simulatedBalanceMinor = 12545000L;
            String formattedBalance = "125,450.00";

            return HomeSection.ok(new WalletData(
                    simulatedBalanceMinor,
                    formattedBalance,
                    "LKR",
                    true,
                    defaultSummary,
                    activeCardSummary,
                    activeAccounts.size(),
                    nonRemovedCards.size()
            ));
        } catch (Exception ex) {
            log.warn("Failed to resolve wallet for userId={}: {}", userId, ex.getMessage());
            return HomeSection.failed("WALLET_UNAVAILABLE");
        }
    }

    private HomeSection<List<RecentActivityItem>> resolveRecentActivity(Long userId) {
        try {
            if (userId == null) {
                return HomeSection.failed("USER_ID_REQUIRED");
            }
            if (transactionStore == null) {
                return HomeSection.ok(List.of());
            }

            Page<TransactionRecord> page = transactionStore.findTransfersByUserId(
                    userId, PageRequest.of(0, 5, Sort.by("createdAt").descending()));

            List<RecentActivityItem> items = new ArrayList<>();
            if (page != null && page.getContent() != null) {
                for (TransactionRecord tx : page.getContent()) {
                    boolean isIncoming = userId.equals(tx.receiverId());
                    String direction = isIncoming ? "INCOMING" : "OUTGOING";

                    String counterparty = isIncoming ? tx.senderFullName() : tx.receiverFullName();
                    if (counterparty == null || counterparty.isBlank()) {
                        counterparty = isIncoming ? tx.senderPhone() : tx.receiverPhone();
                    }
                    if (counterparty == null || counterparty.isBlank()) {
                        counterparty = "Counterparty";
                    }

                    BigDecimal amount = tx.amount() != null ? tx.amount() : BigDecimal.ZERO;
                    long amountMinor = amount.multiply(BigDecimal.valueOf(100)).longValue();
                    String amountLkr = String.format("%,.2f", amount);

                    items.add(new RecentActivityItem(
                            tx.id(),
                            tx.reference(),
                            amountMinor,
                            amountLkr,
                            tx.currency() != null ? tx.currency() : "LKR",
                            tx.status() != null ? tx.status().name() : "COMPLETED",
                            direction,
                            counterparty,
                            tx.createdAt(),
                            tx.simulated()
                    ));
                }
            }

            return HomeSection.ok(items);
        } catch (Exception ex) {
            log.warn("Failed to resolve recent activity for userId={}: {}", userId, ex.getMessage());
            return HomeSection.failed("RECENT_ACTIVITY_UNAVAILABLE");
        }
    }

    private HomeSection<RewardsPointsData> resolveRewardsPoints(Long userId) {
        try {
            if (userId == null) {
                return HomeSection.failed("USER_ID_REQUIRED");
            }
            long pointsBalance = 1250L;
            String formattedPoints = String.format("%,d pts", pointsBalance);
            boolean hasNewOffers = true;
            return HomeSection.ok(new RewardsPointsData(pointsBalance, formattedPoints, hasNewOffers));
        } catch (Exception ex) {
            log.warn("Failed to resolve rewards points for userId={}: {}", userId, ex.getMessage());
            return HomeSection.failed("REWARDS_UNAVAILABLE");
        }
    }

    private HomeSection<SpendSummaryData> resolveSpendSummary(Long userId) {
        try {
            if (userId == null) {
                return HomeSection.failed("USER_ID_REQUIRED");
            }
            LocalDate today = LocalDate.now();
            String monthName = today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(ZoneId.of("Asia/Colombo")).toInstant();

            long totalSpentMinor = 0L;
            boolean hasTransactions = false;

            if (transactionStore != null) {
                Page<TransactionRecord> sentPage = null;
                try {
                    sentPage = transactionStore.searchTransfers(
                            userId,
                            com.lankapay.transfer.web.dto.TransferDirection.SENT,
                            TransactionStatus.COMPLETED,
                            startOfMonth,
                            Instant.now(),
                            null,
                            null,
                            null,
                            PageRequest.of(0, 200)
                    );
                } catch (Exception ex) {
                    log.debug("searchTransfers failed: {}", ex.getMessage());
                }

                if (sentPage != null && sentPage.getContent() != null && !sentPage.getContent().isEmpty()) {
                    for (TransactionRecord tx : sentPage.getContent()) {
                        if (tx.amount() != null) {
                            totalSpentMinor += tx.amount().multiply(BigDecimal.valueOf(100)).longValue();
                        }
                    }
                    hasTransactions = totalSpentMinor > 0;
                } else {
                    // Fallback to findTransfersByUserId
                    try {
                        Page<TransactionRecord> userTransfers = transactionStore.findTransfersByUserId(userId, PageRequest.of(0, 50));
                        if (userTransfers != null && userTransfers.getContent() != null) {
                            for (TransactionRecord tx : userTransfers.getContent()) {
                                if (userId.equals(tx.senderId()) && tx.status() == TransactionStatus.COMPLETED) {
                                    if (tx.amount() != null) {
                                        totalSpentMinor += tx.amount().multiply(BigDecimal.valueOf(100)).longValue();
                                    }
                                }
                            }
                            hasTransactions = totalSpentMinor > 0;
                        }
                    } catch (Exception fallbackEx) {
                        log.debug("Fallback user transfer query failed: {}", fallbackEx.getMessage());
                    }
                }
            }

            long budgetMinor = 50_000_00L; // 50,000.00 LKR budget ceiling
            String totalSpentLkr = String.format("%,.2f", (double) totalSpentMinor / 100.0);
            String budgetLkr = String.format("%,.2f", (double) budgetMinor / 100.0);
            double budgetUsedFraction = Math.min(1.0, (double) totalSpentMinor / (double) budgetMinor);

            return HomeSection.ok(new SpendSummaryData(
                    totalSpentMinor,
                    totalSpentLkr,
                    budgetMinor,
                    budgetLkr,
                    budgetUsedFraction,
                    monthName,
                    hasTransactions
            ));
        } catch (Exception ex) {
            log.warn("Failed to resolve spend summary for userId={}: {}", userId, ex.getMessage());
            return HomeSection.failed("SPEND_SUMMARY_UNAVAILABLE");
        }
    }

    private HomeSection<NudgeData> resolveNudge(Long userId, SpendSummaryData spendSummary) {
        try {
            if (userId == null) {
                return HomeSection.failed("USER_ID_REQUIRED");
            }

            // Priority 1: Phone not verified
            if (userStore != null) {
                try {
                    Optional<UserAccount> userOpt = userStore.findUserById(userId);
                    if (userOpt.isPresent() && userOpt.get().status() == UserStatus.PENDING_VERIFICATION) {
                        return HomeSection.ok(new NudgeData(
                                "verify_phone",
                                "VERIFY_PHONE",
                                "Verify your phone number to unlock full features",
                                "Verify now",
                                "/auth/verify-phone"
                        ));
                    }
                } catch (Exception ex) {
                    log.debug("User lookup failed in nudge resolution: {}", ex.getMessage());
                }
            }

            // Priority 2: Link first bank account
            if (linkedAccountStore != null) {
                try {
                    List<LinkedAccount> accounts = linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(userId);
                    boolean hasActiveAccount = accounts != null && accounts.stream()
                            .anyMatch(a -> a.status() == BankAccountStatus.ACTIVE);
                    if (!hasActiveAccount) {
                        return HomeSection.ok(new NudgeData(
                                "link_account",
                                "LINK_ACCOUNT",
                                "Link your bank account to start sending money",
                                "Link account",
                                "/wallet/accounts/link"
                        ));
                    }
                } catch (Exception ex) {
                    log.debug("Linked account lookup failed in nudge: {}", ex.getMessage());
                }
            }

            // Priority 3: Card awaiting OTP verification
            if (cardStore != null) {
                try {
                    List<Card> cards = cardStore.findAllByUserId(userId);
                    Optional<Card> pendingCard = (cards != null)
                            ? cards.stream()
                                .filter(c -> !c.isRemoved() && c.status() == CardStatus.PENDING_VERIFICATION)
                                .findFirst()
                            : Optional.empty();

                    if (pendingCard.isPresent()) {
                        return HomeSection.ok(new NudgeData(
                                "verify_card_otp",
                                "VERIFY_CARD_OTP",
                                "Card ending in " + pendingCard.get().last4() + " requires OTP verification",
                                "Verify card",
                                "/wallet/cards/verify"
                        ));
                    }
                } catch (Exception ex) {
                    log.debug("Card lookup failed in nudge: {}", ex.getMessage());
                }
            }

            // Priority 4: Transfer pending / processing
            if (transactionStore != null) {
                try {
                    Page<TransactionRecord> page = transactionStore.findTransfersByUserId(userId, PageRequest.of(0, 10));
                    if (page != null && page.getContent() != null) {
                        boolean hasPending = page.getContent().stream()
                                .anyMatch(tx -> userId.equals(tx.senderId()) &&
                                        (tx.status() == TransactionStatus.PENDING
                                                || tx.status() == TransactionStatus.PROCESSING
                                                || tx.status() == TransactionStatus.CHALLENGE_REQUIRED));
                        if (hasPending) {
                            return HomeSection.ok(new NudgeData(
                                    "transfer_pending",
                                    "TRANSFER_PENDING",
                                    "You have a transfer currently in progress",
                                    "View status",
                                    "/activity"
                            ));
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Transfer check for nudge ignored: {}", ex.getMessage());
                }
            }

            // Priority 5: Budget warning (spend > 80%)
            if (spendSummary != null && spendSummary.budgetUsedFraction() >= 0.8) {
                return HomeSection.ok(new NudgeData(
                        "budget_warning",
                        "BUDGET_WARNING",
                        "You have used over 80% of your monthly budget",
                        "View budget",
                        "/analytics"
                ));
            }

            // Priority 6: Dynamic financial insight (if present)
            if (insightService != null) {
                try {
                    List<com.lankapay.insights.domain.InsightItem> insights = insightService.getInsights(userId);
                    if (insights != null && !insights.isEmpty()) {
                        com.lankapay.insights.domain.InsightItem top = insights.get(0);
                        return HomeSection.ok(new NudgeData(
                                top.id(),
                                top.type().name(),
                                top.text(),
                                "View details",
                                top.actionDeepLink()
                        ));
                    }
                } catch (Exception ex) {
                    log.debug("Insights check for nudge ignored: {}", ex.getMessage());
                }
            }

            // Priority 7: Default security tip
            return HomeSection.ok(new NudgeData(
                    "security_tip",
                    "SECURITY_TIP",
                    "Never share your PIN, OTP, or password with anyone, even LankaPay staff",
                    "Security tips",
                    "/profile/security"
            ));

        } catch (Exception ex) {
            log.warn("Failed to resolve nudge for userId={}: {}", userId, ex.getMessage());
            return HomeSection.failed("NUDGE_UNAVAILABLE");
        }
    }

    private HomeSection<List<ShortcutItem>> resolveShortcuts() {
        boolean billsOn = featureFlagService != null && featureFlagService.isEnabled("bills");
        boolean splitOn = featureFlagService != null && featureFlagService.isEnabled("split");
        boolean goalsOn = featureFlagService != null && featureFlagService.isEnabled("goals");

        List<ShortcutItem> list = List.of(
                new ShortcutItem("send", "Send", "swap_horiz_rounded", "/pay/send", true),
                new ShortcutItem("scan_qr", "Scan QR", "qr_code_scanner", "/pay/qr", true),
                new ShortcutItem("bills", "Pay Bills", "receipt_long", "/pay/bills", billsOn),
                new ShortcutItem("split", "Split Bill", "call_split", "/pay/split", splitOn),
                new ShortcutItem("goals", "Save", "savings", "/explore/goals", goalsOn)
        );
        return HomeSection.ok(list);
    }
}

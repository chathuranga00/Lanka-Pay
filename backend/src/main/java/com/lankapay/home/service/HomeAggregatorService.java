package com.lankapay.home.service;

import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardStore;
import com.lankapay.feature.service.FeatureFlagService;
import com.lankapay.home.domain.HomeSection;
import com.lankapay.home.web.dto.*;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.domain.NotificationStore;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
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

    public HomeAggregatorService(
            UserStore userStore,
            NotificationStore notificationStore,
            LinkedAccountStore linkedAccountStore,
            CardStore cardStore,
            TransactionStore transactionStore,
            FeatureFlagService featureFlagService) {
        this.userStore = userStore;
        this.notificationStore = notificationStore;
        this.linkedAccountStore = linkedAccountStore;
        this.cardStore = cardStore;
        this.transactionStore = transactionStore;
        this.featureFlagService = featureFlagService;
    }

    public HomeAggregateResponse aggregateHome(Long userId) {
        HomeSection<GreetingData> greeting = resolveGreeting(userId);
        HomeSection<NotificationData> notifications = resolveNotifications(userId);
        HomeSection<WalletData> wallet = resolveWallet(userId);
        HomeSection<List<RecentActivityItem>> recentActivity = resolveRecentActivity(userId);

        // Not-yet-implemented modules return UNAVAILABLE with an explicit error marker
        HomeSection<Object> rewardsPoints = HomeSection.unavailable("MODULE_NOT_YET_IMPLEMENTED");
        HomeSection<Object> stories = HomeSection.unavailable("MODULE_NOT_YET_IMPLEMENTED");
        HomeSection<Object> spendSummary = HomeSection.unavailable("MODULE_NOT_YET_IMPLEMENTED");
        HomeSection<Object> nudge = HomeSection.unavailable("MODULE_NOT_YET_IMPLEMENTED");

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

            List<LinkedAccount> activeAccounts = accounts.stream()
                    .filter(a -> a.status() == BankAccountStatus.ACTIVE)
                    .toList();

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
            List<Card> nonRemovedCards = cards.stream()
                    .filter(c -> !c.isRemoved())
                    .toList();

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

            return HomeSection.ok(items);
        } catch (Exception ex) {
            log.warn("Failed to resolve recent activity for userId={}: {}", userId, ex.getMessage());
            return HomeSection.failed("RECENT_ACTIVITY_UNAVAILABLE");
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

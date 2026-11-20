package com.lankapay.search.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.feature.service.FeatureFlagService;
import com.lankapay.search.domain.SearchResultType;
import com.lankapay.search.web.dto.SearchResultItem;
import com.lankapay.search.web.dto.SearchResponse;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Global search service for LankaPay ID Super App.
 *
 * Scoping & Security Rules:
 * - Scoped strictly to the authenticated user's own data (transactions, recent recipients)
 *   and public catalogs (features/screens, billers, merchants).
 * - A phone-number-looking query NEVER returns account holders or users.
 *   It returns a single "Send to this number" action pointing to the existing lookup flow.
 * - No user enumeration.
 * - Suspicious or abusive query patterns are audited.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9\\s\\-()]{7,15}$");
    private static final Pattern DIGITS_ONLY = Pattern.compile("[^0-9+]");
    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "(?i)('|--|/\\*|union\\s+select|select\\s+.*\\s+from|<script|javascript:|exec\\s+|drop\\s+table)"
    );

    private final TransactionStore transactionStore;
    private final FeatureFlagService featureFlagService;
    private final AuditService auditService;

    public SearchService(
            TransactionStore transactionStore,
            FeatureFlagService featureFlagService,
            AuditService auditService) {
        this.transactionStore = transactionStore;
        this.featureFlagService = featureFlagService;
        this.auditService = auditService;
    }

    /**
     * Execute search query for an authenticated user.
     */
    public SearchResponse search(Long userId, String query, Set<SearchResultType> requestedTypes, String clientIp) {
        if (userId == null) {
            throw new ValidationException("UNAUTHORIZED", "User must be authenticated");
        }

        if (query == null || query.trim().length() < 2) {
            throw new ValidationException("SEARCH_QUERY_TOO_SHORT", "Search query must be at least 2 characters");
        }

        final String trimmedQuery = query.trim();
        final String qLower = trimmedQuery.toLowerCase();
        final Set<SearchResultType> types = (requestedTypes == null || requestedTypes.isEmpty())
                ? EnumSet.allOf(SearchResultType.class)
                : requestedTypes;

        // 1. Audit abusive patterns (SQL injection, XSS attempts, abnormally long patterns)
        boolean suspicious = false;
        if (INJECTION_PATTERN.matcher(trimmedQuery).find() || trimmedQuery.length() > 100) {
            suspicious = true;
            log.warn("Abusive/suspicious search pattern detected for userId={}: length={}", userId, trimmedQuery.length());
            if (auditService != null) {
                auditService.log(
                        userId,
                        "SEARCH_ABUSE_SUSPECTED",
                        "SEARCH",
                        null,
                        "{\"queryLength\":" + trimmedQuery.length() + ",\"reason\":\"SUSPICIOUS_PAYLOAD\"}",
                        clientIp != null ? clientIp : "127.0.0.1"
                );
            }
        }

        // 2. Anti-enumeration: Check if query looks like a phone number
        String digitsOnly = DIGITS_ONLY.matcher(trimmedQuery).replaceAll("");
        boolean isPhoneLike = PHONE_PATTERN.matcher(trimmedQuery).matches()
                && digitsOnly.length() >= 7 && digitsOnly.length() <= 15;

        List<SearchResultItem> allResults = new ArrayList<>();

        if (isPhoneLike) {
            // Anti-enumeration invariant: NEVER search users by phone. Return single action.
            if (types.contains(SearchResultType.ACTION)) {
                String normalizedPhone = digitsOnly.startsWith("+") ? digitsOnly : digitsOnly;
                allResults.add(new SearchResultItem(
                        "action-send-" + normalizedPhone,
                        SearchResultType.ACTION,
                        "Send to this number",
                        normalizedPhone,
                        "PAY",
                        "app://send?recipient=" + URLEncoder.encode(normalizedPhone, StandardCharsets.UTF_8),
                        "send",
                        Map.of("phone", normalizedPhone, "action", "LOOKUP_AND_SEND")
                ));
            }
        } else {
            // Standard query: search features, own transactions, billers, merchants, recent recipients

            // A. Features / Screens (respecting feature flags)
            if (types.contains(SearchResultType.FEATURE)) {
                allResults.addAll(searchFeatures(qLower));
            }

            // B. Public Billers
            if (types.contains(SearchResultType.BILLER)) {
                allResults.addAll(searchBillers(qLower));
            }

            // C. Public Merchants
            if (types.contains(SearchResultType.MERCHANT)) {
                allResults.addAll(searchMerchants(qLower));
            }

            // D. User's Own Transactions (Strict Object-Level Access Control)
            if (types.contains(SearchResultType.TRANSACTION)) {
                allResults.addAll(searchOwnTransactions(userId, qLower));
            }

            // E. Recent Recipients (strictly from user's own past payments)
            if (types.contains(SearchResultType.RECIPIENT)) {
                allResults.addAll(searchRecentRecipients(userId, qLower));
            }
        }

        // Group results by type
        Map<SearchResultType, List<SearchResultItem>> grouped = allResults.stream()
                .collect(Collectors.groupingBy(
                        SearchResultItem::type,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // Audit standard search execution
        if (!suspicious && auditService != null) {
            auditService.log(
                    userId,
                    "SEARCH_QUERY",
                    "SEARCH",
                    null,
                    "{\"queryLength\":" + trimmedQuery.length() + ",\"resultCount\":" + allResults.size() + "}",
                    clientIp != null ? clientIp : "127.0.0.1"
            );
        }

        return new SearchResponse(trimmedQuery, allResults.size(), allResults, grouped);
    }

    // ── Search sub-methods ──────────────────────────────────────

    private List<SearchResultItem> searchFeatures(String qLower) {
        List<FeatureDefinition> catalog = List.of(
                new FeatureDefinition("feat-send", "Send Money", "Instant peer-to-peer bank transfers", "PAY", "/send", "swap_horiz", null),
                new FeatureDefinition("feat-qr", "LankaQR Payment", "Scan merchant or static QR codes", "PAY", "/qr", "qr_code", null),
                new FeatureDefinition("feat-bills", "Pay Utility Bills", "Electricity, water, and telecom bills", "PAY", "/pay/bills", "receipt_long", "bills"),
                new FeatureDefinition("feat-split", "Split Bill", "Split dinner or group expenses with friends", "PAY", "/pay/split", "call_split", "split"),
                new FeatureDefinition("feat-sched", "Scheduled Payments", "Standing orders and recurring transfers", "PAY", "/pay/scheduled", "schedule", "scheduled"),
                new FeatureDefinition("feat-accounts", "Bank Accounts", "Manage connected commercial bank accounts", "ACCOUNTS", "/accounts", "account_balance", null),
                new FeatureDefinition("feat-cards", "Virtual & Debit Cards", "Manage cards, limits, and security", "CARDS", "/cards", "credit_card", "cards"),
                new FeatureDefinition("feat-city", "City Directory", "Discover local LankaQR merchants and places", "CITY", "/city", "storefront", "city"),
                new FeatureDefinition("feat-chat", "Customer Support", "In-app support chat and banking FAQs", "SUPPORT", "/chat", "chat_bubble", "support"),
                new FeatureDefinition("feat-goals", "Savings Goals", "Smart piggy banks and target savings", "GOALS", "/goals", "savings", "goals"),
                new FeatureDefinition("feat-rewards", "Rewards & Points", "Loyalty ledger and partner vouchers", "REWARDS", "/rewards", "stars", "rewards"),
                new FeatureDefinition("feat-analytics", "Spending Analytics", "Categorized spend and downloadable statements", "ANALYTICS", "/analytics", "donut_large", "analytics"),
                new FeatureDefinition("feat-history", "Transaction History", "View past transfers and official receipts", "HISTORY", "/history", "history", null),
                new FeatureDefinition("feat-notifs", "Notifications", "System alerts and transaction notices", "NOTIFICATIONS", "/notifications", "notifications", null),
                new FeatureDefinition("feat-security", "Security & PIN", "Change app PIN, biometric login, devices", "SETTINGS", "/settings/security", "lock", null),
                new FeatureDefinition("feat-privacy", "Privacy Settings", "Payment proxy discovery and privacy controls", "SETTINGS", "/settings/privacy", "shield", null)
        );

        List<SearchResultItem> results = new ArrayList<>();
        for (FeatureDefinition feat : catalog) {
            // Check feature flag if attached
            if (feat.flagKey() != null && featureFlagService != null && !featureFlagService.isEnabled(feat.flagKey())) {
                continue;
            }

            if (feat.title().toLowerCase().contains(qLower)
                    || feat.description().toLowerCase().contains(qLower)
                    || feat.category().toLowerCase().contains(qLower)) {
                results.add(new SearchResultItem(
                        feat.id(),
                        SearchResultType.FEATURE,
                        feat.title(),
                        feat.description(),
                        feat.category(),
                        feat.route(),
                        feat.icon()
                ));
            }
        }
        return results;
    }

    private List<SearchResultItem> searchBillers(String qLower) {
        List<BillerDefinition> catalog = List.of(
                new BillerDefinition("biller-ceb", "CEB", "Ceylon Electricity Board", "Electricity", "/bills/ceb"),
                new BillerDefinition("biller-leco", "LECO", "Lanka Electricity Company", "Electricity", "/bills/leco"),
                new BillerDefinition("biller-water", "NWSDB", "National Water Supply & Drainage Board", "Water", "/bills/water"),
                new BillerDefinition("biller-dialog", "DIALOG", "Dialog Axiata Telecom & DTV", "Telecom", "/bills/dialog"),
                new BillerDefinition("biller-mobitel", "MOBITEL", "SLTMobitel Mobile & Broadband", "Telecom", "/bills/mobitel"),
                new BillerDefinition("biller-slt", "SLT", "Sri Lanka Telecom Fiber & Fixed", "Telecom", "/bills/slt"),
                new BillerDefinition("biller-airtel", "AIRTEL", "Airtel Telecommunications", "Telecom", "/bills/airtel"),
                new BillerDefinition("biller-slic", "SLIC", "Sri Lanka Insurance Life & General", "Insurance", "/bills/slic"),
                new BillerDefinition("biller-cmc", "CMC", "Colombo Municipal Council Rates", "Municipal", "/bills/cmc")
        );

        List<SearchResultItem> results = new ArrayList<>();
        for (BillerDefinition b : catalog) {
            if (b.name().toLowerCase().contains(qLower)
                    || b.code().toLowerCase().contains(qLower)
                    || b.category().toLowerCase().contains(qLower)) {
                results.add(new SearchResultItem(
                        b.id(),
                        SearchResultType.BILLER,
                        b.name() + " (" + b.code() + ")",
                        b.category() + " • " + b.code(),
                        "BILLER",
                        b.route(),
                        "receipt"
                ));
            }
        }
        return results;
    }

    private List<SearchResultItem> searchMerchants(String qLower) {
        List<MerchantDefinition> catalog = List.of(
                new MerchantDefinition("m-keells", "Keells Super", "Supermarket & Groceries", "Colombo 03", "/city/merchants/keells"),
                new MerchantDefinition("m-cargills", "Cargills Food City", "Supermarket & Daily Essentials", "Islandwide", "/city/merchants/cargills"),
                new MerchantDefinition("m-coffee", "Colombo Coffee Club", "Cafe & Artisanal Dining", "Colombo 07", "/city/merchants/coffee"),
                new MerchantDefinition("m-odel", "Odel Department Store", "Fashion & Lifestyle", "Alexandra Place", "/city/merchants/odel"),
                new MerchantDefinition("m-softlogic", "Softlogic Max", "Consumer Electronics", "Colombo 04", "/city/merchants/softlogic"),
                new MerchantDefinition("m-spacey", "Spa Ceylon Luxury Ayurveda", "Wellness & Skincare", "Galle Face", "/city/merchants/spa-ceylon"),
                new MerchantDefinition("m-pns", "Perera & Sons (P&S)", "Bakery & Confectionery", "Colombo", "/city/merchants/pns"),
                new MerchantDefinition("m-laugfs", "Laugfs Supermarket", "Retail Groceries & Fuel", "Rajagiriya", "/city/merchants/laugfs")
        );

        List<SearchResultItem> results = new ArrayList<>();
        for (MerchantDefinition m : catalog) {
            if (m.name().toLowerCase().contains(qLower)
                    || m.category().toLowerCase().contains(qLower)
                    || m.location().toLowerCase().contains(qLower)) {
                results.add(new SearchResultItem(
                        m.id(),
                        SearchResultType.MERCHANT,
                        m.name(),
                        m.category() + " • " + m.location(),
                        "MERCHANT",
                        m.route(),
                        "store"
                ));
            }
        }
        return results;
    }

    private List<SearchResultItem> searchOwnTransactions(Long userId, String qLower) {
        if (transactionStore == null) return List.of();

        // Object-Level Access: strictly query only for the authenticated user
        var page = transactionStore.findTransfersByUserId(userId, PageRequest.of(0, 50));
        if (page == null || page.isEmpty()) return List.of();

        List<SearchResultItem> results = new ArrayList<>();
        for (TransactionRecord tx : page.getContent()) {
            // Extra safety guard: ensure userId is either sender or receiver
            boolean isSender = userId.equals(tx.senderId());
            boolean isReceiver = tx.receiverId() != null && userId.equals(tx.receiverId());
            if (!isSender && !isReceiver) {
                continue; // Enforce object-level access control
            }

            String ref = tx.reference() != null ? tx.reference().toLowerCase() : "";
            String note = tx.note() != null ? tx.note().toLowerCase() : "";
            String receiverName = tx.receiverFullName() != null ? tx.receiverFullName().toLowerCase() : "";
            String senderName = tx.senderFullName() != null ? tx.senderFullName().toLowerCase() : "";

            if (ref.contains(qLower) || note.contains(qLower) || receiverName.contains(qLower) || senderName.contains(qLower)) {
                String title = (tx.note() != null && !tx.note().isBlank())
                        ? tx.note()
                        : (isSender ? "Sent to " + (tx.receiverFullName() != null ? tx.receiverFullName() : "recipient")
                                    : "Received from " + (tx.senderFullName() != null ? tx.senderFullName() : "sender"));

                String subtitle = tx.reference() + " • LKR " + tx.amount();
                results.add(new SearchResultItem(
                        "txn-" + tx.id(),
                        SearchResultType.TRANSACTION,
                        title,
                        subtitle,
                        "TRANSACTION",
                        "/history/receipt/" + tx.reference(),
                        "receipt",
                        Map.of(
                                "reference", tx.reference(),
                                "amount", tx.amount().toString(),
                                "status", tx.status() != null ? tx.status().name() : "COMPLETED"
                        )
                ));
            }
        }
        return results;
    }

    private List<SearchResultItem> searchRecentRecipients(Long userId, String qLower) {
        if (transactionStore == null) return List.of();

        var page = transactionStore.findTransfersBySenderId(userId, PageRequest.of(0, 50));
        if (page == null || page.isEmpty()) return List.of();

        // Distinct recipient names that this user has successfully paid
        Set<String> recipientNames = new LinkedHashSet<>();
        for (TransactionRecord tx : page.getContent()) {
            if (userId.equals(tx.senderId()) && tx.receiverFullName() != null && !tx.receiverFullName().isBlank()) {
                recipientNames.add(tx.receiverFullName());
            }
        }

        List<SearchResultItem> results = new ArrayList<>();
        for (String name : recipientNames) {
            if (name.toLowerCase().contains(qLower)) {
                results.add(new SearchResultItem(
                        "recip-" + Math.abs(name.hashCode()),
                        SearchResultType.RECIPIENT,
                        name,
                        "Recent Recipient",
                        "RECIPIENT",
                        "app://send?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8),
                        "person",
                        Map.of("recipientName", name)
                ));
            }
        }
        return results;
    }

    // Helper records for static catalogs
    private record FeatureDefinition(String id, String title, String description, String category, String route, String icon, String flagKey) {}
    private record BillerDefinition(String id, String code, String name, String category, String route) {}
    private record MerchantDefinition(String id, String name, String category, String location, String route) {}
}

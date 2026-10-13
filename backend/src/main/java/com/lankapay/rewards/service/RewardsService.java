package com.lankapay.rewards.service;

import com.lankapay.admin.service.RiskManagementService;
import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.InvalidCredentialsException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.rewards.store.InMemoryPointsLedgerStore;
import com.lankapay.rewards.store.InMemoryRewardOfferStore;
import com.lankapay.rewards.domain.PointsEntryType;
import com.lankapay.rewards.domain.PointsLedgerEntry;
import com.lankapay.rewards.domain.PointsLedgerStore;
import com.lankapay.rewards.domain.RewardOffer;
import com.lankapay.rewards.domain.RewardTier;
import com.lankapay.rewards.domain.RewardsConfig;
import com.lankapay.rewards.event.TransactionConfirmedEvent;
import com.lankapay.rewards.event.TransactionReversedEvent;
import com.lankapay.rewards.web.dto.PointsLedgerEntryDto;
import com.lankapay.rewards.web.dto.RedeemPointsRequest;
import com.lankapay.rewards.web.dto.RedeemPointsResponse;
import com.lankapay.rewards.web.dto.RewardOfferDto;
import com.lankapay.rewards.web.dto.RewardsSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service orchestrating rewards points earning, redemption, offers, and ledger auditing.
 * Enforces strict append-only ledger semantics, caps, anti-abuse checks, and idempotency.
 */
@Service
public class RewardsService {

    private static final Logger log = LoggerFactory.getLogger(RewardsService.class);

    private final PointsLedgerStore ledgerStore;
    private final InMemoryRewardOfferStore offerStore;
    private final RewardsConfig config;
    private final UserStore userStore;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final RiskManagementService riskManagementService;

    // In-memory idempotency cache for redemption: Idempotency-Key -> Cached Response
    private final Map<String, RedeemPointsResponse> redemptionIdempotencyCache = new ConcurrentHashMap<>();

    // User ID -> Timestamps of recent transactions in the last hour for velocity checking
    private final Map<Long, List<Instant>> userVelocityTimestamps = new ConcurrentHashMap<>();

    @Autowired
    public RewardsService(
            @Autowired(required = false) PointsLedgerStore ledgerStore,
            @Autowired(required = false) InMemoryRewardOfferStore offerStore,
            @Autowired(required = false) UserStore userStore,
            @Autowired(required = false) PasswordEncoder passwordEncoder,
            @Autowired(required = false) AuditService auditService,
            @Autowired(required = false) NotificationService notificationService,
            @Autowired(required = false) RiskManagementService riskManagementService
    ) {
        this.ledgerStore = ledgerStore != null ? ledgerStore : new InMemoryPointsLedgerStore();
        this.offerStore = offerStore != null ? offerStore : new InMemoryRewardOfferStore();
        this.config = RewardsConfig.defaultConfig();
        this.userStore = userStore;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.riskManagementService = riskManagementService;

        initDefaultOffers();
    }

    private void initDefaultOffers() {
        if (offerStore.findAll().isEmpty()) {
            offerStore.save(new RewardOffer(
                    "OFFER-BILL-2X",
                    "2x Points on Utility Bills",
                    "Earn double simulated points on CEB, Water Board, and Telecom bill settlements this month.",
                    "BILLS",
                    2.0,
                    null,
                    Instant.now().minus(5, ChronoUnit.DAYS),
                    Instant.now().plus(60, ChronoUnit.DAYS),
                    100,
                    "Valid for verified utility bill payments up to 100 points bonus. Simulated points only.",
                    true
            ));

            offerStore.save(new RewardOffer(
                    "OFFER-QR-LANKAQR",
                    "LankaQR Merchant Bonus",
                    "Get 50 bonus points on your next LankaQR purchase above 1,000 LKR.",
                    "LANKAQR",
                    null,
                    50,
                    Instant.now().minus(2, ChronoUnit.DAYS),
                    Instant.now().plus(45, ChronoUnit.DAYS),
                    50,
                    "Applies to merchant QR payments. Simulated points only.",
                    true
            ));

            offerStore.save(new RewardOffer(
                    "OFFER-FOOD-3X",
                    "3x Points on Dining & Cafes",
                    "Triple points when paying at partnered restaurants and cafes.",
                    "FOOD",
                    3.0,
                    null,
                    Instant.now().minus(1, ChronoUnit.DAYS),
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    150,
                    "Valid at registered dining merchants. Simulated points only.",
                    true
            ));
        }
    }

    // -------------------------------------------------------------------------
    // 1. Transaction Confirmed Event Handling (EARN)
    // -------------------------------------------------------------------------

    /**
     * Idempotently process confirmed transaction to award simulated points.
     * Enforces anti-abuse: self-dealing prevention, daily/monthly caps, velocity checks.
     */
    public synchronized Optional<PointsLedgerEntry> handleTransactionConfirmed(TransactionConfirmedEvent event) {
        if (event == null || event.transactionId() == null || event.senderUserId() == null) {
            log.warn("Ignoring invalid or incomplete confirmed event: {}", event);
            return Optional.empty();
        }

        final Long userId = event.senderUserId();

        // 1. Idempotency Check per transaction ID
        if (ledgerStore.hasEarnedForTransaction(userId, event.transactionId())) {
            log.info("Idempotent ignore: transaction {} already earned points for user {}", event.transactionId(), userId);
            return Optional.empty();
        }

        // 2. Anti-Abuse: Self-Dealing Check
        // No points for transferring to yourself or between accounts you own
        if (event.receiverUserId() != null && event.receiverUserId().equals(userId)) {
            log.warn("Anti-abuse trigger: Self-dealing blocked for user {} on transaction {}", userId, event.transactionId());
            return Optional.empty();
        }
        if (event.senderAccountNumber() != null && event.senderAccountNumber().equals(event.receiverAccountNumber())
                && event.senderBankCode() != null && event.senderBankCode().equals(event.receiverBankCode())) {
            log.warn("Anti-abuse trigger: Same account transfer blocked for user {} on transaction {}", userId, event.transactionId());
            return Optional.empty();
        }

        // 3. Anti-Abuse: Velocity & Fraud Checks
        if (isVelocityExceeded(userId)) {
            log.warn("Anti-abuse trigger: Velocity limit exceeded for user {}", userId);
            if (riskManagementService != null) {
                try {
                    User dummyUser = new User();
                    dummyUser.setId(userId);
                    riskManagementService.raiseAlert(
                            dummyUser,
                            null,
                            com.lankapay.admin.domain.FraudAlertType.VELOCITY_EXCEEDED,
                            com.lankapay.admin.domain.FraudAlertSeverity.HIGH,
                            "User exceeded rewards velocity limit of " + config.velocityMaxTxnPerHour() + "/hr",
                            "{\"userId\":" + userId + "}"
                    );
                } catch (Exception ignored) {}
            }
            return Optional.empty();
        }

        // 4. Calculate Base Points & Active Offers
        int basePoints = calculateBasePoints(event);
        if (basePoints <= 0) {
            return Optional.empty();
        }

        // Check active offers for category or merchant
        String matchedOfferId = null;
        int offerBonusPoints = 0;
        Instant now = Instant.now();

        for (RewardOffer offer : offerStore.findAll()) {
            if (offer.isValidAt(now) && isOfferApplicable(offer, event)) {
                matchedOfferId = offer.id();
                if (offer.pointsMultiplier() != null && offer.pointsMultiplier() > 1.0) {
                    int multiplied = (int) Math.round(basePoints * (offer.pointsMultiplier() - 1.0));
                    if (offer.maxPointsCap() != null) {
                        multiplied = Math.min(multiplied, offer.maxPointsCap());
                    }
                    offerBonusPoints += multiplied;
                } else if (offer.fixedPoints() != null) {
                    int fixed = offer.fixedPoints();
                    if (offer.maxPointsCap() != null) {
                        fixed = Math.min(fixed, offer.maxPointsCap());
                    }
                    offerBonusPoints += fixed;
                }
                break; // Apply best matching offer
            }
        }

        int totalToEarn = basePoints + offerBonusPoints;

        // Cap per transaction
        totalToEarn = Math.min(totalToEarn, config.maxPointsPerTransaction());

        // 5. Enforce Daily and Monthly Caps
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        int earnedToday = ledgerStore.getEarnedPointsToday(userId, startOfDay);
        int dailyRemaining = Math.max(0, config.dailyPointsCap() - earnedToday);
        totalToEarn = Math.min(totalToEarn, dailyRemaining);

        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        int earnedThisMonth = ledgerStore.getEarnedPointsThisMonth(userId, startOfMonth);
        int monthlyRemaining = Math.max(0, config.monthlyPointsCap() - earnedThisMonth);
        totalToEarn = Math.min(totalToEarn, monthlyRemaining);

        if (totalToEarn <= 0) {
            log.info("Points cap reached for user {} (daily or monthly limit reached)", userId);
            return Optional.empty();
        }

        // 6. Append Immutable Ledger Entry
        Instant expiresAt = Instant.now().plus(config.defaultExpirationDays(), ChronoUnit.DAYS);
        Map<String, String> meta = new HashMap<>();
        meta.put("transactionType", event.transactionType() != null ? event.transactionType() : "UNKNOWN");
        if (event.merchantOrCategory() != null) meta.put("category", event.merchantOrCategory());
        if (matchedOfferId != null) meta.put("offerId", matchedOfferId);
        meta.put("disclaimer", RewardsSummaryResponse.SIMULATED_DISCLAIMER);

        PointsLedgerEntry entry = ledgerStore.append(
                userId,
                PointsEntryType.EARN,
                totalToEarn,
                event.transactionId(),
                "Earned from " + (event.transactionType() != null ? event.transactionType() : "transaction"),
                matchedOfferId,
                expiresAt,
                meta
        );

        recordVelocity(userId);

        log.info("Awarded {} simulated points to user {} for txn {}", totalToEarn, userId, event.transactionId());
        return Optional.of(entry);
    }

    private int calculateBasePoints(TransactionConfirmedEvent event) {
        String type = event.transactionType() != null ? event.transactionType().toUpperCase() : "TRANSFER";
        switch (type) {
            case "BILL_PAYMENT":
                return config.billPaymentFixedPoints();
            case "QR_PAYMENT":
                if (event.amount() != null) {
                    return Math.max(1, event.amount().divideToIntegralValue(BigDecimal.valueOf(100)).intValue() * config.qrPaymentPointsPer100Lkr());
                }
                return 10;
            case "CARD_PAYMENT":
                int pts = 5;
                if (event.isFirstCardPayment()) {
                    pts += config.firstCardBonusPoints();
                }
                return pts;
            case "REFERRAL":
                return config.referralBonusPoints();
            default:
                // Standard P2P transfers do not earn points by default to avoid abuse
                return 0;
        }
    }

    private boolean isOfferApplicable(RewardOffer offer, TransactionConfirmedEvent event) {
        if (offer.merchantOrCategory() == null) return false;
        String offerTarget = offer.merchantOrCategory().toUpperCase();
        if (event.transactionType() != null && event.transactionType().equalsIgnoreCase(offerTarget)) return true;
        if (event.merchantOrCategory() != null && event.merchantOrCategory().equalsIgnoreCase(offerTarget)) return true;
        return false;
    }

    private boolean isVelocityExceeded(Long userId) {
        List<Instant> times = userVelocityTimestamps.get(userId);
        if (times == null) return false;
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long count = times.stream().filter(t -> t.isAfter(oneHourAgo)).count();
        return count >= config.velocityMaxTxnPerHour();
    }

    private void recordVelocity(Long userId) {
        userVelocityTimestamps.compute(userId, (k, v) -> {
            List<Instant> list = v != null ? v : new ArrayList<>();
            list.add(Instant.now());
            Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
            list.removeIf(t -> t.isBefore(oneHourAgo));
            return list;
        });
    }

    // -------------------------------------------------------------------------
    // 2. Transaction Reversed Event Handling (REVERSE)
    // -------------------------------------------------------------------------

    /**
     * When a transaction is refunded/reversed, reverse all earned points.
     */
    public synchronized Optional<PointsLedgerEntry> handleTransactionReversed(TransactionReversedEvent event) {
        if (event == null || event.transactionId() == null || event.userId() == null) {
            return Optional.empty();
        }

        List<PointsLedgerEntry> earnEntries = ledgerStore.findEarnEntriesForTransaction(event.userId(), event.transactionId());
        if (earnEntries.isEmpty()) {
            log.info("No points were earned for reversed transaction {}, nothing to reverse", event.transactionId());
            return Optional.empty();
        }

        int pointsToReverse = earnEntries.stream().mapToInt(PointsLedgerEntry::points).sum();
        if (pointsToReverse <= 0) return Optional.empty();

        Map<String, String> meta = new HashMap<>();
        meta.put("originalTransactionId", event.transactionId());
        meta.put("reversedReason", event.reason() != null ? event.reason() : "Transaction reversed");
        meta.put("disclaimer", RewardsSummaryResponse.SIMULATED_DISCLAIMER);

        PointsLedgerEntry entry = ledgerStore.append(
                event.userId(),
                PointsEntryType.REVERSE,
                -pointsToReverse,
                event.transactionId(),
                "Reversal of earned points: " + (event.reason() != null ? event.reason() : "Transaction reversed"),
                null,
                null,
                meta
        );

        log.info("Reversed {} points for user {} on reversed txn {}", pointsToReverse, event.userId(), event.transactionId());
        return Optional.of(entry);
    }

    // -------------------------------------------------------------------------
    // 3. User Summary & Ledger
    // -------------------------------------------------------------------------

    public RewardsSummaryResponse getSummary(Long userId) {
        int balance = ledgerStore.getCurrentBalance(userId);

        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        int confirmedCount = ledgerStore.getConfirmedTransactionCountThisMonth(userId, startOfMonth);
        RewardTier tier = RewardTier.fromMonthlyTransactionCount(confirmedCount);

        Instant now = Instant.now();
        Instant windowEnd = now.plus(30, ChronoUnit.DAYS);
        int expiringSoon = ledgerStore.getExpiringSoonPoints(userId, now, windowEnd);

        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        int earnedToday = ledgerStore.getEarnedPointsToday(userId, startOfDay);
        int earnedThisMonth = ledgerStore.getEarnedPointsThisMonth(userId, startOfMonth);

        int dailyCapRemaining = Math.max(0, config.dailyPointsCap() - earnedToday);
        int monthlyCapRemaining = Math.max(0, config.monthlyPointsCap() - earnedThisMonth);

        boolean hasNewOffers = !offerStore.findAll().isEmpty();

        return new RewardsSummaryResponse(
                balance,
                tier,
                tier.getDisplayName(),
                expiringSoon,
                RewardsSummaryResponse.SIMULATED_DISCLAIMER,
                earnedThisMonth,
                earnedToday,
                dailyCapRemaining,
                monthlyCapRemaining,
                hasNewOffers
        );
    }

    public List<PointsLedgerEntryDto> getLedger(Long userId, String typeFilter) {
        List<PointsLedgerEntry> entries = ledgerStore.findByUserId(userId);
        return entries.stream()
                .filter(e -> typeFilter == null || typeFilter.isBlank() || typeFilter.equalsIgnoreCase("ALL")
                        || e.type().name().equalsIgnoreCase(typeFilter))
                .sorted(Comparator.comparing(PointsLedgerEntry::createdAt).reversed())
                .map(PointsLedgerEntryDto::from)
                .collect(Collectors.toList());
    }

    public List<RewardOfferDto> getOffers() {
        Instant now = Instant.now();
        return offerStore.findAll().stream()
                .filter(o -> o.isValidAt(now))
                .map(RewardOfferDto::from)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // 4. Redemption Flow (POST /rewards/redeem)
    // -------------------------------------------------------------------------

    public RedeemPointsResponse redeemPoints(
            User user,
            RedeemPointsRequest request,
            String idempotencyKey,
            String stepUpPinHeader
    ) {
        if (request == null || request.points() == null || request.points() <= 0) {
            throw new ValidationException("INVALID_POINTS", "Redemption points must be greater than 0");
        }

        // 1. Step-up Authentication check
        String pinToVerify = (request.pin() != null && !request.pin().isBlank()) ? request.pin() : stepUpPinHeader;
        verifyStepUpPin(user.getId(), pinToVerify);

        // 2. Idempotency Check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            RedeemPointsResponse cached = redemptionIdempotencyCache.get(idempotencyKey);
            if (cached != null) {
                log.info("Idempotent replay of redemption key={}", idempotencyKey);
                return cached;
            }
        }

        // 3. Atomically check balance and append REDEEM entry
        PointsLedgerEntry entry;
        synchronized (this) {
            int currentBalance = ledgerStore.getCurrentBalance(user.getId());
            if (currentBalance < request.points()) {
                throw new ConflictException("INSUFFICIENT_POINTS",
                        "Insufficient simulated points. Balance: " + currentBalance + ", requested: " + request.points());
            }

            Map<String, String> meta = new HashMap<>();
            meta.put("rewardType", request.rewardType());
            meta.put("disclaimer", RewardsSummaryResponse.SIMULATED_DISCLAIMER);
            if (request.voucherPartner() != null) meta.put("voucherPartner", request.voucherPartner());

            entry = ledgerStore.append(
                    user.getId(),
                    PointsEntryType.REDEEM,
                    -request.points(),
                    null,
                    "Redeemed for " + request.rewardType() + (request.voucherPartner() != null ? " (" + request.voucherPartner() + ")" : ""),
                    null,
                    null,
                    meta
            );
        }

        // 4. Generate simulated voucher code or statement credit label
        String voucherCode = null;
        String statementCreditLabel = null;
        String rewardType = request.rewardType().toUpperCase();

        if (rewardType.contains("STATEMENT") || rewardType.contains("CREDIT")) {
            statementCreditLabel = "SIMULATED Statement Credit - " + (request.points() / 10) + " LKR Equivalent (No Cash Value)";
        } else {
            voucherCode = "VOUCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        RedeemPointsResponse response = new RedeemPointsResponse(
                entry.id(),
                request.rewardType(),
                request.points(),
                entry.runningBalance(),
                voucherCode,
                request.voucherPartner() != null ? request.voucherPartner() : "LankaPay Partner",
                statementCreditLabel,
                "COMPLETED",
                entry.createdAt(),
                RewardsSummaryResponse.SIMULATED_DISCLAIMER
        );

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redemptionIdempotencyCache.put(idempotencyKey, response);
        }

        if (auditService != null) {
            try {
                auditService.log(user.getId(), "REWARDS_REDEEM", "REWARD", null,
                        "{\"points\":" + request.points() + ",\"newBalance\":" + entry.runningBalance() + "}", "127.0.0.1");
            } catch (Exception ignored) {}
        }

        log.info("User {} redeemed {} points. Remaining balance: {}", user.getId(), request.points(), entry.runningBalance());
        return response;
    }

    private void verifyStepUpPin(Long userId, String pin) {
        if (pin == null || pin.isBlank()) {
            throw new AuthorizationException("STEP_UP_AUTH_REQUIRED", "Step-up authentication required: PIN must be provided");
        }
        if (userStore != null && passwordEncoder != null) {
            UserAccount account = userStore.findUserById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("USER", userId));
            if (!passwordEncoder.matches(pin, account.passwordHash())) {
                throw new InvalidCredentialsException("INVALID_PIN", "Invalid PIN provided for step-up authentication");
            }
        }
    }

    public PointsLedgerStore getLedgerStore() {
        return ledgerStore;
    }

    public InMemoryRewardOfferStore getOfferStore() {
        return offerStore;
    }
}

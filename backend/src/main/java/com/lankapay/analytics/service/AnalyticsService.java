package com.lankapay.analytics.service;

import com.lankapay.analytics.domain.*;
import com.lankapay.analytics.store.InMemoryStatementStore;
import com.lankapay.analytics.web.dto.*;
import com.lankapay.bill.domain.BillPaymentRecord;
import com.lankapay.bill.domain.BillPaymentStore;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.audit.service.AuditService;
import com.lankapay.transfer.domain.FundingType;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final ZoneId SRI_LANKA_ZONE = ZoneId.of("Asia/Colombo");

    private final TransactionStore transactionStore;
    private final BillPaymentStore billPaymentStore;
    private final CategoryClassificationService categoryClassificationService;
    private final UserTransactionCategoryStore categoryStore;
    private final BudgetStore budgetStore;
    private final InMemoryStatementStore statementStore;
    private final StatementPdfGenerator statementPdfGenerator;
    private final StatementRateLimiter statementRateLimiter;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final UserStore userStore;

    public AnalyticsService(
            TransactionStore transactionStore,
            BillPaymentStore billPaymentStore,
            CategoryClassificationService categoryClassificationService,
            UserTransactionCategoryStore categoryStore,
            BudgetStore budgetStore,
            InMemoryStatementStore statementStore,
            StatementPdfGenerator statementPdfGenerator,
            StatementRateLimiter statementRateLimiter,
            AuditService auditService,
            NotificationService notificationService,
            UserStore userStore) {
        this.transactionStore = transactionStore;
        this.billPaymentStore = billPaymentStore;
        this.categoryClassificationService = categoryClassificationService;
        this.categoryStore = categoryStore;
        this.budgetStore = budgetStore;
        this.statementStore = statementStore;
        this.statementPdfGenerator = statementPdfGenerator;
        this.statementRateLimiter = statementRateLimiter;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.userStore = userStore;
    }

    /**
     * Compute analytics summary for a given month.
     * Only confirmed SUCCESS (COMPLETED) transactions count towards spent and received.
     * Pending ones are calculated and isolated separately.
     */
    public AnalyticsSummaryResponse getSummary(Long userId, String monthInput) {
        String month = normalizeMonth(monthInput);
        YearMonth ym = YearMonth.parse(month);
        Instant startOfMonth = ym.atDay(1).atStartOfDay(SRI_LANKA_ZONE).toInstant();
        Instant endOfMonth = ym.atEndOfMonth().atTime(23, 59, 59, 999_999_999).atZone(SRI_LANKA_ZONE).toInstant();

        YearMonth prevYm = ym.minusMonths(1);
        Instant prevStart = prevYm.atDay(1).atStartOfDay(SRI_LANKA_ZONE).toInstant();
        Instant prevEnd = prevYm.atEndOfMonth().atTime(23, 59, 59, 999_999_999).atZone(SRI_LANKA_ZONE).toInstant();

        List<TransactionRecord> allTransfers = getUserTransfers(userId);
        List<BillPaymentRecord> allBills = getUserBills(userId);

        long spentMinor = 0L;
        long receivedMinor = 0L;
        long pendingSpentMinor = 0L;
        int pendingCount = 0;

        Map<TransactionCategory, CategoryAccumulator> categoryMap = new EnumMap<>(TransactionCategory.class);
        Map<String, PayeeAccumulator> payeeMap = new HashMap<>();

        // Process transfers in current month
        for (TransactionRecord tx : allTransfers) {
            Instant createdAt = tx.createdAt();
            if (createdAt == null || createdAt.isBefore(startOfMonth) || createdAt.isAfter(endOfMonth)) {
                continue;
            }

            long txAmountMinor = tx.amount() != null ? tx.amount().movePointRight(2).longValue() : 0L;
            boolean isSender = userId.equals(tx.senderId());
            boolean isReceiver = userId.equals(tx.receiverId());

            if (isConfirmedSuccess(tx.status())) {
                if (isSender) {
                    spentMinor += txAmountMinor;
                    var resolved = categoryClassificationService.resolveTransferCategory(userId, tx);
                    categoryMap.computeIfAbsent(resolved.category(), k -> new CategoryAccumulator(resolved.category(), resolved.colorHex()))
                            .add(txAmountMinor);

                    String payeeName = tx.receiverFullName() != null && !tx.receiverFullName().isBlank()
                            ? tx.receiverFullName()
                            : (tx.receiverPhone() != null ? maskPhone(tx.receiverPhone()) : "Recipient");
                    payeeMap.computeIfAbsent(payeeName, PayeeAccumulator::new).add(txAmountMinor);
                }
                if (isReceiver) {
                    receivedMinor += txAmountMinor;
                }
            } else if (isPending(tx.status()) && isSender) {
                pendingSpentMinor += txAmountMinor;
                pendingCount++;
            }
        }

        // Process bill payments in current month
        for (BillPaymentRecord bill : allBills) {
            Instant createdAt = bill.createdAt();
            if (createdAt == null || createdAt.isBefore(startOfMonth) || createdAt.isAfter(endOfMonth)) {
                continue;
            }

            long billAmountMinor = bill.totalAmount() != null
                    ? bill.totalAmount().movePointRight(2).longValue()
                    : (bill.amount() != null ? bill.amount().movePointRight(2).longValue() : 0L);

            if (isConfirmedSuccess(bill.status())) {
                spentMinor += billAmountMinor;
                var resolved = categoryClassificationService.resolveBillCategory(userId, bill);
                categoryMap.computeIfAbsent(resolved.category(), k -> new CategoryAccumulator(resolved.category(), resolved.colorHex()))
                        .add(billAmountMinor);

                String payeeName = bill.billerName() != null ? bill.billerName() : "Biller";
                payeeMap.computeIfAbsent(payeeName, PayeeAccumulator::new).add(billAmountMinor);
            } else if (isPending(bill.status())) {
                pendingSpentMinor += billAmountMinor;
                pendingCount++;
            }
        }

        // Previous month spent calculation for month-over-month
        long prevSpentMinor = 0L;
        for (TransactionRecord tx : allTransfers) {
            Instant createdAt = tx.createdAt();
            if (createdAt != null && !createdAt.isBefore(prevStart) && !createdAt.isAfter(prevEnd)) {
                if (userId.equals(tx.senderId()) && isConfirmedSuccess(tx.status())) {
                    prevSpentMinor += tx.amount() != null ? tx.amount().movePointRight(2).longValue() : 0L;
                }
            }
        }
        for (BillPaymentRecord bill : allBills) {
            Instant createdAt = bill.createdAt();
            if (createdAt != null && !createdAt.isBefore(prevStart) && !createdAt.isAfter(prevEnd)) {
                if (isConfirmedSuccess(bill.status())) {
                    prevSpentMinor += bill.totalAmount() != null
                            ? bill.totalAmount().movePointRight(2).longValue()
                            : (bill.amount() != null ? bill.amount().movePointRight(2).longValue() : 0L);
                }
            }
        }

        // Build category totals
        final long finalSpentMinor = spentMinor;
        List<CategoryTotalDto> byCategoryTotals = categoryMap.values().stream()
                .map(acc -> {
                    double pct = finalSpentMinor > 0 ? (acc.totalMinor * 100.0) / finalSpentMinor : 0.0;
                    return new CategoryTotalDto(
                            acc.category.name(),
                            acc.category.getDisplayName(),
                            acc.colorHex,
                            acc.totalMinor,
                            BigDecimal.valueOf(acc.totalMinor, 2),
                            acc.count,
                            roundTwoDecimals(pct)
                    );
                })
                .sorted(Comparator.comparing(CategoryTotalDto::totalMinor).reversed())
                .collect(Collectors.toList());

        // Build top payees
        List<TopPayeeDto> topPayees = payeeMap.values().stream()
                .sorted(Comparator.comparingLong(PayeeAccumulator::getTotalMinor).reversed())
                .limit(5)
                .map(p -> new TopPayeeDto(
                        p.name,
                        p.totalMinor,
                        BigDecimal.valueOf(p.totalMinor, 2),
                        p.count
                ))
                .collect(Collectors.toList());

        // Month-over-month
        long diff = spentMinor - prevSpentMinor;
        double momPct = prevSpentMinor > 0 ? ((diff * 100.0) / prevSpentMinor) : (spentMinor > 0 ? 100.0 : 0.0);
        MonthOverMonthDto mom = new MonthOverMonthDto(
                spentMinor,
                BigDecimal.valueOf(spentMinor, 2),
                prevSpentMinor,
                BigDecimal.valueOf(prevSpentMinor, 2),
                diff,
                BigDecimal.valueOf(diff, 2),
                roundTwoDecimals(momPct),
                diff > 0
        );

        // Budget evaluation
        Optional<Budget> budgetOpt = budgetStore.findByUserIdAndMonth(userId, month);
        Long budgetTotalMinor = null;
        BigDecimal budgetTotalAmount = null;
        Double budgetUsedPercentage = null;
        Long budgetRemainingMinor = null;
        BigDecimal budgetRemainingAmount = null;

        if (budgetOpt.isPresent()) {
            Budget budget = budgetOpt.get();
            budgetTotalMinor = budget.getMonthlyTotalMinor();
            budgetTotalAmount = budgetTotalMinor != null ? BigDecimal.valueOf(budgetTotalMinor, 2) : BigDecimal.ZERO;
            long bTotal = budgetTotalMinor != null ? budgetTotalMinor : 0L;
            if (bTotal > 0) {
                double usedPct = (spentMinor * 100.0) / bTotal;
                budgetUsedPercentage = roundTwoDecimals(usedPct);
                long remaining = Math.max(0L, bTotal - spentMinor);
                budgetRemainingMinor = remaining;
                budgetRemainingAmount = BigDecimal.valueOf(remaining, 2);

                checkAndTriggerBudgetNotifications(userId, budget, usedPct);
            }
        }

        return new AnalyticsSummaryResponse(
                month,
                spentMinor,
                BigDecimal.valueOf(spentMinor, 2),
                receivedMinor,
                BigDecimal.valueOf(receivedMinor, 2),
                pendingSpentMinor,
                BigDecimal.valueOf(pendingSpentMinor, 2),
                pendingCount,
                byCategoryTotals,
                topPayees,
                mom,
                budgetTotalMinor,
                budgetTotalAmount,
                budgetUsedPercentage,
                budgetRemainingMinor,
                budgetRemainingAmount
        );
    }

    /**
     * Set or replace monthly budget.
     */
    public BudgetResponse setBudget(Long userId, SetBudgetRequest request) {
        String month = normalizeMonth(request.month());
        Budget budget = budgetStore.findByUserIdAndMonth(userId, month)
                .orElseGet(() -> Budget.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .month(month)
                        .createdAt(Instant.now())
                        .build());

        budget.setMonthlyTotalMinor(request.monthlyTotalMinor());
        if (request.categoryBudgetsMinor() != null) {
            budget.setCategoryBudgetsMinor(new HashMap<>(request.categoryBudgetsMinor()));
        }
        budget.setUpdatedAt(Instant.now());

        long currentSpentMinor = calculateMonthSpent(userId, month);
        double usedPct = budget.getMonthlyTotalMinor() > 0 ? (currentSpentMinor * 100.0) / budget.getMonthlyTotalMinor() : 0.0;
        checkAndTriggerBudgetNotifications(userId, budget, usedPct);

        Budget saved = budgetStore.save(budget);
        return BudgetResponse.from(saved, currentSpentMinor);
    }

    /**
     * Get budget for a month.
     */
    public BudgetResponse getBudget(Long userId, String monthInput) {
        String month = normalizeMonth(monthInput);
        Budget budget = budgetStore.findByUserIdAndMonth(userId, month)
                .orElseThrow(() -> new ResourceNotFoundException("BUDGET_NOT_FOUND", "No budget found for month " + month));

        long currentSpentMinor = calculateMonthSpent(userId, month);
        double usedPct = budget.getMonthlyTotalMinor() > 0 ? (currentSpentMinor * 100.0) / budget.getMonthlyTotalMinor() : 0.0;
        checkAndTriggerBudgetNotifications(userId, budget, usedPct);

        return BudgetResponse.from(budget, currentSpentMinor);
    }

    /**
     * Partial update of existing budget.
     */
    public BudgetResponse patchBudget(Long userId, SetBudgetRequest request) {
        String month = normalizeMonth(request.month());
        Budget budget = budgetStore.findByUserIdAndMonth(userId, month)
                .orElseThrow(() -> new ResourceNotFoundException("BUDGET_NOT_FOUND", "No budget found for month " + month));

        if (request.monthlyTotalMinor() != null) {
            budget.setMonthlyTotalMinor(request.monthlyTotalMinor());
        }
        if (request.categoryBudgetsMinor() != null) {
            if (budget.getCategoryBudgetsMinor() == null) {
                budget.setCategoryBudgetsMinor(new HashMap<>());
            }
            budget.getCategoryBudgetsMinor().putAll(request.categoryBudgetsMinor());
        }
        budget.setUpdatedAt(Instant.now());

        long currentSpentMinor = calculateMonthSpent(userId, month);
        double usedPct = budget.getMonthlyTotalMinor() > 0 ? (currentSpentMinor * 100.0) / budget.getMonthlyTotalMinor() : 0.0;
        checkAndTriggerBudgetNotifications(userId, budget, usedPct);

        Budget saved = budgetStore.save(budget);
        return BudgetResponse.from(saved, currentSpentMinor);
    }

    /**
     * Update/recolour category for a specific transaction.
     */
    public UserTransactionCategory updateTransactionCategory(Long userId, UpdateCategoryRequest request) {
        TransactionCategory cat;
        try {
            cat = TransactionCategory.valueOf(request.category().toUpperCase());
        } catch (Exception e) {
            cat = TransactionCategory.OTHER;
        }

        UserTransactionCategory userCat = new UserTransactionCategory(
                userId,
                request.transactionId(),
                request.transactionType() != null ? request.transactionType().toUpperCase() : "TRANSFER",
                cat,
                request.colorHex() != null ? request.colorHex() : cat.getDefaultColorHex(),
                Instant.now()
        );

        return categoryStore.save(userCat);
    }

    /**
     * List user activity transactions with comprehensive filters.
     */
    public List<ActivityItemDto> listActivity(Long userId, StatementRequest filter) {
        List<TransactionRecord> transfers = getUserTransfers(userId);
        List<BillPaymentRecord> bills = getUserBills(userId);

        List<ActivityItemDto> items = new ArrayList<>();

        // Convert transfers
        for (TransactionRecord tx : transfers) {
            boolean isSender = userId.equals(tx.senderId());
            boolean isReceiver = userId.equals(tx.receiverId());
            if (!isSender && !isReceiver) continue;

            String direction = isSender ? "DEBIT" : "CREDIT";
            var resolvedCat = categoryClassificationService.resolveTransferCategory(userId, tx);

            long amountMinor = tx.amount() != null ? tx.amount().movePointRight(2).longValue() : 0L;
            BigDecimal amt = tx.amount() != null ? tx.amount() : BigDecimal.ZERO;

            String title = isSender
                    ? (tx.receiverFullName() != null ? tx.receiverFullName() : "Transfer to " + maskPhone(tx.receiverPhone()))
                    : (tx.senderFullName() != null ? tx.senderFullName() : "Transfer from " + maskPhone(tx.senderPhone()));

            items.add(new ActivityItemDto(
                    tx.id(),
                    "TRANSFER",
                    tx.reference(),
                    title,
                    tx.note() != null ? tx.note() : (isSender ? "Sent transfer" : "Received transfer"),
                    amountMinor,
                    amt,
                    tx.currency() != null ? tx.currency() : "LKR",
                    direction,
                    tx.status() != null ? tx.status().name() : TransactionStatus.PENDING.name(),
                    resolvedCat.category().name(),
                    resolvedCat.colorHex(),
                    tx.fundingType() != null ? tx.fundingType().name() : FundingType.ACCOUNT.name(),
                    tx.cardLast4(),
                    isSender ? tx.senderBankCode() : tx.receiverBankCode(),
                    tx.createdAt()
            ));
        }

        // Convert bills
        for (BillPaymentRecord bill : bills) {
            var resolvedCat = categoryClassificationService.resolveBillCategory(userId, bill);
            long amountMinor = bill.totalAmount() != null
                    ? bill.totalAmount().movePointRight(2).longValue()
                    : (bill.amount() != null ? bill.amount().movePointRight(2).longValue() : 0L);
            BigDecimal amt = bill.totalAmount() != null ? bill.totalAmount() : (bill.amount() != null ? bill.amount() : BigDecimal.ZERO);

            items.add(new ActivityItemDto(
                    bill.id(),
                    "BILL",
                    bill.paymentReference(),
                    bill.billerName() != null ? bill.billerName() : "Bill Payment",
                    bill.customerReferenceMasked() != null ? "Ref: " + bill.customerReferenceMasked() : "Bill Payment",
                    amountMinor,
                    amt,
                    bill.currency() != null ? bill.currency() : "LKR",
                    "DEBIT",
                    bill.status() != null ? bill.status().name() : TransactionStatus.PENDING.name(),
                    resolvedCat.category().name(),
                    resolvedCat.colorHex(),
                    bill.fundingType() != null ? bill.fundingType().name() : FundingType.ACCOUNT.name(),
                    null,
                    null,
                    bill.createdAt()
            ));
        }

        // Apply filters
        return items.stream()
                .filter(item -> filterDateRange(item, filter))
                .filter(item -> filterDirection(item, filter.direction()))
                .filter(item -> filterCategory(item, filter.category()))
                .filter(item -> filterFundingType(item, filter.fundingType()))
                .filter(item -> filterBankCode(item, filter.bankCode()))
                .filter(item -> filterAmount(item, filter.minAmount(), filter.maxAmount()))
                .sorted(Comparator.comparing(ActivityItemDto::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /**
     * Export statement:
     * - Rate-limited
     * - Server-side PDF with SIMULATED watermark and masked numbers
     * - Audited
     * - Short-lived token
     */
    public StatementResponse exportStatement(Long userId, StatementRequest request, String clientIp) {
        // 1. Rate limiting check
        statementRateLimiter.consume(userId);

        // 2. Filter items for statement
        List<ActivityItemDto> items = listActivity(userId, request);

        long totalSpentMinor = items.stream()
                .filter(i -> "DEBIT".equalsIgnoreCase(i.direction()) && "COMPLETED".equalsIgnoreCase(i.status()))
                .mapToLong(ActivityItemDto::amountMinor)
                .sum();

        long totalReceivedMinor = items.stream()
                .filter(i -> "CREDIT".equalsIgnoreCase(i.direction()) && "COMPLETED".equalsIgnoreCase(i.status()))
                .mapToLong(ActivityItemDto::amountMinor)
                .sum();

        // 3. User details (masked)
        UserAccount user = userStore.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found with id: " + userId));

        String customerName = user.fullName() != null ? user.fullName() : "Customer";
        String customerPhoneMasked = maskPhone(user.phone());
        String period = request.month() != null && !request.month().isBlank() ? request.month() : "Custom Period";

        // 4. Generate PDF
        byte[] pdfBytes = statementPdfGenerator.generateStatementPdf(
                customerName,
                customerPhoneMasked,
                period,
                totalSpentMinor,
                totalReceivedMinor,
                items
        );

        // 5. Audit export event
        String detailJson = String.format("{\"period\":\"%s\",\"itemsCount\":%d,\"spentMinor\":%d,\"receivedMinor\":%d}",
                period, items.size(), totalSpentMinor, totalReceivedMinor);
        auditService.log(userId, "STATEMENT_EXPORTED", "STATEMENT", null, detailJson, clientIp);

        // 6. Generate secure short-lived token (15 mins)
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(15));
        String filename = "lankapay-statement-" + period + ".pdf";

        StatementExportAudit audit = new StatementExportAudit(
                token,
                userId,
                filename,
                period,
                pdfBytes,
                Instant.now(),
                expiresAt,
                clientIp
        );
        statementStore.save(audit);

        String downloadUrl = "/api/v1/statements/download/" + token;
        return new StatementResponse(token, downloadUrl, filename, period, expiresAt);
    }

    /**
     * Download statement PDF with strict object-level access verification.
     */
    public StatementExportAudit downloadStatement(String token, Long authenticatedUserId) {
        StatementExportAudit audit = statementStore.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("STATEMENT_NOT_FOUND", "Statement download token is invalid or expired."));

        if (!audit.userId().equals(authenticatedUserId)) {
            log.warn("IDOR attempt: userId={} attempted to download statement belonging to userId={}", authenticatedUserId, audit.userId());
            throw new AuthorizationException("FORBIDDEN", "You do not have permission to download this statement.");
        }

        return audit;
    }

    // ── Helper methods ─────────────────────────────────────────

    private void checkAndTriggerBudgetNotifications(Long userId, Budget budget, double usedPct) {
        if (usedPct >= 100.0 && !budget.isNotified100()) {
            userStore.findUserById(userId).ifPresent(u -> {
                try {
                    notificationService.notifyBudgetThreshold(u.toUser(), 100);
                } catch (Exception e) {
                    log.error("Failed to deliver 100% budget notification", e);
                }
            });
            budget.setNotified100(true);
            budget.setNotified80(true);
            budgetStore.save(budget);
        } else if (usedPct >= 80.0 && !budget.isNotified80()) {
            userStore.findUserById(userId).ifPresent(u -> {
                try {
                    notificationService.notifyBudgetThreshold(u.toUser(), 80);
                } catch (Exception e) {
                    log.error("Failed to deliver 80% budget notification", e);
                }
            });
            budget.setNotified80(true);
            budgetStore.save(budget);
        }
    }

    private long calculateMonthSpent(Long userId, String month) {
        YearMonth ym = YearMonth.parse(month);
        Instant startOfMonth = ym.atDay(1).atStartOfDay(SRI_LANKA_ZONE).toInstant();
        Instant endOfMonth = ym.atEndOfMonth().atTime(23, 59, 59, 999_999_999).atZone(SRI_LANKA_ZONE).toInstant();

        long spent = 0L;
        for (TransactionRecord tx : getUserTransfers(userId)) {
            if (tx.createdAt() != null && !tx.createdAt().isBefore(startOfMonth) && !tx.createdAt().isAfter(endOfMonth)) {
                if (userId.equals(tx.senderId()) && isConfirmedSuccess(tx.status())) {
                    spent += tx.amount() != null ? tx.amount().movePointRight(2).longValue() : 0L;
                }
            }
        }
        for (BillPaymentRecord bill : getUserBills(userId)) {
            if (bill.createdAt() != null && !bill.createdAt().isBefore(startOfMonth) && !bill.createdAt().isAfter(endOfMonth)) {
                if (isConfirmedSuccess(bill.status())) {
                    spent += bill.totalAmount() != null
                            ? bill.totalAmount().movePointRight(2).longValue()
                            : (bill.amount() != null ? bill.amount().movePointRight(2).longValue() : 0L);
                }
            }
        }
        return spent;
    }

    private List<TransactionRecord> getUserTransfers(Long userId) {
        try {
            Page<TransactionRecord> page = transactionStore.findTransfersByUserId(userId, PageRequest.of(0, 2000, Sort.by(Sort.Direction.DESC, "createdAt")));
            return page.getContent();
        } catch (Exception e) {
            log.warn("Failed to fetch transfers by userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<BillPaymentRecord> getUserBills(Long userId) {
        try {
            return billPaymentStore.findByUserId(userId, 0, 2000);
        } catch (Exception e) {
            log.warn("Failed to fetch bills by userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean isConfirmedSuccess(TransactionStatus status) {
        return status == TransactionStatus.COMPLETED;
    }

    private boolean isPending(TransactionStatus status) {
        return status == TransactionStatus.PENDING ||
                status == TransactionStatus.PROCESSING ||
                status == TransactionStatus.CHALLENGE_REQUIRED ||
                status == TransactionStatus.QUOTED ||
                status == TransactionStatus.REQUIRES_RECONCILIATION ||
                status == TransactionStatus.REVERSAL_PENDING;
    }

    private String normalizeMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(SRI_LANKA_ZONE).toString();
        }
        return month.trim();
    }

    private double roundTwoDecimals(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        int len = phone.length();
        return phone.substring(0, Math.min(6, len)) + "***" + phone.substring(Math.max(0, len - 4));
    }

    private boolean filterDateRange(ActivityItemDto item, StatementRequest filter) {
        if (item.createdAt() == null) return true;
        if (filter.month() != null && !filter.month().isBlank()) {
            try {
                YearMonth ym = YearMonth.parse(filter.month());
                Instant start = ym.atDay(1).atStartOfDay(SRI_LANKA_ZONE).toInstant();
                Instant end = ym.atEndOfMonth().atTime(23, 59, 59, 999_999_999).atZone(SRI_LANKA_ZONE).toInstant();
                return !item.createdAt().isBefore(start) && !item.createdAt().isAfter(end);
            } catch (Exception ignored) {}
        }
        if (filter.fromDate() != null && item.createdAt().isBefore(filter.fromDate())) {
            return false;
        }
        if (filter.toDate() != null && item.createdAt().isAfter(filter.toDate())) {
            return false;
        }
        return true;
    }

    private boolean filterDirection(ActivityItemDto item, String dir) {
        if (dir == null || dir.isBlank() || "ALL".equalsIgnoreCase(dir)) return true;
        return dir.equalsIgnoreCase(item.direction());
    }

    private boolean filterCategory(ActivityItemDto item, String category) {
        if (category == null || category.isBlank() || "ALL".equalsIgnoreCase(category)) return true;
        return category.equalsIgnoreCase(item.category());
    }

    private boolean filterFundingType(ActivityItemDto item, String fundingType) {
        if (fundingType == null || fundingType.isBlank() || "ALL".equalsIgnoreCase(fundingType)) return true;
        return fundingType.equalsIgnoreCase(item.fundingType());
    }

    private boolean filterBankCode(ActivityItemDto item, String bankCode) {
        if (bankCode == null || bankCode.isBlank()) return true;
        return bankCode.equalsIgnoreCase(item.bankCode());
    }

    private boolean filterAmount(ActivityItemDto item, BigDecimal min, BigDecimal max) {
        if (min != null && item.amount().compareTo(min) < 0) return false;
        if (max != null && item.amount().compareTo(max) > 0) return false;
        return true;
    }

    private static class CategoryAccumulator {
        final TransactionCategory category;
        final String colorHex;
        long totalMinor = 0L;
        int count = 0;

        CategoryAccumulator(TransactionCategory category, String colorHex) {
            this.category = category;
            this.colorHex = colorHex;
        }

        void add(long minor) {
            totalMinor += minor;
            count++;
        }
    }

    private static class PayeeAccumulator {
        final String name;
        long totalMinor = 0L;
        int count = 0;

        PayeeAccumulator(String name) {
            this.name = name;
        }

        void add(long minor) {
            totalMinor += minor;
            count++;
        }

        long getTotalMinor() {
            return totalMinor;
        }
    }
}

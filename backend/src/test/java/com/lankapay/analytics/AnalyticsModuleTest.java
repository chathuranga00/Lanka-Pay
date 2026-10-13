package com.lankapay.analytics;

import com.lankapay.analytics.domain.Budget;
import com.lankapay.analytics.domain.StatementExportAudit;
import com.lankapay.analytics.domain.TransactionCategory;
import com.lankapay.analytics.domain.UserTransactionCategory;
import com.lankapay.analytics.service.*;
import com.lankapay.analytics.store.InMemoryBudgetStore;
import com.lankapay.analytics.store.InMemoryStatementStore;
import com.lankapay.analytics.store.InMemoryUserTransactionCategoryStore;
import com.lankapay.analytics.web.dto.*;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bill.adapter.BillerCategory;
import com.lankapay.bill.domain.BillPaymentRecord;
import com.lankapay.bill.domain.BillPaymentStore;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.RateLimitException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.service.NotificationMessageFormatter;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransactionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnalyticsModuleTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    private InMemoryUserTransactionCategoryStore categoryStore;
    private InMemoryBudgetStore budgetStore;
    private InMemoryStatementStore statementStore;
    private CategoryClassificationService classificationService;
    private StatementPdfGenerator pdfGenerator;
    private StatementRateLimiter rateLimiter;

    @Mock private TransactionStore transactionStore;
    @Mock private BillPaymentStore billPaymentStore;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    @Mock private UserStore userStore;

    private AnalyticsService analyticsService;

    private final Long testUserId = 1001L;
    private final String currentMonth = "2026-10";
    private User testUser;
    private UserAccount testUserAccount;

    @BeforeEach
    void setUp() {
        categoryStore = new InMemoryUserTransactionCategoryStore();
        budgetStore = new InMemoryBudgetStore();
        statementStore = new InMemoryStatementStore();
        classificationService = new CategoryClassificationService(categoryStore);
        pdfGenerator = new StatementPdfGenerator();
        rateLimiter = new StatementRateLimiter(5); // 5 statements per 10m

        analyticsService = new AnalyticsService(
                transactionStore,
                billPaymentStore,
                classificationService,
                categoryStore,
                budgetStore,
                statementStore,
                pdfGenerator,
                rateLimiter,
                auditService,
                notificationService,
                userStore
        );

        testUserAccount = new UserAccount(
                testUserId,
                "+94771234567",
                "Kasun Perera",
                "199012345678",
                "hash",
                UserStatus.ACTIVE,
                UserRole.CUSTOMER,
                0,
                null,
                Instant.now(),
                Instant.now()
        );
        testUser = testUserAccount.toUser();

        lenient().when(userStore.findUserById(testUserId)).thenReturn(Optional.of(testUserAccount));
    }

    @Test
    @DisplayName("Category totals calculate correctly and ONLY confirmed SUCCESS transactions count towards spent and received")
    void testCategoryTotalsAndOnlySuccessCounted() {
        YearMonth ym = YearMonth.parse(currentMonth);
        Instant txTime = ym.atDay(10).atTime(12, 0).atZone(ZONE).toInstant();

        // 1. Success transfer: Food note (1500.00 LKR = 150000 minor)
        TransactionRecord foodTx = createTransfer(1L, testUserId, 2000L, new BigDecimal("1500.00"),
                TransactionStatus.COMPLETED, "Lunch at cafe", txTime);

        // 2. Success transfer: Uber ride (800.00 LKR = 80000 minor)
        TransactionRecord transportTx = createTransfer(2L, testUserId, 2001L, new BigDecimal("800.00"),
                TransactionStatus.COMPLETED, "Uber ride to office", txTime);

        // 3. Success transfer received: (5000.00 LKR = 500000 minor)
        TransactionRecord receivedTx = createTransfer(3L, 2002L, testUserId, new BigDecimal("5000.00"),
                TransactionStatus.COMPLETED, "Salary advance", txTime);

        // 4. Pending transfer: (2000.00 LKR = 200000 minor) - MUST NOT count in spent
        TransactionRecord pendingTx = createTransfer(4L, testUserId, 2003L, new BigDecimal("2000.00"),
                TransactionStatus.PENDING, "Dinner pending", txTime);

        // 5. Failed transfer: (3500.00 LKR) - MUST NOT count in spent
        TransactionRecord failedTx = createTransfer(5L, testUserId, 2004L, new BigDecimal("3500.00"),
                TransactionStatus.FAILED, "Failed transfer", txTime);

        when(transactionStore.findTransfersByUserId(eq(testUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(foodTx, transportTx, receivedTx, pendingTx, failedTx)));

        // 6. Success Bill payment: CEB Utility (3200.00 LKR = 320000 minor)
        BillPaymentRecord billTx = createBillPayment(101L, testUserId, "CEB", new BigDecimal("3200.00"),
                TransactionStatus.COMPLETED, txTime);

        when(billPaymentStore.findByUserId(eq(testUserId), anyInt(), anyInt()))
                .thenReturn(List.of(billTx));

        // Execute summary query
        AnalyticsSummaryResponse summary = analyticsService.getSummary(testUserId, currentMonth);

        // Assertions:
        // Total spent = 150000 + 80000 + 320000 = 550000 minor (5500.00 LKR)
        assertThat(summary.spentMinor()).isEqualTo(550000L);
        assertThat(summary.spentAmount()).isEqualByComparingTo(new BigDecimal("5500.00"));

        // Total received = 500000 minor
        assertThat(summary.receivedMinor()).isEqualTo(500000L);
        assertThat(summary.receivedAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));

        // Pending transactions isolated
        assertThat(summary.pendingSpentMinor()).isEqualTo(200000L);
        assertThat(summary.pendingCount()).isEqualTo(1);

        // Category breakdown
        assertThat(summary.byCategoryTotals()).hasSize(3);
        Map<String, Long> catTotals = new HashMap<>();
        summary.byCategoryTotals().forEach(c -> catTotals.put(c.category(), c.totalMinor()));

        assertThat(catTotals.get("FOOD")).isEqualTo(150000L);
        assertThat(catTotals.get("TRANSPORT")).isEqualTo(80000L);
        assertThat(catTotals.get("BILLS")).isEqualTo(320000L);

        // Verify top payees (sorted by spent)
        assertThat(summary.topPayees()).isNotEmpty();
        assertThat(summary.topPayees().get(0).name()).isEqualTo("CEB");
    }

    @Test
    @DisplayName("User can recategorise or recolour a transaction and it reflects in summary")
    void testUserRecategorization() {
        YearMonth ym = YearMonth.parse(currentMonth);
        Instant txTime = ym.atDay(10).atTime(12, 0).atZone(ZONE).toInstant();

        // Default transfer without food keywords defaults to TRANSFERS
        TransactionRecord tx = createTransfer(10L, testUserId, 2000L, new BigDecimal("1200.00"),
                TransactionStatus.COMPLETED, "Payment for checkup", txTime);

        when(transactionStore.findTransfersByUserId(eq(testUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));
        when(billPaymentStore.findByUserId(eq(testUserId), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        // Recategorise to HEALTH with custom color
        UpdateCategoryRequest req = new UpdateCategoryRequest("TRANSFER", 10L, "HEALTH", "#FF0077");
        analyticsService.updateTransactionCategory(testUserId, req);

        AnalyticsSummaryResponse summary = analyticsService.getSummary(testUserId, currentMonth);

        assertThat(summary.byCategoryTotals()).hasSize(1);
        CategoryTotalDto cat = summary.byCategoryTotals().get(0);
        assertThat(cat.category()).isEqualTo("HEALTH");
        assertThat(cat.colorHex()).isEqualTo("#FF0077");
        assertThat(cat.totalMinor()).isEqualTo(120000L);
    }

    @Test
    @DisplayName("Budget triggers privacy-safe notifications at 80% and 100% thresholds")
    void testBudgetThresholdNotifications() {
        // Set monthly budget of 100,000 minor (1,000 LKR)
        SetBudgetRequest setReq = new SetBudgetRequest(currentMonth, 100000L, Map.of("FOOD", 50000L));
        when(transactionStore.findTransfersByUserId(eq(testUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(billPaymentStore.findByUserId(eq(testUserId), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        BudgetResponse budgetResp = analyticsService.setBudget(testUserId, setReq);
        assertThat(budgetResp.monthlyTotalMinor()).isEqualTo(100000L);
        assertThat(budgetResp.usedPercentage()).isEqualTo(0.0);

        // Case 1: Spent 75% -> No notifications triggered
        YearMonth ym = YearMonth.parse(currentMonth);
        Instant txTime = ym.atDay(5).atTime(10, 0).atZone(ZONE).toInstant();
        TransactionRecord tx75 = createTransfer(21L, testUserId, 2000L, new BigDecimal("750.00"),
                TransactionStatus.COMPLETED, "Lunch", txTime);

        when(transactionStore.findTransfersByUserId(eq(testUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx75)));

        analyticsService.getSummary(testUserId, currentMonth);
        verify(notificationService, never()).notifyBudgetThreshold(any(), anyInt());

        // Case 2: Spent reaches 85% (850 LKR = 85000 minor) -> 80% notification triggered
        TransactionRecord tx85 = createTransfer(22L, testUserId, 2000L, new BigDecimal("850.00"),
                TransactionStatus.COMPLETED, "Dinner", txTime);

        when(transactionStore.findTransfersByUserId(eq(testUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx85)));

        analyticsService.getSummary(testUserId, currentMonth);
        verify(notificationService, times(1)).notifyBudgetThreshold(any(), eq(80));

        // Verify notification formatter privacy safety
        NotificationMessageFormatter formatter = new NotificationMessageFormatter();
        var msg80 = formatter.formatBudgetThreshold(80);
        NotificationMessageFormatter.assertPrivacySafe(msg80.message());

        // Case 3: Re-checking at 85% does NOT spam duplicate 80% notification
        analyticsService.getSummary(testUserId, currentMonth);
        verify(notificationService, times(1)).notifyBudgetThreshold(any(), eq(80));

        // Case 4: Spent reaches 105% -> 100% notification triggered
        TransactionRecord tx105 = createTransfer(23L, testUserId, 2000L, new BigDecimal("1050.00"),
                TransactionStatus.COMPLETED, "Grocery", txTime);

        when(transactionStore.findTransfersByUserId(eq(testUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx105)));

        analyticsService.getSummary(testUserId, currentMonth);
        verify(notificationService, times(1)).notifyBudgetThreshold(any(), eq(100));

        var msg100 = formatter.formatBudgetThreshold(100);
        NotificationMessageFormatter.assertPrivacySafe(msg100.message());
    }

    @Test
    @DisplayName("Statement export masks sensitive data, includes SIMULATED watermark, and audits")
    void testStatementMaskingAndWatermark() {
        YearMonth ym = YearMonth.parse(currentMonth);
        Instant txTime = ym.atDay(15).atTime(14, 0).atZone(ZONE).toInstant();

        TransactionRecord tx = createTransfer(31L, testUserId, 2000L, new BigDecimal("4500.00"),
                TransactionStatus.COMPLETED, "Shopping payment", txTime);

        when(transactionStore.findTransfersByUserId(eq(testUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));
        when(billPaymentStore.findByUserId(eq(testUserId), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        StatementRequest req = new StatementRequest(currentMonth, null, null, "ALL", null, null, null, null, null);
        StatementResponse resp = analyticsService.exportStatement(testUserId, req, "192.168.1.50");

        assertThat(resp.token()).isNotBlank();
        assertThat(resp.downloadUrl()).contains(resp.token());
        assertThat(resp.filename()).endsWith(".pdf");

        // Verify export was audited
        verify(auditService, times(1)).log(
                eq(testUserId),
                eq("STATEMENT_EXPORTED"),
                eq("STATEMENT"),
                isNull(),
                anyString(),
                eq("192.168.1.50")
        );

        // Download statement
        StatementExportAudit audit = analyticsService.downloadStatement(resp.token(), testUserId);
        assertThat(audit.pdfBytes()).isNotEmpty();

        String pdfContent = new String(audit.pdfBytes(), StandardCharsets.US_ASCII);

        // Compliant PDF header
        assertThat(pdfContent).startsWith("%PDF-1.4");

        // Mandatory SIMULATED watermark
        assertThat(pdfContent).contains("SIMULATED");

        // Masked phone in statement (e.g. +94771***4567 or similar masked format)
        assertThat(pdfContent).contains("***");
        // Must NEVER contain unmasked phone number
        assertThat(pdfContent).doesNotContain("+94771234567");
    }

    @Test
    @DisplayName("Statement download enforces strict object-level access (IDOR protection)")
    void testStatementObjectLevelAccess() {
        when(transactionStore.findTransfersByUserId(eq(testUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(billPaymentStore.findByUserId(eq(testUserId), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        StatementRequest req = new StatementRequest(currentMonth, null, null, "ALL", null, null, null, null, null);
        StatementResponse resp = analyticsService.exportStatement(testUserId, req, "10.0.0.1");

        Long unauthorizedUserId = 9999L;

        // Attacker attempting to download victim's statement
        assertThatThrownBy(() -> analyticsService.downloadStatement(resp.token(), unauthorizedUserId))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("permission");
    }

    @Test
    @DisplayName("Statement exports are rate-limited to prevent abuse")
    void testStatementRateLimit() {
        when(transactionStore.findTransfersByUserId(eq(testUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(billPaymentStore.findByUserId(eq(testUserId), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        StatementRequest req = new StatementRequest(currentMonth, null, null, "ALL", null, null, null, null, null);

        // Capacity is 5: first 5 exports succeed
        for (int i = 0; i < 5; i++) {
            StatementResponse resp = analyticsService.exportStatement(testUserId, req, "127.0.0.1");
            assertThat(resp.token()).isNotNull();
        }

        // 6th export must throw RateLimitException
        assertThatThrownBy(() -> analyticsService.exportStatement(testUserId, req, "127.0.0.1"))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("rate limit exceeded");
    }

    // ── Helper builders ─────────────────────────────────────────

    private TransactionRecord createTransfer(Long id, Long senderId, Long receiverId, BigDecimal amount,
                                             TransactionStatus status, String note, Instant createdAt) {
        return new TransactionRecord(
                id,
                "REF-" + id,
                "IDEM-" + id,
                senderId,
                "+94771234567",
                "Kasun Perera",
                receiverId,
                "+94779876543",
                "Nimal Silva",
                "7010",
                "7083",
                amount,
                "LKR",
                status,
                null,
                "BANK-REF-" + id,
                "PROV-REF-" + id,
                note,
                false,
                null,
                null,
                1L,
                createdAt,
                createdAt,
                FundingType.ACCOUNT,
                null,
                null,
                null,
                BigDecimal.ZERO,
                amount,
                null,
                null
        );
    }

    private BillPaymentRecord createBillPayment(Long id, Long userId, String billerName, BigDecimal amount,
                                                TransactionStatus status, Instant createdAt) {
        return new BillPaymentRecord(
                id,
                "BILL-REF-" + id,
                "IDEM-BILL-" + id,
                userId,
                "BILLER-" + billerName,
                billerName,
                BillerCategory.UTILITIES,
                "enc",
                "••••5678",
                "Kasun Perera",
                amount,
                BigDecimal.ZERO,
                amount,
                "LKR",
                status,
                null,
                "CONF-" + id,
                FundingType.ACCOUNT,
                null,
                "Savings Account",
                null,
                null,
                createdAt,
                createdAt
        );
    }
}

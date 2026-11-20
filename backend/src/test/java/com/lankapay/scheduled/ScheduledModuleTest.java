package com.lankapay.scheduled;

import com.lankapay.admin.service.RiskManagementService;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.bill.adapter.BillerAdapterRegistry;
import com.lankapay.bill.adapter.SimulatedBillerAdapter;
import com.lankapay.bill.service.BillService;
import com.lankapay.bill.web.dto.BillPaymentReceiptResponse;
import com.lankapay.bill.web.dto.PayBillRequest;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardKind;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.common.exception.*;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.domain.NotificationType;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.scheduled.domain.*;
import com.lankapay.scheduled.service.ScheduledPaymentService;
import com.lankapay.scheduled.store.InMemoryScheduledPaymentStore;
import com.lankapay.scheduled.store.InMemoryScheduledRunStore;
import com.lankapay.scheduled.web.dto.CreateScheduleRequest;
import com.lankapay.scheduled.web.dto.UpdateScheduleRequest;
import com.lankapay.transfer.domain.FundingSource;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.service.CardDailyLimitService;
import com.lankapay.transfer.service.DailyLimitService;
import com.lankapay.transfer.service.TransferService;
import com.lankapay.transfer.web.dto.InitiateTransferRequest;
import com.lankapay.transfer.web.dto.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Scheduled Payments Module Tests")
class ScheduledModuleTest {

    private InMemoryScheduledPaymentStore scheduleStore;
    private InMemoryScheduledRunStore runStore;
    private PasswordEncoder passwordEncoder;
    private ScheduledPaymentService scheduledPaymentService;
    private SimulatedBillerAdapter simulatedBillerAdapter;
    private BillerAdapterRegistry billerAdapterRegistry;

    @Mock
    private UserStore userStore;
    @Mock
    private LinkedAccountStore linkedAccountStore;
    @Mock
    private CardStore cardStore;
    @Mock
    private DailyLimitService dailyLimitService;
    @Mock
    private CardDailyLimitService cardDailyLimitService;
    @Mock
    private RiskManagementService riskManagementService;
    @Mock
    private TransferService transferService;
    @Mock
    private BillService billService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;

    private User userA;
    private User userB;
    private User payeeUser;
    private UserAccount userAccountA;
    private UserAccount payeeAccount;
    private final String validPin = "123456";

    @BeforeEach
    void setUp() {
        scheduleStore = new InMemoryScheduledPaymentStore();
        runStore = new InMemoryScheduledRunStore();
        passwordEncoder = new BCryptPasswordEncoder();

        simulatedBillerAdapter = new SimulatedBillerAdapter();
        simulatedBillerAdapter.reset();
        billerAdapterRegistry = new BillerAdapterRegistry(List.of(simulatedBillerAdapter));

        scheduledPaymentService = new ScheduledPaymentService(
                scheduleStore,
                runStore,
                userStore,
                linkedAccountStore,
                cardStore,
                dailyLimitService,
                cardDailyLimitService,
                riskManagementService,
                transferService,
                billService,
                billerAdapterRegistry,
                passwordEncoder,
                notificationService,
                auditService
        );

        String hash = passwordEncoder.encode(validPin);
        userA = new User("+94771111111", "Alice Perera", null, hash);
        userA.setId(101L);
        userA.setPasswordHash(hash);

        userB = new User("+94772222222", "Bob Silva", null, hash);
        userB.setId(102L);
        userB.setPasswordHash(hash);

        payeeUser = new User("+94773333333", "Charlie Recipient", null, hash);
        payeeUser.setId(103L);
        payeeUser.setPasswordHash(hash);

        userAccountA = new UserAccount(101L, "+94771111111", "Alice Perera", "901234567V", hash, UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now());
        payeeAccount = new UserAccount(103L, "+94773333333", "Charlie Recipient", "901234568V", hash, UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now());

        LinkedAccount activeAcc = new LinkedAccount(
                1L, 101L, "BOC", "hash", "•••• 1234", "Alice Checking", "Main",
                true, true, "ref", BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now()
        );
        lenient().when(linkedAccountStore.findAccountByIdAndUserId(1L, 101L)).thenReturn(Optional.of(activeAcc));

        lenient().when(userStore.findUserById(101L)).thenReturn(Optional.of(userAccountA));
        lenient().when(userStore.findUserById(103L)).thenReturn(Optional.of(payeeAccount));
        lenient().when(userStore.findUserByPhone("+94773333333")).thenReturn(Optional.of(payeeAccount));
    }

    @Test
    @DisplayName("Create scheduled transfer successfully with step-up authentication and consent")
    void testCreateScheduledPayment_Success_WithStepUpAuthAndConsent() {
        CreateScheduleRequest req = new CreateScheduleRequest(
                ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", "Monthly allowance"),
                500000L, // 5,000.00 LKR
                "LKR",
                false,
                FundingSource.account(1L),
                ScheduleFrequency.MONTHLY,
                LocalDate.now(),
                LocalDate.now().plusMonths(6),
                true,
                validPin
        );

        ScheduledPayment created = scheduledPaymentService.createSchedule(userA, req, null);

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.userId()).isEqualTo(101L);
        assertThat(created.status()).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(created.consecutiveFailures()).isEqualTo(0);
        assertThat(created.version()).isEqualTo(1L);
        assertThat(created.amountMinor()).isEqualTo(500000L);
        assertThat(created.frequency()).isEqualTo(ScheduleFrequency.MONTHLY);
        verify(dailyLimitService).checkLimit(eq(userA), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Create scheduled payment fails without step-up auth or with invalid PIN")
    void testCreateScheduledPayment_RequiresStepUpAuth() {
        CreateScheduleRequest reqMissingPin = new CreateScheduleRequest(
                ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", null),
                100000L, "LKR", false, FundingSource.account(1L),
                ScheduleFrequency.WEEKLY, LocalDate.now(), null, true, null
        );

        assertThatThrownBy(() -> scheduledPaymentService.createSchedule(userA, reqMissingPin, null))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Step-up authentication required");

        CreateScheduleRequest reqWrongPin = new CreateScheduleRequest(
                ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", null),
                100000L, "LKR", false, FundingSource.account(1L),
                ScheduleFrequency.WEEKLY, LocalDate.now(), null, true, "999999"
        );

        assertThatThrownBy(() -> scheduledPaymentService.createSchedule(userA, reqWrongPin, null))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid PIN");
    }

    @Test
    @DisplayName("Create scheduled payment fails without consent")
    void testCreateScheduledPayment_RequiresConsent() {
        CreateScheduleRequest reqNoConsent = new CreateScheduleRequest(
                ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", null),
                100000L, "LKR", false, FundingSource.account(1L),
                ScheduleFrequency.WEEKLY, LocalDate.now(), null, false, validPin
        );

        assertThatThrownBy(() -> scheduledPaymentService.createSchedule(userA, reqNoConsent, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("User consent is required");
    }

    @Test
    @DisplayName("Double run protection: Same run date skips and prevents double-charging")
    void testDoubleRunProtection_SameDayDoesNotDoubleCharge() {
        CreateScheduleRequest req = new CreateScheduleRequest(
                ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", null),
                250000L, "LKR", false, FundingSource.account(1L),
                ScheduleFrequency.MONTHLY, LocalDate.now(), null, true, validPin
        );
        ScheduledPayment schedule = scheduledPaymentService.createSchedule(userA, req, null);

        TransferResponse mockResp = new TransferResponse(
                1L, "TX-12345", com.lankapay.transfer.domain.TransactionStatus.COMPLETED,
                new BigDecimal("2500.00"), "LKR", 101L, "+94771111111", 103L, "Charlie",
                "SIM-1", "PROV-1", "note", null, true, Instant.now(), Instant.now()
        );
        when(transferService.initiateScheduledTransfer(any(), any(), anyString(), anyString()))
                .thenReturn(mockResp);

        Instant runTime = Instant.now();

        // 1st scheduler run: should execute
        List<ScheduledRunRecord> firstBatch = scheduledPaymentService.runDueSchedules(runTime);
        assertThat(firstBatch).hasSize(1);
        assertThat(firstBatch.get(0).status()).isEqualTo(ScheduledRunStatus.SUCCESS);
        assertThat(firstBatch.get(0).transactionRef()).isEqualTo("TX-12345");

        // 2nd scheduler run immediately on the same due items / time:
        List<ScheduledRunRecord> secondBatch = scheduledPaymentService.runDueSchedules(runTime);
        assertThat(secondBatch).isEmpty();

        // Verify transfer service was invoked ONLY ONCE!
        verify(transferService, times(1)).initiateScheduledTransfer(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Concurrency: Simultaneous executions acquire lock and prevent double-execution")
    void testConcurrency_MultipleThreadsCannotDoubleRun() throws Exception {
        CreateScheduleRequest req = new CreateScheduleRequest(
                ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", null),
                100000L, "LKR", false, FundingSource.account(1L),
                ScheduleFrequency.WEEKLY, LocalDate.now(), null, true, validPin
        );
        ScheduledPayment schedule = scheduledPaymentService.createSchedule(userA, req, null);

        TransferResponse mockResp = new TransferResponse(
                2L, "TX-CONC-1", com.lankapay.transfer.domain.TransactionStatus.COMPLETED,
                new BigDecimal("1000.00"), "LKR", 101L, "+94771111111", 103L, "Charlie",
                "SIM-2", "PROV-2", "note", null, true, Instant.now(), Instant.now()
        );
        when(transferService.initiateScheduledTransfer(any(), any(), anyString(), anyString()))
                .thenReturn(mockResp);

        Instant runTime = Instant.now();
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<List<ScheduledRunRecord>>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return scheduledPaymentService.runDueSchedules(runTime);
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        int totalExecuted = 0;
        for (var f : futures) {
            totalExecuted += f.get().size();
        }

        assertThat(totalExecuted).isEqualTo(1);
        verify(transferService, times(1)).initiateScheduledTransfer(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Failure handling & Auto-pause: Automatically pauses after 2 consecutive failures")
    void testFailureAndAutoPause_AfterTwoConsecutiveFailures() {
        CreateScheduleRequest req = new CreateScheduleRequest(
                ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", null),
                100000L, "LKR", false, FundingSource.account(1L),
                ScheduleFrequency.WEEKLY, LocalDate.now(), null, true, validPin
        );
        ScheduledPayment schedule = scheduledPaymentService.createSchedule(userA, req, null);

        when(transferService.initiateScheduledTransfer(any(), any(), anyString(), anyString()))
                .thenThrow(new LankaPayException("BANK_TIMEOUT", "Core banking connection timed out"));

        Instant runTime1 = Instant.now();

        // Run 1: Failure #1
        List<ScheduledRunRecord> runs1 = scheduledPaymentService.runDueSchedules(runTime1);
        assertThat(runs1).hasSize(1);
        assertThat(runs1.get(0).status()).isEqualTo(ScheduledRunStatus.FAILED);

        ScheduledPayment afterFail1 = scheduleStore.findById(schedule.id()).orElseThrow();
        assertThat(afterFail1.consecutiveFailures()).isEqualTo(1);
        assertThat(afterFail1.status()).isEqualTo(ScheduleStatus.ACTIVE);
        verify(notificationService).dispatch(eq(userA), eq(NotificationType.PAYMENT_FAILED), contains("Failed"), anyString());

        // Advance to next scheduled run date
        Instant runTime2 = runTime1.plus(7, ChronoUnit.DAYS);
        // Set nextRunAt so it becomes due
        scheduleStore.save(afterFail1.withNextRun(runTime2, ScheduleStatus.ACTIVE, 1, runTime1));

        // Run 2: Failure #2 -> Auto-pause!
        List<ScheduledRunRecord> runs2 = scheduledPaymentService.runDueSchedules(runTime2);
        assertThat(runs2).hasSize(1);
        assertThat(runs2.get(0).status()).isEqualTo(ScheduledRunStatus.FAILED);

        ScheduledPayment afterFail2 = scheduleStore.findById(schedule.id()).orElseThrow();
        assertThat(afterFail2.consecutiveFailures()).isEqualTo(2);
        assertThat(afterFail2.status()).isEqualTo(ScheduleStatus.PAUSED); // Auto-paused!

        verify(notificationService).dispatch(eq(userA), eq(NotificationType.PAYMENT_FAILED), contains("Paused"), anyString());
    }

    @Test
    @DisplayName("Limits at run time: Rejects run and records failure when daily limit is exceeded")
    void testLimitsAtRunTime() {
        CreateScheduleRequest req = new CreateScheduleRequest(
                ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", null),
                100000L, "LKR", false, FundingSource.account(1L),
                ScheduleFrequency.WEEKLY, LocalDate.now(), null, true, validPin
        );
        ScheduledPayment schedule = scheduledPaymentService.createSchedule(userA, req, null);

        // At run time, daily limit fails
        doThrow(new UnprocessableEntityException("DAILY_LIMIT_EXCEEDED", "Daily limit exceeded"))
                .when(dailyLimitService).checkLimit(any(User.class), any(BigDecimal.class));

        List<ScheduledRunRecord> runs = scheduledPaymentService.runDueSchedules(Instant.now());
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).status()).isEqualTo(ScheduledRunStatus.FAILED);
        assertThat(runs.get(0).failureReason()).contains("Daily limit exceeded");

        verify(transferService, never()).initiateScheduledTransfer(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Revoked account or frozen card at run time: Fails run safely")
    void testRevokedAccountOrFrozenCardAtRunTime() {
        // Setup card funded schedule
        Card frozenCard = new Card(
                201L, 101L, 1L, "TEST_TOKEN", "1111", 12, 2029, "Visa", "My Card",
                CardKind.VIRTUAL, CardStatus.FROZEN, true, 1000000L, 5000000L,
                Instant.now(), Instant.now(), Instant.now(), null, 1L, null, true
        );
        when(cardStore.findByIdAndUserId(201L, 101L)).thenReturn(Optional.of(frozenCard));

        ScheduledPayment cardSchedule = new ScheduledPayment(
                null, 101L, ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", null),
                100000L, "LKR", false, FundingSource.card(201L),
                ScheduleFrequency.WEEKLY, LocalDate.now(), null,
                Instant.now(), ScheduleStatus.ACTIVE, 0, null, 1L, Instant.now(), Instant.now()
        );
        cardSchedule = scheduleStore.save(cardSchedule);

        List<ScheduledRunRecord> runs = scheduledPaymentService.runDueSchedules(Instant.now());
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).status()).isEqualTo(ScheduledRunStatus.FAILED);
        assertThat(runs.get(0).failureReason()).contains("Card is frozen");

        verify(transferService, never()).initiateScheduledTransfer(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Object-level access (IDOR): User B cannot access or modify User A's schedule")
    void testObjectLevelAccess_IDORProtection() {
        CreateScheduleRequest req = new CreateScheduleRequest(
                ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", null),
                100000L, "LKR", false, FundingSource.account(1L),
                ScheduleFrequency.MONTHLY, LocalDate.now(), null, true, validPin
        );
        ScheduledPayment schedule = scheduledPaymentService.createSchedule(userA, req, null);

        // User B tries to view
        assertThatThrownBy(() -> scheduledPaymentService.getScheduleById(userB, schedule.id()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("permission");

        // User B tries to view run history
        assertThatThrownBy(() -> scheduledPaymentService.getScheduleRuns(userB, schedule.id()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("permission");

        // User B tries to update
        UpdateScheduleRequest updateReq = new UpdateScheduleRequest(null, null, null, null, null, null, ScheduleStatus.PAUSED, null);
        assertThatThrownBy(() -> scheduledPaymentService.updateSchedule(userB, schedule.id(), updateReq, null))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("permission");

        // User B tries to cancel
        assertThatThrownBy(() -> scheduledPaymentService.cancelSchedule(userB, schedule.id()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("permission");
    }

    @Test
    @DisplayName("Edit amount or payee requires step-up auth again")
    void testEditAmountOrPayee_RequiresStepUpAuthAgain() {
        CreateScheduleRequest req = new CreateScheduleRequest(
                ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", null),
                100000L, "LKR", false, FundingSource.account(1L),
                ScheduleFrequency.MONTHLY, LocalDate.now(), null, true, validPin
        );
        ScheduledPayment schedule = scheduledPaymentService.createSchedule(userA, req, null);

        // Edit amount without PIN -> Fails
        UpdateScheduleRequest editNoPin = new UpdateScheduleRequest(
                null, 200000L, false, null, null, null, null, null
        );
        assertThatThrownBy(() -> scheduledPaymentService.updateSchedule(userA, schedule.id(), editNoPin, null))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Step-up authentication required");

        // Edit amount with valid PIN -> Succeeds
        UpdateScheduleRequest editWithPin = new UpdateScheduleRequest(
                null, 200000L, false, null, null, null, null, validPin
        );
        ScheduledPayment updated = scheduledPaymentService.updateSchedule(userA, schedule.id(), editWithPin, null);
        assertThat(updated.amountMinor()).isEqualTo(200000L);
        assertThat(updated.version()).isEqualTo(2L);
    }

    @Test
    @DisplayName("24-hour reminder notification sends privacy-safe alert")
    void test24HourReminderJob() {
        Instant now = Instant.now();
        Instant dueTomorrow = now.plus(Duration.ofHours(18));

        ScheduledPayment schedule = new ScheduledPayment(
                null, 101L, ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", null),
                100000L, "LKR", false, FundingSource.account(1L),
                ScheduleFrequency.MONTHLY, LocalDate.now().plusDays(1), null,
                dueTomorrow, ScheduleStatus.ACTIVE, 0, null, 1L, now, now
        );
        schedule = scheduleStore.save(schedule);

        int remindersSent = scheduledPaymentService.send24HourReminders(now);
        assertThat(remindersSent).isEqualTo(1);

        verify(notificationService).dispatch(
                eq(userA),
                eq(NotificationType.GENERAL),
                contains("Upcoming"),
                contains("tomorrow")
        );

        // Second call should not duplicate reminder
        int secondSent = scheduledPaymentService.send24HourReminders(now);
        assertThat(secondSent).isEqualTo(0);
    }

    @Test
    @DisplayName("Pause, resume, and cancel flows operate correctly")
    void testPauseResumeCancelFlow() {
        CreateScheduleRequest req = new CreateScheduleRequest(
                ScheduleType.TRANSFER,
                ScheduledPayee.forTransfer("+94773333333", "Charlie", null),
                100000L, "LKR", false, FundingSource.account(1L),
                ScheduleFrequency.MONTHLY, LocalDate.now(), null, true, validPin
        );
        ScheduledPayment schedule = scheduledPaymentService.createSchedule(userA, req, null);

        // Pause
        UpdateScheduleRequest pauseReq = new UpdateScheduleRequest(null, null, null, null, null, null, ScheduleStatus.PAUSED, null);
        ScheduledPayment paused = scheduledPaymentService.updateSchedule(userA, schedule.id(), pauseReq, null);
        assertThat(paused.status()).isEqualTo(ScheduleStatus.PAUSED);

        // Resume
        UpdateScheduleRequest resumeReq = new UpdateScheduleRequest(null, null, null, null, null, null, ScheduleStatus.ACTIVE, null);
        ScheduledPayment resumed = scheduledPaymentService.updateSchedule(userA, schedule.id(), resumeReq, null);
        assertThat(resumed.status()).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(resumed.consecutiveFailures()).isEqualTo(0);

        // Cancel
        ScheduledPayment cancelled = scheduledPaymentService.cancelSchedule(userA, schedule.id());
        assertThat(cancelled.status()).isEqualTo(ScheduleStatus.CANCELLED);
    }
}

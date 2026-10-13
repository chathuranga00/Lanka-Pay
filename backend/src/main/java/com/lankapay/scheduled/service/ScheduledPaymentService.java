package com.lankapay.scheduled.service;

import com.lankapay.admin.service.RiskManagementService;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bill.adapter.BillerAdapter;
import com.lankapay.bill.adapter.BillerAdapterRegistry;
import com.lankapay.bill.adapter.BillerInfo;
import com.lankapay.bill.service.BillService;
import com.lankapay.bill.web.dto.BillPaymentReceiptResponse;
import com.lankapay.bill.web.dto.PayBillRequest;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.common.exception.*;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.domain.NotificationType;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.scheduled.domain.*;
import com.lankapay.scheduled.web.dto.CreateScheduleRequest;
import com.lankapay.scheduled.web.dto.UpdateScheduleRequest;
import com.lankapay.transfer.domain.FundingSource;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.service.CardDailyLimitService;
import com.lankapay.transfer.service.DailyLimitService;
import com.lankapay.transfer.service.TransferService;
import com.lankapay.transfer.web.dto.InitiateTransferRequest;
import com.lankapay.transfer.web.dto.TransferResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ScheduledPaymentService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPaymentService.class);

    private final ScheduledPaymentStore scheduleStore;
    private final ScheduledRunStore runStore;
    private final UserStore userStore;
    private final LinkedAccountStore linkedAccountStore;
    private final CardStore cardStore;
    private final DailyLimitService dailyLimitService;
    private final CardDailyLimitService cardDailyLimitService;
    private final RiskManagementService riskManagementService;
    private final TransferService transferService;
    private final BillService billService;
    private final BillerAdapterRegistry billerAdapterRegistry;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Autowired
    public ScheduledPaymentService(
            ScheduledPaymentStore scheduleStore,
            ScheduledRunStore runStore,
            UserStore userStore,
            @Autowired(required = false) LinkedAccountStore linkedAccountStore,
            @Autowired(required = false) CardStore cardStore,
            DailyLimitService dailyLimitService,
            @Autowired(required = false) CardDailyLimitService cardDailyLimitService,
            @Autowired(required = false) RiskManagementService riskManagementService,
            TransferService transferService,
            BillService billService,
            BillerAdapterRegistry billerAdapterRegistry,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService,
            AuditService auditService
    ) {
        this.scheduleStore = scheduleStore;
        this.runStore = runStore;
        this.userStore = userStore;
        this.linkedAccountStore = linkedAccountStore;
        this.cardStore = cardStore;
        this.dailyLimitService = dailyLimitService;
        this.cardDailyLimitService = cardDailyLimitService;
        this.riskManagementService = riskManagementService;
        this.transferService = transferService;
        this.billService = billService;
        this.billerAdapterRegistry = billerAdapterRegistry;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // ── Schedule Management (CRUD & Step-up Auth) ───────────────────

    @Transactional
    public ScheduledPayment createSchedule(User user, CreateScheduleRequest request, String stepUpPinHeader) {
        // 1. Step-up Authentication check
        String pinToVerify = (request.pin() != null && !request.pin().isBlank()) ? request.pin() : stepUpPinHeader;
        verifyStepUpPin(user, pinToVerify);

        // 2. Consent check
        if (request.consent() == null || !request.consent()) {
            throw new ValidationException("CONSENT_REQUIRED", "User consent is required to schedule payments");
        }

        // 3. Date validation
        if (request.startDate() == null) {
            throw new ValidationException("START_DATE_REQUIRED", "Start date is required");
        }
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        LocalDate todayLocal = LocalDate.now(java.time.ZoneId.systemDefault());
        if (request.startDate().isBefore(todayUtc) && request.startDate().isBefore(todayLocal)) {
            throw new ValidationException("INVALID_START_DATE", "Start date cannot be in the past");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ValidationException("INVALID_END_DATE", "End date cannot be before start date");
        }

        // 4. Amount & Type validation
        boolean isBill = request.type() == ScheduleType.BILL;
        boolean isBillAmt = Boolean.TRUE.equals(request.isBillAmount());
        if (isBillAmt && !isBill) {
            throw new ValidationException("INVALID_BILL_AMOUNT_CONFIG", "Dynamic bill amount is only allowed for BILL payments");
        }
        if (!isBillAmt && (request.amountMinor() == null || request.amountMinor() <= 0)) {
            throw new ValidationException("INVALID_AMOUNT", "Amount must be greater than zero");
        }

        // 5. Evaluate Funding Source & Limits at Creation
        FundingSource fundingSource = request.fundingSource() != null ? request.fundingSource() : FundingSource.account(null);
        BigDecimal amount = isBillAmt ? BigDecimal.ZERO : BigDecimal.valueOf(request.amountMinor(), 2);

        validateFundingSourceAndLimits(user, fundingSource, amount, isBillAmt);

        // 6. Validate Payee & Risk Rules at Creation
        ScheduledPayee payee = request.payee();
        if (payee == null) {
            throw new ValidationException("PAYEE_REQUIRED", "Payee is required");
        }

        if (request.type() == ScheduleType.TRANSFER) {
            if (payee.identifier() == null || payee.identifier().isBlank()) {
                throw new ValidationException("PAYEE_REQUIRED", "Payee identifier (phone or user ID) is required");
            }
            User payeeUser = resolvePayeeUser(payee.identifier());
            if (payeeUser.getId().equals(user.getId())) {
                throw new ValidationException("CANNOT_TRANSFER_TO_SELF", "Cannot schedule transfer to self");
            }
            if (riskManagementService != null && !isBillAmt) {
                riskManagementService.evaluateTransferRisk(user, amount);
            }
        } else {
            if (payee.billerId() == null || payee.customerReference() == null) {
                throw new ValidationException("BILLER_REQUIRED", "Biller ID and customer reference are required");
            }
            BillerAdapter adapter = billerAdapterRegistry.getAdapterForBiller(payee.billerId());
            BillerInfo biller = adapter.getBiller(payee.billerId())
                    .orElseThrow(() -> new ResourceNotFoundException("BILLER_NOT_FOUND", "Biller not found: " + payee.billerId()));

            if (isBillAmt) {
                var bill = adapter.fetchBill(biller.id(), payee.customerReference());
                if (bill.amountDue() != null && bill.amountDue().compareTo(BigDecimal.ZERO) > 0) {
                    validateFundingSourceAndLimits(user, fundingSource, bill.amountDue(), false);
                }
            }
        }

        // 7. Calculate initial nextRunAt
        Instant nextRunAt;
        if (request.startDate().isEqual(todayUtc) || request.startDate().isEqual(todayLocal)) {
            nextRunAt = Instant.now();
        } else {
            nextRunAt = request.startDate().atTime(9, 0).atZone(ZoneOffset.UTC).toInstant();
        }

        Instant now = Instant.now();
        ScheduledPayment schedule = new ScheduledPayment(
                null,
                user.getId(),
                request.type(),
                payee,
                request.amountMinor(),
                request.currency() != null ? request.currency() : "LKR",
                isBillAmt,
                fundingSource,
                request.frequency(),
                request.startDate(),
                request.endDate(),
                nextRunAt,
                ScheduleStatus.ACTIVE,
                0,
                null,
                1L,
                now,
                now
        );

        ScheduledPayment saved = scheduleStore.save(schedule);
        log.info("Scheduled payment created id={} for userId={}", saved.id(), user.getId());
        if (auditService != null) {
            auditService.log(user.getId(), "SCHEDULE_CREATED", "SCHEDULED_PAYMENT", saved.id(), "{\"type\":\"" + saved.type() + "\"}", "127.0.0.1");
        }
        return saved;
    }

    public List<ScheduledPayment> listUserSchedules(User user) {
        return scheduleStore.findByUserId(user.getId());
    }

    public ScheduledPayment getScheduleById(User user, Long scheduleId) {
        ScheduledPayment schedule = scheduleStore.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("SCHEDULE_NOT_FOUND", "Schedule not found: " + scheduleId));
        checkScheduleOwnership(user, schedule);
        return schedule;
    }

    @Transactional
    public ScheduledPayment updateSchedule(User user, Long scheduleId, UpdateScheduleRequest request, String stepUpPinHeader) {
        ScheduledPayment schedule = scheduleStore.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("SCHEDULE_NOT_FOUND", "Schedule not found: " + scheduleId));
        checkScheduleOwnership(user, schedule);

        if (schedule.status() == ScheduleStatus.CANCELLED || schedule.status() == ScheduleStatus.COMPLETED) {
            throw new ConflictException("INVALID_SCHEDULE_STATE", "Cannot update a " + schedule.status() + " schedule");
        }

        // Check if editing amount, dynamic bill amount, or payee requires step-up auth
        boolean amountChanging = request.amountMinor() != null && !Objects.equals(request.amountMinor(), schedule.amountMinor());
        boolean billAmountChanging = request.isBillAmount() != null && !Objects.equals(request.isBillAmount(), schedule.isBillAmount());
        boolean payeeChanging = request.payee() != null && !Objects.equals(request.payee(), schedule.payee());

        if (amountChanging || billAmountChanging || payeeChanging) {
            String pinToVerify = (request.pin() != null && !request.pin().isBlank()) ? request.pin() : stepUpPinHeader;
            verifyStepUpPin(user, pinToVerify);

            if (amountChanging && request.amountMinor() != null && request.amountMinor() > 0) {
                BigDecimal newAmt = BigDecimal.valueOf(request.amountMinor(), 2);
                validateFundingSourceAndLimits(user, schedule.fundingSource(), newAmt, false);
            }
        }

        ScheduleStatus nextStatus = request.status() != null ? request.status() : schedule.status();
        int consecutiveFailures = schedule.consecutiveFailures();
        // Resuming resets failures
        if (request.status() == ScheduleStatus.ACTIVE && schedule.status() == ScheduleStatus.PAUSED) {
            consecutiveFailures = 0;
        }

        Instant nextRunAt = schedule.nextRunAt();
        if (request.status() == ScheduleStatus.ACTIVE && nextRunAt == null) {
            nextRunAt = Instant.now();
        }

        Instant now = Instant.now();
        ScheduledPayment updated = schedule.withUpdate(
                request.payee(),
                request.amountMinor(),
                request.isBillAmount() != null ? request.isBillAmount() : schedule.isBillAmount(),
                request.fundingSource(),
                request.frequency(),
                request.endDate(),
                nextRunAt,
                nextStatus,
                now
        );
        if (consecutiveFailures != schedule.consecutiveFailures()) {
            updated = new ScheduledPayment(
                    updated.id(), updated.userId(), updated.type(), updated.payee(),
                    updated.amountMinor(), updated.currency(), updated.isBillAmount(),
                    updated.fundingSource(), updated.frequency(), updated.startDate(),
                    updated.endDate(), updated.nextRunAt(), updated.status(),
                    consecutiveFailures, updated.lastReminderSentAt(),
                    updated.version(), updated.createdAt(), now
            );
        }

        ScheduledPayment saved = scheduleStore.save(updated);
        log.info("Schedule id={} updated for userId={}", saved.id(), user.getId());
        if (auditService != null) {
            auditService.log(user.getId(), "SCHEDULE_UPDATED", "SCHEDULED_PAYMENT", saved.id(), "{\"status\":\"" + saved.status() + "\"}", "127.0.0.1");
        }
        return saved;
    }

    @Transactional
    public ScheduledPayment cancelSchedule(User user, Long scheduleId) {
        ScheduledPayment schedule = scheduleStore.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("SCHEDULE_NOT_FOUND", "Schedule not found: " + scheduleId));
        checkScheduleOwnership(user, schedule);

        if (schedule.status() == ScheduleStatus.CANCELLED) {
            return schedule;
        }

        ScheduledPayment updated = schedule.withStatus(ScheduleStatus.CANCELLED, Instant.now());
        ScheduledPayment saved = scheduleStore.save(updated);
        log.info("Schedule id={} cancelled by userId={}", saved.id(), user.getId());
        if (auditService != null) {
            auditService.log(user.getId(), "SCHEDULE_CANCELLED", "SCHEDULED_PAYMENT", saved.id(), "{\"status\":\"CANCELLED\"}", "127.0.0.1");
        }
        return saved;
    }

    public List<ScheduledRunRecord> getScheduleRuns(User user, Long scheduleId) {
        ScheduledPayment schedule = scheduleStore.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("SCHEDULE_NOT_FOUND", "Schedule not found: " + scheduleId));
        checkScheduleOwnership(user, schedule);
        return runStore.findByScheduleId(scheduleId);
    }

    // ── Scheduled Job Execution (Due Items & 24h Reminders) ─────────

    /**
     * Periodically runs due items using exclusive locking and scheduleId + runDate idempotency.
     */
    @Scheduled(fixedDelay = 60000)
    public void runScheduledJob() {
        runDueSchedules(Instant.now());
    }

    /**
     * Core execution method exposed for testing and cron jobs.
     */
    @Transactional
    public List<ScheduledRunRecord> runDueSchedules(Instant now) {
        List<ScheduledPayment> dueList = scheduleStore.findDueSchedules(now);
        List<ScheduledRunRecord> executedRuns = new ArrayList<>();

        for (ScheduledPayment schedule : dueList) {
            LocalDate runDate = LocalDate.ofInstant(
                    schedule.nextRunAt() != null ? schedule.nextRunAt() : now,
                    ZoneOffset.UTC
            );

            // Double run protection: Lock + Idempotency per scheduleId + runDate
            if (!runStore.acquireRunLock(schedule.id(), runDate)) {
                log.info("Lock already held or run already completed for scheduleId={} runDate={}", schedule.id(), runDate);
                continue;
            }

            String idempotencyKey = "SCHED-" + schedule.id() + "-" + runDate;
            try {
                // Check if already processed
                if (runStore.findByIdempotencyKey(idempotencyKey).isPresent() ||
                        runStore.findByScheduleIdAndRunDate(schedule.id(), runDate).isPresent()) {
                    log.info("Idempotent skip: scheduleId={} runDate={} already executed", schedule.id(), runDate);
                    continue;
                }

                ScheduledRunRecord runRecord = executeSingleRun(schedule, runDate, idempotencyKey, now);
                executedRuns.add(runRecord);

            } finally {
                runStore.releaseRunLock(schedule.id(), runDate);
            }
        }

        return executedRuns;
    }

    private ScheduledRunRecord executeSingleRun(
            ScheduledPayment schedule,
            LocalDate runDate,
            String idempotencyKey,
            Instant now
    ) {
        // Resolve user
        Optional<UserAccount> userOpt = userStore.findUserById(schedule.userId());
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return recordRunFailure(schedule, runDate, idempotencyKey, "USER_INACTIVE_OR_REVOKED", now);
        }
        User user = userOpt.get().toUser();

        Long runAmountMinor = schedule.amountMinor();
        BigDecimal amount = (runAmountMinor != null) ? BigDecimal.valueOf(runAmountMinor, 2) : BigDecimal.ZERO;

        // 1. Evaluate Funding Source & Limits at Run Time
        try {
            if (schedule.isBillAmount() && schedule.type() == ScheduleType.BILL) {
                // Dynamic bill amount lookup
                BillerAdapter adapter = billerAdapterRegistry.getAdapterForBiller(schedule.payee().billerId());
                var bill = adapter.fetchBill(schedule.payee().billerId(), schedule.payee().customerReference());
                if (bill.amountDue() == null || bill.amountDue().compareTo(BigDecimal.ZERO) <= 0) {
                    return recordRunFailure(schedule, runDate, idempotencyKey, "NO_BILL_DUE", now);
                }
                amount = bill.amountDue();
                runAmountMinor = amount.multiply(BigDecimal.valueOf(100)).longValue();
            }

            validateFundingSourceAndLimits(user, schedule.fundingSource(), amount, false);

            if (schedule.type() == ScheduleType.TRANSFER) {
                if (riskManagementService != null) {
                    riskManagementService.evaluateTransferRisk(user, amount);
                }
            }
        } catch (Exception ex) {
            log.warn("Pre-run limits or validation failed for scheduleId={}: {}", schedule.id(), ex.getMessage());
            return recordRunFailure(schedule, runDate, idempotencyKey, ex.getMessage(), now);
        }

        // 2. Execute Transaction
        String txRef = null;
        try {
            if (schedule.type() == ScheduleType.TRANSFER) {
                User payeeUser = resolvePayeeUser(schedule.payee().identifier());
                InitiateTransferRequest txReq = new InitiateTransferRequest(
                        payeeUser.getId(),
                        payeeUser.getPhone(),
                        amount,
                        schedule.currency(),
                        null,
                        schedule.payee().note() != null ? schedule.payee().note() : "Scheduled Transfer",
                        null,
                        schedule.fundingSource()
                );
                TransferResponse resp = transferService.initiateScheduledTransfer(txReq, user, idempotencyKey, "127.0.0.1");
                txRef = resp.reference();
            } else {
                PayBillRequest billReq = new PayBillRequest(
                        schedule.payee().billerId(),
                        schedule.payee().customerReference(),
                        amount,
                        schedule.currency(),
                        schedule.fundingSource(),
                        null,
                        schedule.payee().note() != null ? schedule.payee().note() : "Scheduled Bill Payment",
                        false,
                        null
                );
                BillPaymentReceiptResponse resp = billService.payScheduledBill(user, billReq, idempotencyKey);
                txRef = resp.reference();
            }

            // Success!
            ScheduledRunRecord runRecord = new ScheduledRunRecord(
                    null,
                    schedule.id(),
                    runDate,
                    idempotencyKey,
                    txRef,
                    runAmountMinor,
                    schedule.currency(),
                    ScheduledRunStatus.SUCCESS,
                    null,
                    now
            );
            runRecord = runStore.save(runRecord);

            // Advance schedule to next interval and reset failures
            advanceScheduleSuccess(schedule, runDate, now);
            log.info("Scheduled payment succeeded: scheduleId={} runId={} txRef={}", schedule.id(), runRecord.id(), txRef);
            return runRecord;

        } catch (Exception ex) {
            log.error("Scheduled payment execution exception for scheduleId={}: {}", schedule.id(), ex.getMessage());
            return recordRunFailure(schedule, runDate, idempotencyKey, ex.getMessage(), now);
        }
    }

    private ScheduledRunRecord recordRunFailure(
            ScheduledPayment schedule,
            LocalDate runDate,
            String idempotencyKey,
            String reason,
            Instant now
    ) {
        ScheduledRunRecord runRecord = new ScheduledRunRecord(
                null,
                schedule.id(),
                runDate,
                idempotencyKey,
                null,
                schedule.amountMinor(),
                schedule.currency(),
                ScheduledRunStatus.FAILED,
                reason,
                now
        );
        runRecord = runStore.save(runRecord);

        int consecutiveFailures = schedule.consecutiveFailures() + 1;
        ScheduleStatus nextStatus = consecutiveFailures >= 2 ? ScheduleStatus.PAUSED : schedule.status();
        Instant nextRun = calculateNextRunDate(schedule, now);

        ScheduledPayment updated = schedule.withNextRun(nextRun, nextStatus, consecutiveFailures, now);
        scheduleStore.save(updated);

        // Notify user about failure / auto-pause (strictly privacy safe)
        userStore.findUserById(schedule.userId()).ifPresent(acc -> {
            User u = acc.toUser();
            if (consecutiveFailures >= 2) {
                notificationService.dispatch(
                        u,
                        NotificationType.PAYMENT_FAILED,
                        "Scheduled Payment Paused",
                        "Your scheduled payment was paused after consecutive failed attempts. Tap to manage."
                );
            } else {
                notificationService.dispatch(
                        u,
                        NotificationType.PAYMENT_FAILED,
                        "Scheduled Payment Failed",
                        "Your scheduled payment could not be processed. Tap to review your schedule."
                );
            }
        });

        log.warn("Scheduled payment failed: scheduleId={} consecutiveFailures={} autoPause={}",
                schedule.id(), consecutiveFailures, consecutiveFailures >= 2);
        return runRecord;
    }

    private void advanceScheduleSuccess(ScheduledPayment schedule, LocalDate currentRunDate, Instant now) {
        if (schedule.frequency() == ScheduleFrequency.ONCE) {
            ScheduledPayment completed = schedule.withNextRun(null, ScheduleStatus.COMPLETED, 0, now);
            scheduleStore.save(completed);
            return;
        }

        Instant nextRun = calculateNextRunDate(schedule, now);
        ScheduleStatus nextStatus = ScheduleStatus.ACTIVE;
        if (nextRun == null) {
            nextStatus = ScheduleStatus.COMPLETED;
        }

        ScheduledPayment updated = schedule.withNextRun(nextRun, nextStatus, 0, now);
        scheduleStore.save(updated);
    }

    private Instant calculateNextRunDate(ScheduledPayment schedule, Instant referenceTime) {
        if (schedule.frequency() == ScheduleFrequency.ONCE) {
            return null;
        }

        Instant base = schedule.nextRunAt() != null ? schedule.nextRunAt() : referenceTime;
        Instant next;
        if (schedule.frequency() == ScheduleFrequency.WEEKLY) {
            next = base.plus(7, ChronoUnit.DAYS);
        } else {
            // MONTHLY
            next = base.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        }

        if (schedule.endDate() != null) {
            LocalDate nextDate = LocalDate.ofInstant(next, ZoneOffset.UTC);
            if (nextDate.isAfter(schedule.endDate())) {
                return null;
            }
        }
        return next;
    }

    /**
     * Dispatches reminder notification 24 hours before an upcoming run.
     */
    @Scheduled(fixedDelay = 300000)
    public void runReminderJob() {
        send24HourReminders(Instant.now());
    }

    public int send24HourReminders(Instant now) {
        Instant windowEnd = now.plus(Duration.ofHours(24));
        List<ScheduledPayment> upcoming = scheduleStore.findUpcomingForReminders(now, windowEnd);
        int sent = 0;

        for (ScheduledPayment schedule : upcoming) {
            // Prevent duplicate reminders for the same run
            if (schedule.lastReminderSentAt() != null &&
                    schedule.lastReminderSentAt().isAfter(now.minus(Duration.ofHours(20)))) {
                continue;
            }

            userStore.findUserById(schedule.userId()).ifPresent(acc -> {
                User user = acc.toUser();
                notificationService.dispatch(
                        user,
                        NotificationType.GENERAL,
                        "Upcoming Scheduled Payment",
                        "You have a scheduled payment due tomorrow. Open the app to view details."
                );
            });

            ScheduledPayment updated = schedule.withReminderSent(now);
            scheduleStore.save(updated);
            sent++;
        }

        if (sent > 0) {
            log.info("Sent 24-hour reminders for {} upcoming scheduled payments", sent);
        }
        return sent;
    }

    // ── Helper Validations ──────────────────────────────────────────

    private void verifyStepUpPin(User user, String pin) {
        if (pin == null || pin.isBlank()) {
            throw new AuthorizationException("STEP_UP_AUTH_REQUIRED", "Step-up authentication required: PIN must be provided");
        }
        if (!passwordEncoder.matches(pin, user.getPasswordHash())) {
            throw new InvalidCredentialsException("INVALID_PIN", "Invalid PIN provided for step-up authentication");
        }
    }

    private void checkScheduleOwnership(User user, ScheduledPayment schedule) {
        if (!Objects.equals(schedule.userId(), user.getId())) {
            throw new AuthorizationException("FORBIDDEN", "You do not have permission to access this schedule");
        }
    }

    private void validateFundingSourceAndLimits(User user, FundingSource fs, BigDecimal amount, boolean isBillAmt) {
        if (fs.type() == FundingType.CARD) {
            if (fs.id() == null) {
                throw new ValidationException("CARD_ID_REQUIRED", "Card ID is required");
            }
            if (cardStore == null) {
                throw new LankaPayException("CARD_SERVICE_UNAVAILABLE", "Card service unavailable");
            }
            Card card = cardStore.findByIdAndUserId(fs.id(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("CARD_NOT_FOUND", "Card not found: " + fs.id()));

            if (card.isFrozen() || card.status() == CardStatus.FROZEN) {
                throw new UnprocessableEntityException("CARD_FROZEN", "Card is frozen");
            }
            if (!card.isActive() || card.status() != CardStatus.ACTIVE) {
                throw new UnprocessableEntityException("CARD_NOT_ACTIVE", "Card is not active");
            }
            if (card.isExpired() || card.status() == CardStatus.EXPIRED) {
                throw new UnprocessableEntityException("CARD_EXPIRED", "Card has expired");
            }
            if (!card.onlinePaymentsEnabled()) {
                throw new UnprocessableEntityException("ONLINE_PAYMENTS_DISABLED", "Online payments disabled for this card");
            }
            if (!isBillAmt && amount.compareTo(BigDecimal.ZERO) > 0 && cardDailyLimitService != null) {
                cardDailyLimitService.validateLimit(card, amount);
            }
        } else {
            // Bank Account
            if (fs.id() != null && linkedAccountStore != null) {
                LinkedAccount acc = linkedAccountStore.findAccountByIdAndUserId(fs.id(), user.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Account not found: " + fs.id()));
                if (!acc.isActive() || acc.status() != BankAccountStatus.ACTIVE) {
                    throw new UnprocessableEntityException("ACCOUNT_NOT_ACTIVE", "Bank account is not active");
                }
            }
            if (!isBillAmt && amount.compareTo(BigDecimal.ZERO) > 0) {
                dailyLimitService.checkLimit(user, amount);
            }
        }
    }

    private User resolvePayeeUser(String identifier) {
        String trimmed = identifier.trim();
        if (trimmed.startsWith("+") || trimmed.startsWith("0")) {
            return userStore.findUserByPhone(trimmed)
                    .map(UserAccount::toUser)
                    .orElseThrow(() -> new ResourceNotFoundException("PAYEE_NOT_FOUND", "Payee not found: " + trimmed));
        }
        try {
            Long payeeId = Long.parseLong(trimmed);
            return userStore.findUserById(payeeId)
                    .or(() -> userStore.findUserByPhone(trimmed))
                    .map(UserAccount::toUser)
                    .orElseThrow(() -> new ResourceNotFoundException("PAYEE_NOT_FOUND", "Payee not found: " + trimmed));
        } catch (NumberFormatException e) {
            return userStore.findUserByPhone(trimmed)
                    .map(UserAccount::toUser)
                    .orElseThrow(() -> new ResourceNotFoundException("PAYEE_NOT_FOUND", "Payee not found: " + trimmed));
        }
    }
}

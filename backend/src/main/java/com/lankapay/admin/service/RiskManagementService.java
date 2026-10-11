package com.lankapay.admin.service;

import com.lankapay.admin.domain.*;
import com.lankapay.admin.repository.FraudAlertRepository;
import com.lankapay.admin.repository.RiskRulesRepository;
import com.lankapay.admin.web.dto.FraudAlertResponse;
import com.lankapay.admin.web.dto.RiskRulesResponse;
import com.lankapay.admin.web.dto.UpdateFraudAlertStatusRequest;
import com.lankapay.admin.web.dto.UpdateRiskRulesRequest;
import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.identity.domain.User;
import com.lankapay.transfer.domain.DailyLimit;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.repository.DailyLimitRepository;
import com.lankapay.transfer.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class RiskManagementService {

    private static final Logger log = LoggerFactory.getLogger(RiskManagementService.class);

    private final RiskRulesRepository riskRulesRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final TransferRepository transferRepository;
    private final DailyLimitRepository dailyLimitRepository;
    private final AuditService auditService;

    public RiskManagementService(RiskRulesRepository riskRulesRepository,
                                 FraudAlertRepository fraudAlertRepository,
                                 TransferRepository transferRepository,
                                 DailyLimitRepository dailyLimitRepository,
                                 AuditService auditService) {
        this.riskRulesRepository = riskRulesRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.transferRepository = transferRepository;
        this.dailyLimitRepository = dailyLimitRepository;
        this.auditService = auditService;
    }

    /**
     * Get active risk rules (cached/singleton row).
     */
    @Transactional(readOnly = true)
    public RiskRules getActiveRules() {
        return riskRulesRepository.findById(1L)
                .orElseGet(() -> {
                    RiskRules defaults = new RiskRules();
                    defaults.setId(1L);
                    return riskRulesRepository.save(defaults);
                });
    }

    /**
     * Evaluate transfer risk against configurable rules:
     * 1. Per-transaction limit
     * 2. New-account limits (per-transaction & daily)
     * 3. Velocity checks (max transfers in recent window)
     */
    @Transactional
    public void evaluateTransferRisk(User sender, BigDecimal amount) {
        RiskRules rules = getActiveRules();
        if (!rules.isEnabled()) {
            return;
        }

        // 1. Per-transaction limit
        if (amount.compareTo(rules.getMaxTransactionAmount()) > 0) {
            String desc = "Transfer amount of %s LKR exceeds maximum per-transaction limit of %s LKR"
                    .formatted(amount, rules.getMaxTransactionAmount());
            log.warn("[RISK] User id={} breached per-transaction limit: amount={}, max={}",
                    sender.getId(), amount, rules.getMaxTransactionAmount());

            raiseAlert(sender, null, FraudAlertType.PER_TRANSACTION_LIMIT_EXCEEDED,
                    FraudAlertSeverity.HIGH, desc,
                    "{\"amount\":\"%s\",\"maxAllowed\":\"%s\"}".formatted(amount, rules.getMaxTransactionAmount()));

            throw new UnprocessableEntityException("PER_TRANSACTION_LIMIT_EXCEEDED", desc);
        }

        // 2. New-account limits
        long accountAgeHours = ChronoUnit.HOURS.between(sender.getCreatedAt(), Instant.now());
        if (accountAgeHours < rules.getNewAccountAgeHours()) {
            // New account per-transaction limit check
            if (amount.compareTo(rules.getNewAccountMaxTransactionAmount()) > 0) {
                String desc = "New account (<%d hours old) transfer of %s LKR exceeds new-account transaction limit of %s LKR"
                        .formatted(rules.getNewAccountAgeHours(), amount, rules.getNewAccountMaxTransactionAmount());
                log.warn("[RISK] New account id={} breached transaction limit: amount={}, max={}",
                        sender.getId(), amount, rules.getNewAccountMaxTransactionAmount());

                raiseAlert(sender, null, FraudAlertType.NEW_ACCOUNT_LIMIT_EXCEEDED,
                        FraudAlertSeverity.HIGH, desc,
                        "{\"amount\":\"%s\",\"maxAllowed\":\"%s\",\"accountAgeHours\":%d}"
                                .formatted(amount, rules.getNewAccountMaxTransactionAmount(), accountAgeHours));

                throw new UnprocessableEntityException("NEW_ACCOUNT_LIMIT_EXCEEDED", desc);
            }

            // New account daily cap check
            LocalDate today = LocalDate.now();
            BigDecimal todaySent = dailyLimitRepository.findByUserIdAndLimitDate(sender.getId(), today)
                    .map(DailyLimit::getTotalSent)
                    .orElse(BigDecimal.ZERO);
            BigDecimal projectedDaily = todaySent.add(amount);

            if (projectedDaily.compareTo(rules.getNewAccountMaxDailyAmount()) > 0) {
                String desc = "New account daily spend (%s LKR) exceeds new-account daily limit of %s LKR"
                        .formatted(projectedDaily, rules.getNewAccountMaxDailyAmount());
                log.warn("[RISK] New account id={} breached daily limit: projected={}, max={}",
                        sender.getId(), projectedDaily, rules.getNewAccountMaxDailyAmount());

                raiseAlert(sender, null, FraudAlertType.NEW_ACCOUNT_LIMIT_EXCEEDED,
                        FraudAlertSeverity.HIGH, desc,
                        "{\"projectedDaily\":\"%s\",\"maxAllowed\":\"%s\",\"accountAgeHours\":%d}"
                                .formatted(projectedDaily, rules.getNewAccountMaxDailyAmount(), accountAgeHours));

                throw new UnprocessableEntityException("NEW_ACCOUNT_DAILY_LIMIT_EXCEEDED", desc);
            }
        }

        // 3. Velocity checks
        Instant velocitySince = Instant.now().minus(rules.getVelocityWindowMinutes(), ChronoUnit.MINUTES);
        long recentTransferCount = transferRepository.countBySenderIdAndCreatedAtAfter(sender.getId(), velocitySince);

        if (recentTransferCount >= rules.getMaxVelocityCountPerHour()) {
            String desc = "Velocity check failed: user initiated %d transfers in the last %d minutes (limit: %d)"
                    .formatted(recentTransferCount, rules.getVelocityWindowMinutes(), rules.getMaxVelocityCountPerHour());
            log.warn("[RISK] Velocity limit breached for user id={}: recentCount={}, maxAllowed={}",
                    sender.getId(), recentTransferCount, rules.getMaxVelocityCountPerHour());

            raiseAlert(sender, null, FraudAlertType.VELOCITY_EXCEEDED,
                    FraudAlertSeverity.HIGH, desc,
                    "{\"recentCount\":%d,\"maxAllowed\":%d,\"windowMinutes\":%d}"
                            .formatted(recentTransferCount, rules.getMaxVelocityCountPerHour(), rules.getVelocityWindowMinutes()));

            throw new UnprocessableEntityException("VELOCITY_LIMIT_EXCEEDED", desc);
        }
    }

    /**
     * Raise a fraud alert and persist it to the database.
     */
    @Transactional
    public FraudAlert raiseAlert(User user, Transfer transfer, FraudAlertType alertType,
                                 FraudAlertSeverity severity, String description, String metadataJson) {
        FraudAlert alert = new FraudAlert(user, transfer, alertType, severity, description, metadataJson);
        FraudAlert saved = fraudAlertRepository.save(alert);
        log.info("[FRAUD ALERT] Raised alert id={} type={} severity={} for user id={}",
                saved.getId(), alertType, severity, user.getId());

        auditService.log(user.getId(), "FRAUD_ALERT_RAISED", "FRAUD_ALERT", saved.getId(),
                "{\"alertType\":\"%s\",\"severity\":\"%s\"}".formatted(alertType, severity), "SYSTEM");

        return saved;
    }

    @Transactional(readOnly = true)
    public RiskRulesResponse getRiskRules() {
        return RiskRulesResponse.from(getActiveRules());
    }

    @Transactional
    public RiskRulesResponse updateRiskRules(UpdateRiskRulesRequest req, Long adminUserId, String clientIp) {
        RiskRules rules = getActiveRules();

        if (req.maxTransactionAmount() != null) {
            rules.setMaxTransactionAmount(req.maxTransactionAmount());
        }
        if (req.maxDailyAmount() != null) {
            rules.setMaxDailyAmount(req.maxDailyAmount());
        }
        if (req.maxVelocityCountPerHour() != null) {
            rules.setMaxVelocityCountPerHour(req.maxVelocityCountPerHour());
        }
        if (req.newAccountAgeHours() != null) {
            rules.setNewAccountAgeHours(req.newAccountAgeHours());
        }
        if (req.newAccountMaxTransactionAmount() != null) {
            rules.setNewAccountMaxTransactionAmount(req.newAccountMaxTransactionAmount());
        }
        if (req.newAccountMaxDailyAmount() != null) {
            rules.setNewAccountMaxDailyAmount(req.newAccountMaxDailyAmount());
        }
        if (req.velocityWindowMinutes() != null) {
            rules.setVelocityWindowMinutes(req.velocityWindowMinutes());
        }
        if (req.enabled() != null) {
            rules.setEnabled(req.enabled());
        }
        rules.setUpdatedBy(adminUserId);

        RiskRules saved = riskRulesRepository.save(rules);

        auditService.log(adminUserId, "ADMIN_UPDATE_RISK_RULES", "RISK_RULES", saved.getId(),
                "{\"enabled\":%b,\"maxTx\":\"%s\"}".formatted(saved.isEnabled(), saved.getMaxTransactionAmount()), clientIp);

        return RiskRulesResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<FraudAlertResponse> getFraudAlerts(FraudAlertStatus status, FraudAlertSeverity severity, Long userId, Pageable pageable) {
        if (userId != null) {
            return fraudAlertRepository.findByUserId(userId, pageable).map(FraudAlertResponse::from);
        }
        if (status != null && severity != null) {
            return fraudAlertRepository.findByStatusAndSeverity(status, severity, pageable).map(FraudAlertResponse::from);
        }
        if (status != null) {
            return fraudAlertRepository.findByStatus(status, pageable).map(FraudAlertResponse::from);
        }
        return fraudAlertRepository.findAll(pageable).map(FraudAlertResponse::from);
    }

    @Transactional(readOnly = true)
    public FraudAlertResponse getFraudAlert(Long id) {
        FraudAlert alert = fraudAlertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FRAUD_ALERT_NOT_FOUND", "Fraud alert not found with id: " + id));
        return FraudAlertResponse.from(alert);
    }

    @Transactional
    public FraudAlertResponse updateFraudAlertStatus(Long id, UpdateFraudAlertStatusRequest req, Long adminUserId, String clientIp) {
        FraudAlert alert = fraudAlertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FRAUD_ALERT_NOT_FOUND", "Fraud alert not found with id: " + id));

        alert.updateStatus(req.status(), adminUserId, req.resolutionNotes());
        FraudAlert saved = fraudAlertRepository.save(alert);

        auditService.log(adminUserId, "ADMIN_UPDATE_FRAUD_ALERT_STATUS", "FRAUD_ALERT", saved.getId(),
                "{\"newStatus\":\"%s\"}".formatted(saved.getStatus()), clientIp);

        return FraudAlertResponse.from(saved);
    }
}

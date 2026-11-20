package com.lankapay.admin.service;

import com.lankapay.admin.domain.*;
import com.lankapay.admin.web.dto.FraudAlertResponse;
import com.lankapay.admin.web.dto.RiskRulesResponse;
import com.lankapay.admin.web.dto.UpdateFraudAlertStatusRequest;
import com.lankapay.admin.web.dto.UpdateRiskRulesRequest;
import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.identity.domain.User;
import com.lankapay.transfer.domain.DailyLimitRecord;
import com.lankapay.transfer.domain.TransactionStore;
import com.lankapay.transfer.domain.Transfer;
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

    private final AdminStore adminStore;
    private final TransactionStore transactionStore;
    private final AuditService auditService;

    public RiskManagementService(AdminStore adminStore,
                                 TransactionStore transactionStore,
                                 AuditService auditService) {
        this.adminStore = adminStore;
        this.transactionStore = transactionStore;
        this.auditService = auditService;
    }

    /**
     * Get active risk rules (cached/singleton row).
     */
    @Transactional(readOnly = true)
    public RiskRules getActiveRules() {
        RiskRulesRecord rec = adminStore.findRiskRules()
                .orElseGet(() -> adminStore.saveRiskRules(RiskRulesRecord.defaultRules()));
        RiskRules rules = new RiskRules();
        rules.setId(rec.id());
        rules.setMaxTransactionAmount(rec.maxTransactionAmount());
        rules.setMaxDailyAmount(rec.maxDailyAmount());
        rules.setMaxVelocityCountPerHour(rec.maxVelocityCountPerHour());
        rules.setNewAccountAgeHours(rec.newAccountAgeHours());
        rules.setNewAccountMaxTransactionAmount(rec.newAccountMaxTransactionAmount());
        rules.setNewAccountMaxDailyAmount(rec.newAccountMaxDailyAmount());
        rules.setVelocityWindowMinutes(rec.velocityWindowMinutes());
        rules.setEnabled(rec.enabled());
        rules.setCreatedAt(rec.createdAt());
        rules.setUpdatedAt(rec.updatedAt());
        rules.setUpdatedBy(rec.updatedBy());
        return rules;
    }

    /**
     * Evaluate transfer risk against configurable rules.
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
            BigDecimal todaySent = transactionStore != null ? transactionStore.findDailyLimit(sender.getId(), today)
                    .map(DailyLimitRecord::totalSent)
                    .orElse(BigDecimal.ZERO) : BigDecimal.ZERO;
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
        long recentTransferCount = transactionStore != null ?
                transactionStore.countTransfersBySenderIdAndCreatedAtAfter(sender.getId(), velocitySince) : 0L;

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
     * Raise a fraud alert and persist it in Firestore.
     */
    @Transactional
    public FraudAlert raiseAlert(User user, Transfer transfer, FraudAlertType alertType,
                                 FraudAlertSeverity severity, String description, String metadataJson) {
        FraudAlertRecord record = new FraudAlertRecord(
                null,
                user.getId(),
                user.getPhone(),
                user.getFullName(),
                transfer != null ? transfer.getId() : null,
                transfer != null ? transfer.getReference() : null,
                alertType,
                severity,
                FraudAlertStatus.OPEN,
                description,
                metadataJson,
                Instant.now(),
                null,
                null,
                null
        );
        FraudAlertRecord saved = adminStore.saveFraudAlert(record);
        log.info("[FRAUD ALERT] Raised alert id={} type={} severity={} for user id={}",
                saved.id(), alertType, severity, user.getId());

        auditService.log(user.getId(), "FRAUD_ALERT_RAISED", "FRAUD_ALERT", saved.id(),
                "{\"alertType\":\"%s\",\"severity\":\"%s\"}".formatted(alertType, severity), "SYSTEM");

        FraudAlert alert = new FraudAlert(user, transfer, alertType, severity, description, metadataJson);
        alert.setId(saved.id());
        return alert;
    }

    @Transactional(readOnly = true)
    public RiskRulesResponse getRiskRules() {
        RiskRulesRecord rec = adminStore.findRiskRules()
                .orElseGet(() -> adminStore.saveRiskRules(RiskRulesRecord.defaultRules()));
        return RiskRulesResponse.from(rec);
    }

    @Transactional
    public RiskRulesResponse updateRiskRules(UpdateRiskRulesRequest req, Long adminUserId, String clientIp) {
        RiskRulesRecord current = adminStore.findRiskRules()
                .orElseGet(RiskRulesRecord::defaultRules);

        RiskRulesRecord updated = new RiskRulesRecord(
                current.id(),
                req.maxTransactionAmount() != null ? req.maxTransactionAmount() : current.maxTransactionAmount(),
                req.maxDailyAmount() != null ? req.maxDailyAmount() : current.maxDailyAmount(),
                req.maxVelocityCountPerHour() != null ? req.maxVelocityCountPerHour() : current.maxVelocityCountPerHour(),
                req.newAccountAgeHours() != null ? req.newAccountAgeHours() : current.newAccountAgeHours(),
                req.newAccountMaxTransactionAmount() != null ? req.newAccountMaxTransactionAmount() : current.newAccountMaxTransactionAmount(),
                req.newAccountMaxDailyAmount() != null ? req.newAccountMaxDailyAmount() : current.newAccountMaxDailyAmount(),
                req.velocityWindowMinutes() != null ? req.velocityWindowMinutes() : current.velocityWindowMinutes(),
                req.enabled() != null ? req.enabled() : current.enabled(),
                current.createdAt(),
                Instant.now(),
                adminUserId
        );
        RiskRulesRecord saved = adminStore.saveRiskRules(updated);

        auditService.log(adminUserId, "ADMIN_UPDATE_RISK_RULES", "RISK_RULES", saved.id(),
                "{\"enabled\":%b,\"maxTx\":\"%s\"}".formatted(saved.enabled(), saved.maxTransactionAmount()), clientIp);

        return RiskRulesResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<FraudAlertResponse> getFraudAlerts(FraudAlertStatus status, FraudAlertSeverity severity, Long userId, Pageable pageable) {
        if (userId != null) {
            return adminStore.findFraudAlertsByUserId(userId, pageable).map(FraudAlertResponse::from);
        }
        if (status != null && severity != null) {
            return adminStore.findFraudAlertsByStatusAndSeverity(status, severity, pageable).map(FraudAlertResponse::from);
        }
        if (status != null) {
            return adminStore.findFraudAlertsByStatus(status, pageable).map(FraudAlertResponse::from);
        }
        return adminStore.findAllFraudAlerts(pageable).map(FraudAlertResponse::from);
    }

    @Transactional(readOnly = true)
    public FraudAlertResponse getFraudAlert(Long id) {
        FraudAlertRecord alert = adminStore.findFraudAlertById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FRAUD_ALERT_NOT_FOUND", "Fraud alert not found with id: " + id));
        return FraudAlertResponse.from(alert);
    }

    @Transactional
    public FraudAlertResponse updateFraudAlertStatus(Long id, UpdateFraudAlertStatusRequest req, Long adminUserId, String clientIp) {
        FraudAlertRecord alert = adminStore.findFraudAlertById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FRAUD_ALERT_NOT_FOUND", "Fraud alert not found with id: " + id));

        FraudAlertRecord updated = new FraudAlertRecord(
                alert.id(),
                alert.userId(),
                alert.userPhone(),
                alert.userFullName(),
                alert.transferId(),
                alert.transferReference(),
                alert.alertType(),
                alert.severity(),
                req.status(),
                alert.description(),
                alert.metadataJson(),
                alert.createdAt(),
                Instant.now(),
                adminUserId,
                req.resolutionNotes()
        );
        FraudAlertRecord saved = adminStore.saveFraudAlert(updated);

        auditService.log(adminUserId, "ADMIN_UPDATE_FRAUD_ALERT_STATUS", "FRAUD_ALERT", saved.id(),
                "{\"newStatus\":\"%s\"}".formatted(saved.status()), clientIp);

        return FraudAlertResponse.from(saved);
    }
}

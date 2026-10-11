package com.lankapay.admin.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Domain port interface for administrative risk management and fraud alert persistence.
 */
public interface AdminStore {

    Optional<RiskRulesRecord> findRiskRules();

    RiskRulesRecord saveRiskRules(RiskRulesRecord rules);

    Optional<FraudAlertRecord> findFraudAlertById(Long id);

    FraudAlertRecord saveFraudAlert(FraudAlertRecord alert);

    Page<FraudAlertRecord> findFraudAlertsByStatus(FraudAlertStatus status, Pageable pageable);

    Page<FraudAlertRecord> findFraudAlertsByUserId(Long userId, Pageable pageable);

    Page<FraudAlertRecord> findFraudAlertsByStatusAndSeverity(FraudAlertStatus status, FraudAlertSeverity severity, Pageable pageable);

    Page<FraudAlertRecord> findAllFraudAlerts(Pageable pageable);
}

package com.lankapay.admin.repository;

import com.lankapay.admin.domain.FraudAlert;
import com.lankapay.admin.domain.FraudAlertSeverity;
import com.lankapay.admin.domain.FraudAlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long>, JpaSpecificationExecutor<FraudAlert> {

    Page<FraudAlert> findByStatus(FraudAlertStatus status, Pageable pageable);

    Page<FraudAlert> findByUserId(Long userId, Pageable pageable);

    Page<FraudAlert> findByStatusAndSeverity(FraudAlertStatus status, FraudAlertSeverity severity, Pageable pageable);
}

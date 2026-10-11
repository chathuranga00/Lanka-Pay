package com.lankapay.admin.service;

import com.lankapay.admin.domain.*;
import com.lankapay.admin.repository.FraudAlertRepository;
import com.lankapay.admin.repository.RiskRulesRepository;
import com.lankapay.admin.web.dto.UpdateFraudAlertStatusRequest;
import com.lankapay.admin.web.dto.UpdateRiskRulesRequest;
import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.transfer.domain.DailyLimit;
import com.lankapay.transfer.repository.DailyLimitRepository;
import com.lankapay.transfer.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskManagementServiceTest {

    @Mock RiskRulesRepository riskRulesRepository;
    @Mock FraudAlertRepository fraudAlertRepository;
    @Mock TransferRepository transferRepository;
    @Mock DailyLimitRepository dailyLimitRepository;
    @Mock AuditService auditService;

    RiskManagementService service;
    RiskRules rules;
    User establishedUser;
    User newUser;

    @BeforeEach
    void setUp() {
        service = new RiskManagementService(
                riskRulesRepository,
                fraudAlertRepository,
                transferRepository,
                dailyLimitRepository,
                auditService
        );

        rules = new RiskRules();
        rules.setId(1L);
        rules.setMaxTransactionAmount(new BigDecimal("50000.00"));
        rules.setMaxDailyAmount(new BigDecimal("100000.00"));
        rules.setMaxVelocityCountPerHour(5);
        rules.setNewAccountAgeHours(24);
        rules.setNewAccountMaxTransactionAmount(new BigDecimal("10000.00"));
        rules.setNewAccountMaxDailyAmount(new BigDecimal("25000.00"));
        rules.setVelocityWindowMinutes(60);
        rules.setEnabled(true);

        lenient().when(riskRulesRepository.findById(1L)).thenReturn(Optional.of(rules));

        // Established user: created 10 days ago
        establishedUser = new User("+94771111111", "Old User", "199011111111", "hash");
        ReflectionTestUtils.setField(establishedUser, "id", 10L);
        ReflectionTestUtils.setField(establishedUser, "createdAt", Instant.now().minus(10, ChronoUnit.DAYS));

        // New user: created 2 hours ago
        newUser = new User("+94772222222", "New User", "199522222222", "hash");
        ReflectionTestUtils.setField(newUser, "id", 20L);
        ReflectionTestUtils.setField(newUser, "createdAt", Instant.now().minus(2, ChronoUnit.HOURS));

        lenient().when(fraudAlertRepository.save(any(FraudAlert.class))).thenAnswer(inv -> {
            FraudAlert a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 100L);
            return a;
        });
    }

    @Nested
    @DisplayName("Per-Transaction Limit Enforcement")
    class PerTransactionLimits {

        @Test
        @DisplayName("Transfer within max per-transaction limit succeeds")
        void transferWithinLimitPasses() {
            when(transferRepository.countBySenderIdAndCreatedAtAfter(eq(10L), any(Instant.class)))
                    .thenReturn(0L);

            service.evaluateTransferRisk(establishedUser, new BigDecimal("45000.00"));

            verify(fraudAlertRepository, never()).save(any());
        }

        @Test
        @DisplayName("Transfer exceeding max per-transaction limit throws exception and raises fraud alert")
        void transferExceedingLimitBlockedAndAlerted() {
            assertThatThrownBy(() ->
                    service.evaluateTransferRisk(establishedUser, new BigDecimal("55000.00"))
            ).isInstanceOf(UnprocessableEntityException.class)
             .hasMessageContaining("exceeds maximum per-transaction limit");

            ArgumentCaptor<FraudAlert> alertCaptor = ArgumentCaptor.forClass(FraudAlert.class);
            verify(fraudAlertRepository).save(alertCaptor.capture());

            FraudAlert alert = alertCaptor.getValue();
            assertThat(alert.getAlertType()).isEqualTo(FraudAlertType.PER_TRANSACTION_LIMIT_EXCEEDED);
            assertThat(alert.getSeverity()).isEqualTo(FraudAlertSeverity.HIGH);
            assertThat(alert.getUser().getId()).isEqualTo(10L);

            verify(auditService).log(eq(10L), eq("FRAUD_ALERT_RAISED"), eq("FRAUD_ALERT"), any(), any(), eq("SYSTEM"));
        }
    }

    @Nested
    @DisplayName("New-Account Limits Enforcement")
    class NewAccountLimits {

        @Test
        @DisplayName("New account under new-account limits passes")
        void newAccountUnderLimitPasses() {
            when(dailyLimitRepository.findByUserIdAndLimitDate(eq(20L), any(LocalDate.class)))
                    .thenReturn(Optional.of(new DailyLimit(newUser, LocalDate.now(), new BigDecimal("25000.00"))));
            when(transferRepository.countBySenderIdAndCreatedAtAfter(eq(20L), any(Instant.class)))
                    .thenReturn(1L);

            service.evaluateTransferRisk(newUser, new BigDecimal("8000.00"));

            verify(fraudAlertRepository, never()).save(any());
        }

        @Test
        @DisplayName("New account exceeding new-account per-transaction limit is blocked and raises fraud alert")
        void newAccountExceedingTxLimitBlocked() {
            assertThatThrownBy(() ->
                    service.evaluateTransferRisk(newUser, new BigDecimal("12000.00"))
            ).isInstanceOf(UnprocessableEntityException.class)
             .hasMessageContaining("exceeds new-account transaction limit");

            ArgumentCaptor<FraudAlert> alertCaptor = ArgumentCaptor.forClass(FraudAlert.class);
            verify(fraudAlertRepository).save(alertCaptor.capture());

            FraudAlert alert = alertCaptor.getValue();
            assertThat(alert.getAlertType()).isEqualTo(FraudAlertType.NEW_ACCOUNT_LIMIT_EXCEEDED);
            assertThat(alert.getSeverity()).isEqualTo(FraudAlertSeverity.HIGH);
            assertThat(alert.getUser().getId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("New account exceeding new-account daily limit is blocked and raises fraud alert")
        void newAccountExceedingDailyLimitBlocked() {
            DailyLimit daily = new DailyLimit(newUser, LocalDate.now(), new BigDecimal("25000.00"));
            daily.addSent(new BigDecimal("20000.00"));

            when(dailyLimitRepository.findByUserIdAndLimitDate(eq(20L), any(LocalDate.class)))
                    .thenReturn(Optional.of(daily));

            assertThatThrownBy(() ->
                    service.evaluateTransferRisk(newUser, new BigDecimal("6000.00"))
            ).isInstanceOf(UnprocessableEntityException.class)
             .hasMessageContaining("exceeds new-account daily limit");

            ArgumentCaptor<FraudAlert> alertCaptor = ArgumentCaptor.forClass(FraudAlert.class);
            verify(fraudAlertRepository).save(alertCaptor.capture());

            FraudAlert alert = alertCaptor.getValue();
            assertThat(alert.getAlertType()).isEqualTo(FraudAlertType.NEW_ACCOUNT_LIMIT_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("Velocity Checks Enforcement")
    class VelocityChecks {

        @Test
        @DisplayName("User with rapid consecutive transactions breaches velocity check and is blocked")
        void velocityBreachBlockedAndAlerted() {
            when(transferRepository.countBySenderIdAndCreatedAtAfter(eq(10L), any(Instant.class)))
                    .thenReturn(5L); // limit is 5

            assertThatThrownBy(() ->
                    service.evaluateTransferRisk(establishedUser, new BigDecimal("1000.00"))
            ).isInstanceOf(UnprocessableEntityException.class)
             .hasMessageContaining("Velocity check failed");

            ArgumentCaptor<FraudAlert> alertCaptor = ArgumentCaptor.forClass(FraudAlert.class);
            verify(fraudAlertRepository).save(alertCaptor.capture());

            FraudAlert alert = alertCaptor.getValue();
            assertThat(alert.getAlertType()).isEqualTo(FraudAlertType.VELOCITY_EXCEEDED);
            assertThat(alert.getSeverity()).isEqualTo(FraudAlertSeverity.HIGH);
        }
    }

    @Nested
    @DisplayName("Audit Log Entries Created on Admin Actions")
    class AdminAuditing {

        @Test
        @DisplayName("Updating risk rules generates audit entry")
        void updateRiskRulesCreatesAuditEntry() {
            when(riskRulesRepository.save(any(RiskRules.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateRiskRulesRequest req = new UpdateRiskRulesRequest(
                    new BigDecimal("60000.00"),
                    new BigDecimal("120000.00"),
                    10,
                    48,
                    new BigDecimal("15000.00"),
                    new BigDecimal("30000.00"),
                    60,
                    true
            );

            service.updateRiskRules(req, 999L, "192.168.1.1");

            verify(auditService).log(eq(999L), eq("ADMIN_UPDATE_RISK_RULES"), eq("RISK_RULES"), eq(1L), anyString(), eq("192.168.1.1"));
        }

        @Test
        @DisplayName("Updating fraud alert status generates audit entry")
        void updateFraudAlertStatusCreatesAuditEntry() {
            FraudAlert alert = new FraudAlert(establishedUser, null, FraudAlertType.SUSPICIOUS_ACTIVITY,
                    FraudAlertSeverity.MEDIUM, "test", null);
            ReflectionTestUtils.setField(alert, "id", 77L);

            when(fraudAlertRepository.findById(77L)).thenReturn(Optional.of(alert));
            when(fraudAlertRepository.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateFraudAlertStatusRequest req = new UpdateFraudAlertStatusRequest(
                    FraudAlertStatus.RESOLVED,
                    "Verified customer identity"
            );

            service.updateFraudAlertStatus(77L, req, 999L, "192.168.1.1");

            assertThat(alert.getStatus()).isEqualTo(FraudAlertStatus.RESOLVED);
            assertThat(alert.getResolvedBy()).isEqualTo(999L);
            verify(auditService).log(eq(999L), eq("ADMIN_UPDATE_FRAUD_ALERT_STATUS"), eq("FRAUD_ALERT"), eq(77L), anyString(), eq("192.168.1.1"));
        }
    }
}

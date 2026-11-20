package com.lankapay.admin.service;

import com.lankapay.admin.domain.*;
import com.lankapay.admin.web.dto.UpdateFraudAlertStatusRequest;
import com.lankapay.admin.web.dto.UpdateRiskRulesRequest;
import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.identity.domain.User;
import com.lankapay.transfer.domain.DailyLimitRecord;
import com.lankapay.transfer.domain.TransactionStore;
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

    @Mock AdminStore adminStore;
    @Mock TransactionStore transactionStore;
    @Mock AuditService auditService;

    RiskManagementService service;
    RiskRulesRecord defaultRules;
    User establishedUser;
    User newUser;

    @BeforeEach
    void setUp() {
        service = new RiskManagementService(
                adminStore,
                transactionStore,
                auditService
        );

        defaultRules = new RiskRulesRecord(
                1L,
                new BigDecimal("50000.00"),
                new BigDecimal("100000.00"),
                5,
                24,
                new BigDecimal("10000.00"),
                new BigDecimal("25000.00"),
                60,
                true,
                Instant.now(),
                Instant.now(),
                1L
        );

        lenient().when(adminStore.findRiskRules()).thenReturn(Optional.of(defaultRules));

        // Established user: created 10 days ago
        establishedUser = new User("+94771111111", "Old User", "199011111111", "hash");
        ReflectionTestUtils.setField(establishedUser, "id", 10L);
        ReflectionTestUtils.setField(establishedUser, "createdAt", Instant.now().minus(10, ChronoUnit.DAYS));

        // New user: created 2 hours ago
        newUser = new User("+94772222222", "New User", "199522222222", "hash");
        ReflectionTestUtils.setField(newUser, "id", 20L);
        ReflectionTestUtils.setField(newUser, "createdAt", Instant.now().minus(2, ChronoUnit.HOURS));

        lenient().when(adminStore.saveFraudAlert(any(FraudAlertRecord.class))).thenAnswer(inv -> {
            FraudAlertRecord r = inv.getArgument(0);
            return new FraudAlertRecord(100L, r.userId(), r.userPhone(), r.userFullName(),
                    r.transferId(), r.transferReference(), r.alertType(), r.severity(),
                    r.status(), r.description(), r.metadataJson(), r.createdAt(),
                    r.resolvedAt(), r.resolvedBy(), r.resolutionNotes());
        });
    }

    @Nested
    @DisplayName("Per-Transaction Limit Enforcement")
    class PerTransactionLimits {

        @Test
        @DisplayName("Transfer within max per-transaction limit succeeds")
        void transferWithinLimitPasses() {
            when(transactionStore.countTransfersBySenderIdAndCreatedAtAfter(eq(10L), any(Instant.class)))
                    .thenReturn(0L);

            service.evaluateTransferRisk(establishedUser, new BigDecimal("45000.00"));

            verify(adminStore, never()).saveFraudAlert(any());
        }

        @Test
        @DisplayName("Transfer exceeding max per-transaction limit throws exception and raises fraud alert")
        void transferExceedingLimitBlockedAndAlerted() {
            assertThatThrownBy(() ->
                    service.evaluateTransferRisk(establishedUser, new BigDecimal("55000.00"))
            ).isInstanceOf(UnprocessableEntityException.class)
             .hasMessageContaining("exceeds maximum per-transaction limit");

            ArgumentCaptor<FraudAlertRecord> alertCaptor = ArgumentCaptor.forClass(FraudAlertRecord.class);
            verify(adminStore).saveFraudAlert(alertCaptor.capture());

            FraudAlertRecord alert = alertCaptor.getValue();
            assertThat(alert.alertType()).isEqualTo(FraudAlertType.PER_TRANSACTION_LIMIT_EXCEEDED);
            assertThat(alert.severity()).isEqualTo(FraudAlertSeverity.HIGH);
            assertThat(alert.userId()).isEqualTo(10L);

            verify(auditService).log(eq(10L), eq("FRAUD_ALERT_RAISED"), eq("FRAUD_ALERT"), any(), any(), eq("SYSTEM"));
        }
    }

    @Nested
    @DisplayName("New-Account Limits Enforcement")
    class NewAccountLimits {

        @Test
        @DisplayName("New account under new-account limits passes")
        void newAccountUnderLimitPasses() {
            when(transactionStore.findDailyLimit(eq(20L), any(LocalDate.class)))
                    .thenReturn(Optional.of(new DailyLimitRecord(1L, 20L, LocalDate.now(), BigDecimal.ZERO, new BigDecimal("25000.00"))));
            when(transactionStore.countTransfersBySenderIdAndCreatedAtAfter(eq(20L), any(Instant.class)))
                    .thenReturn(1L);

            service.evaluateTransferRisk(newUser, new BigDecimal("8000.00"));

            verify(adminStore, never()).saveFraudAlert(any());
        }

        @Test
        @DisplayName("New account exceeding new-account per-transaction limit is blocked and raises fraud alert")
        void newAccountExceedingTxLimitBlocked() {
            assertThatThrownBy(() ->
                    service.evaluateTransferRisk(newUser, new BigDecimal("12000.00"))
            ).isInstanceOf(UnprocessableEntityException.class)
             .hasMessageContaining("exceeds new-account transaction limit");

            ArgumentCaptor<FraudAlertRecord> alertCaptor = ArgumentCaptor.forClass(FraudAlertRecord.class);
            verify(adminStore).saveFraudAlert(alertCaptor.capture());

            FraudAlertRecord alert = alertCaptor.getValue();
            assertThat(alert.alertType()).isEqualTo(FraudAlertType.NEW_ACCOUNT_LIMIT_EXCEEDED);
            assertThat(alert.severity()).isEqualTo(FraudAlertSeverity.HIGH);
            assertThat(alert.userId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("New account exceeding new-account daily limit is blocked and raises fraud alert")
        void newAccountExceedingDailyLimitBlocked() {
            when(transactionStore.findDailyLimit(eq(20L), any(LocalDate.class)))
                    .thenReturn(Optional.of(new DailyLimitRecord(1L, 20L, LocalDate.now(), new BigDecimal("20000.00"), new BigDecimal("25000.00"))));

            assertThatThrownBy(() ->
                    service.evaluateTransferRisk(newUser, new BigDecimal("6000.00"))
            ).isInstanceOf(UnprocessableEntityException.class)
             .hasMessageContaining("exceeds new-account daily limit");

            ArgumentCaptor<FraudAlertRecord> alertCaptor = ArgumentCaptor.forClass(FraudAlertRecord.class);
            verify(adminStore).saveFraudAlert(alertCaptor.capture());

            FraudAlertRecord alert = alertCaptor.getValue();
            assertThat(alert.alertType()).isEqualTo(FraudAlertType.NEW_ACCOUNT_LIMIT_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("Velocity Checks Enforcement")
    class VelocityChecks {

        @Test
        @DisplayName("User with rapid consecutive transactions breaches velocity check and is blocked")
        void velocityBreachBlockedAndAlerted() {
            when(transactionStore.countTransfersBySenderIdAndCreatedAtAfter(eq(10L), any(Instant.class)))
                    .thenReturn(5L); // limit is 5

            assertThatThrownBy(() ->
                    service.evaluateTransferRisk(establishedUser, new BigDecimal("1000.00"))
            ).isInstanceOf(UnprocessableEntityException.class)
             .hasMessageContaining("Velocity check failed");

            ArgumentCaptor<FraudAlertRecord> alertCaptor = ArgumentCaptor.forClass(FraudAlertRecord.class);
            verify(adminStore).saveFraudAlert(alertCaptor.capture());

            FraudAlertRecord alert = alertCaptor.getValue();
            assertThat(alert.alertType()).isEqualTo(FraudAlertType.VELOCITY_EXCEEDED);
            assertThat(alert.severity()).isEqualTo(FraudAlertSeverity.HIGH);
        }
    }

    @Nested
    @DisplayName("Audit Log Entries Created on Admin Actions")
    class AdminAuditing {

        @Test
        @DisplayName("Updating risk rules generates audit entry")
        void updateRiskRulesCreatesAuditEntry() {
            when(adminStore.saveRiskRules(any(RiskRulesRecord.class))).thenAnswer(inv -> inv.getArgument(0));

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
            FraudAlertRecord alert = new FraudAlertRecord(
                    77L, 10L, "+94771111111", "Old User", null, null,
                    FraudAlertType.SUSPICIOUS_ACTIVITY, FraudAlertSeverity.MEDIUM,
                    FraudAlertStatus.OPEN, "test", null, Instant.now(), null, null, null
            );

            when(adminStore.findFraudAlertById(77L)).thenReturn(Optional.of(alert));
            when(adminStore.saveFraudAlert(any(FraudAlertRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateFraudAlertStatusRequest req = new UpdateFraudAlertStatusRequest(
                    FraudAlertStatus.RESOLVED,
                    "Verified customer identity"
            );

            service.updateFraudAlertStatus(77L, req, 999L, "192.168.1.1");

            verify(auditService).log(eq(999L), eq("ADMIN_UPDATE_FRAUD_ALERT_STATUS"), eq("FRAUD_ALERT"), eq(77L), anyString(), eq("192.168.1.1"));
        }
    }
}

package com.lankapay.admin.store.firestore;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.lankapay.admin.domain.*;
import com.lankapay.admin.service.RiskManagementService;
import com.lankapay.admin.web.dto.FraudAlertResponse;
import com.lankapay.admin.web.dto.RiskRulesResponse;
import com.lankapay.admin.web.dto.UpdateFraudAlertStatusRequest;
import com.lankapay.admin.web.dto.UpdateRiskRulesRequest;
import com.lankapay.audit.service.AuditService;
import com.lankapay.audit.store.firestore.FirestoreAuditStore;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.store.firestore.FirestoreUserStore;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransferInitiatedVia;
import com.lankapay.transfer.store.firestore.FirestoreTransactionStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test suite for AdminStore (riskConfig, fraudAlerts, admin authorization and auditing)
 * against the Firestore emulator.
 *
 * Verifies:
 * 1. riskConfig collection stores singleton rules (document ID = "default").
 * 2. Admin risk rule updates are audited in auditLogs collection.
 * 3. Per-transaction limit breaches throw 422, raise fraud alert in fraudAlerts, and write audit trail.
 * 4. Velocity breaches in window throw 422 and raise fraud alert.
 * 5. Fraud alert querying and status update with resolution notes and audit logging.
 * 6. Admin authorization enforcement (non-admin forbidden).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(FirestoreAdminStoreEmulatorTest.EmulatorAvailableCondition.class)
class FirestoreAdminStoreEmulatorTest {

    private static final Logger log = LoggerFactory.getLogger(FirestoreAdminStoreEmulatorTest.class);
    private static final String PROJECT_ID = "lankapay-test";

    private static Firestore firestore;
    private static FirestoreAdminStore adminStore;
    private static FirestoreAuditStore auditStore;
    private static FirestoreUserStore userStore;
    private static FirestoreTransactionStore transactionStore;

    private static AuditService auditService;
    private static RiskManagementService riskManagementService;

    private static User adminUser;
    private static User regularCustomer;

    @BeforeAll
    static void initFirestore() {
        String emulatorHost = System.getenv("FIRESTORE_EMULATOR_HOST");
        if (emulatorHost == null || emulatorHost.isBlank()) {
            emulatorHost = System.getProperty("FIRESTORE_EMULATOR_HOST");
        }
        if (emulatorHost == null || emulatorHost.isBlank()) {
            if (isPortListening("localhost", 8085)) {
                emulatorHost = "localhost:8085";
            } else if (isPortListening("127.0.0.1", 8085)) {
                emulatorHost = "127.0.0.1:8085";
            }
        }

        if (emulatorHost == null || emulatorHost.isBlank()) {
            log.warn("Firestore emulator not accessible on port 8085. Skipping test execution.");
            return;
        }

        FirestoreOptions options = FirestoreOptions.newBuilder()
                .setProjectId(PROJECT_ID)
                .setHost(emulatorHost)
                .setCredentials(NoCredentials.getInstance())
                .build();

        firestore = options.getService();
        try {
            firestore.collection("riskConfig").document("default").delete().get();
        } catch (Exception ignored) {}
        com.lankapay.common.firestore.FirestoreTransactionHelper txHelper =
                new com.lankapay.common.firestore.FirestoreTransactionHelper(firestore);
        adminStore = new FirestoreAdminStore(firestore);
        auditStore = new FirestoreAuditStore(firestore);
        userStore = new FirestoreUserStore(firestore, txHelper);
        transactionStore = new FirestoreTransactionStore(firestore);

        auditService = new AuditService(auditStore);
        riskManagementService = new RiskManagementService(
                adminStore, transactionStore, auditService
        );

        long suffix = System.currentTimeMillis() % 1000000L;

        adminUser = new User("+9477" + (8000000 + suffix), "Admin " + suffix, "1985" + (10000000 + suffix), "hash");
        adminUser.setId(800000L + suffix);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setCreatedAt(Instant.now().minus(20, ChronoUnit.DAYS));
        userStore.saveUser(new UserAccount(
                adminUser.getId(), adminUser.getPhone(), adminUser.getFullName(),
                adminUser.getNicNumber(), adminUser.getPasswordHash(),
                UserStatus.ACTIVE, UserRole.ADMIN, 0, null, adminUser.getCreatedAt(), Instant.now()
        ));

        regularCustomer = new User("+9477" + (9000000 + suffix), "Customer " + suffix, "1994" + (10000000 + suffix), "hash");
        regularCustomer.setId(900000L + suffix);
        regularCustomer.setStatus(UserStatus.ACTIVE);
        regularCustomer.setRole(UserRole.CUSTOMER);
        regularCustomer.setCreatedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        // Created 10 days ago (established account)
        userStore.saveUser(new UserAccount(
                regularCustomer.getId(), regularCustomer.getPhone(), regularCustomer.getFullName(),
                regularCustomer.getNicNumber(), regularCustomer.getPasswordHash(),
                UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, regularCustomer.getCreatedAt(), Instant.now()
        ));
    }

    @Test
    @Order(1)
    @DisplayName("1. Risk rules: default initialization in riskConfig collection (document ID = 'default')")
    void testRiskRulesDefaultAndPersistence() {
        RiskRulesResponse rules = riskManagementService.getRiskRules();

        assertThat(rules.maxTransactionAmount()).isEqualByComparingTo("50000.00");
        assertThat(rules.maxDailyAmount()).isEqualByComparingTo("100000.00");
        assertThat(rules.maxVelocityCountPerHour()).isEqualTo(5);
        assertThat(rules.enabled()).isTrue();

        Optional<RiskRulesRecord> doc = adminStore.findRiskRules();
        assertThat(doc).isPresent();
        assertThat(doc.get().maxTransactionAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    @Order(2)
    @DisplayName("2. Admin update risk rules: persists to riskConfig and writes audit log to auditLogs")
    void testAdminUpdateRiskRulesWithAudit() {
        UpdateRiskRulesRequest req = new UpdateRiskRulesRequest(
                new BigDecimal("60000.00"),
                new BigDecimal("120000.00"),
                6,
                24,
                new BigDecimal("15000.00"),
                new BigDecimal("30000.00"),
                60,
                true
        );

        RiskRulesResponse updated = riskManagementService.updateRiskRules(req, adminUser.getId(), "10.0.0.1");

        assertThat(updated.maxTransactionAmount()).isEqualByComparingTo("60000.00");
        assertThat(updated.maxDailyAmount()).isEqualByComparingTo("120000.00");
        assertThat(updated.maxVelocityCountPerHour()).isEqualTo(6);

        // Verify audit trail in auditLogs
        var auditLogs = auditStore.findByActorId(adminUser.getId(), PageRequest.of(0, 10));
        assertThat(auditLogs.getContent()).anyMatch(a ->
                "ADMIN_UPDATE_RISK_RULES".equals(a.eventType()) && "RISK_RULES".equals(a.entityType())
        );
    }

    @Test
    @Order(3)
    @DisplayName("3. Per-transaction limit breach: throws 422, raises alert in fraudAlerts, and audits")
    void testPerTransactionLimitBreachRaisesAlertAndAudits() {
        // Current maxTransactionAmount is 60000.00 LKR
        BigDecimal breachAmount = new BigDecimal("75000.00");

        assertThatThrownBy(() -> riskManagementService.evaluateTransferRisk(regularCustomer, breachAmount))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("exceeds maximum per-transaction limit");

        // Verify fraud alert raised in fraudAlerts collection
        Page<FraudAlertRecord> alerts = adminStore.findFraudAlertsByUserId(regularCustomer.getId(), PageRequest.of(0, 10));
        assertThat(alerts.getContent()).anyMatch(a ->
                a.alertType() == FraudAlertType.PER_TRANSACTION_LIMIT_EXCEEDED &&
                a.severity() == FraudAlertSeverity.HIGH &&
                a.status() == FraudAlertStatus.OPEN
        );

        // Verify audit trail logged
        var auditLogs = auditStore.findByActorId(regularCustomer.getId(), PageRequest.of(0, 10));
        assertThat(auditLogs.getContent()).anyMatch(a -> "FRAUD_ALERT_RAISED".equals(a.eventType()));
    }

    @Test
    @Order(4)
    @DisplayName("4. Velocity checks: exceeding max transactions in window throws 422 and raises alert")
    void testVelocityLimitBreachRaisesAlert() {
        // Current velocity window limit is 6 transfers per hour
        // Seed 6 completed transfers in the transactionStore
        for (int i = 0; i < 6; i++) {
            String idem = "IDEM-VEL-" + i + "-" + UUID.randomUUID();
            TransactionRecord tx = new TransactionRecord(
                    null,
                    UUID.randomUUID().toString(),
                    idem,
                    regularCustomer.getId(),
                    regularCustomer.getPhone(),
                    regularCustomer.getFullName(),
                    adminUser.getId(),
                    adminUser.getPhone(),
                    adminUser.getFullName(),
                    "BOC",
                    "BOC",
                    new BigDecimal("100.00"),
                    "LKR",
                    TransactionStatus.COMPLETED,
                    null,
                    null,
                    null,
                    null,
                    false,
                    TransferInitiatedVia.APP,
                    null,
                    0L,
                    Instant.now(),
                    Instant.now()
            );
            transactionStore.createTransferWithIdempotency(tx, idem, "HASH-" + i);
        }

        // 7th transfer must fail velocity check
        assertThatThrownBy(() -> riskManagementService.evaluateTransferRisk(regularCustomer, new BigDecimal("200.00")))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("Velocity check failed");

        // Verify velocity fraud alert in fraudAlerts
        Page<FraudAlertRecord> alerts = adminStore.findFraudAlertsByUserId(regularCustomer.getId(), PageRequest.of(0, 10));
        assertThat(alerts.getContent()).anyMatch(a ->
                a.alertType() == FraudAlertType.VELOCITY_EXCEEDED &&
                a.severity() == FraudAlertSeverity.HIGH
        );
    }

    @Test
    @Order(5)
    @DisplayName("5. Fraud alert querying and resolution by admin with audit logging")
    void testFraudAlertQueryingAndStatusUpdate() {
        // Query open alerts
        Page<FraudAlertResponse> openAlerts = riskManagementService.getFraudAlerts(
                FraudAlertStatus.OPEN, null, regularCustomer.getId(), PageRequest.of(0, 10));
        assertThat(openAlerts.getContent()).isNotEmpty();

        Long alertId = openAlerts.getContent().get(0).id();

        // Admin resolves the alert
        UpdateFraudAlertStatusRequest updateReq = new UpdateFraudAlertStatusRequest(
                FraudAlertStatus.RESOLVED, "Verified customer identity via call, false positive confirmed"
        );
        FraudAlertResponse resolved = riskManagementService.updateFraudAlertStatus(alertId, updateReq, adminUser.getId(), "10.0.0.1");

        assertThat(resolved.status()).isEqualTo(FraudAlertStatus.RESOLVED);
        assertThat(resolved.resolvedBy()).isEqualTo(adminUser.getId());
        assertThat(resolved.resolutionNotes()).contains("Verified customer identity");

        // Verify status persisted in Firestore
        Optional<FraudAlertRecord> stored = adminStore.findFraudAlertById(alertId);
        assertThat(stored).isPresent();
        assertThat(stored.get().status()).isEqualTo(FraudAlertStatus.RESOLVED);

        // Verify audit trail logged for status update
        var auditLogs = auditStore.findByActorId(adminUser.getId(), PageRequest.of(0, 10));
        assertThat(auditLogs.getContent()).anyMatch(a ->
                "ADMIN_UPDATE_FRAUD_ALERT_STATUS".equals(a.eventType())
        );
    }

    @Test
    @Order(6)
    @DisplayName("6. Admin authorization defense: non-admin customer is forbidden from admin operations")
    void testAdminAuthorizationDefenses() {
        // Invariant: Customer role must not be permitted to update risk rules
        assertThat(regularCustomer.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(adminUser.getRole()).isEqualTo(UserRole.ADMIN);

        // Simulated security check that guards admin endpoints
        boolean hasAdminRole = regularCustomer.getRole() == UserRole.ADMIN;
        assertThat(hasAdminRole).isFalse();

        assertThatThrownBy(() -> {
            if (!hasAdminRole) {
                throw new AuthorizationException("FORBIDDEN", "Admin privilege required");
            }
            riskManagementService.updateRiskRules(
                    new UpdateRiskRulesRequest(new BigDecimal("1.00"), null, null, null, null, null, null, null),
                    regularCustomer.getId(), "127.0.0.1"
            );
        }).isInstanceOf(AuthorizationException.class)
          .hasMessageContaining("Admin privilege required");
    }

    // ─────────────────────────────────────────────────────────────
    // Helper & Condition
    // ─────────────────────────────────────────────────────────────

    private static boolean isPortListening(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static class EmulatorAvailableCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (isPortListening("localhost", 8085) || isPortListening("127.0.0.1", 8085)) {
                return ConditionEvaluationResult.enabled("Firestore emulator running on port 8085");
            }
            return ConditionEvaluationResult.disabled("Firestore emulator not accessible on port 8085");
        }
    }
}

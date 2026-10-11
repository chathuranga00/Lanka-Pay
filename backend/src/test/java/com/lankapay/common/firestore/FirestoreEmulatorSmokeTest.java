package com.lankapay.common.firestore;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.*;
import com.lankapay.common.util.MoneyHelper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test verifying connectivity and transaction execution against a Google Cloud Firestore emulator.
 *
 * Connectivity priority:
 * 1. If FIRESTORE_EMULATOR_HOST is already set in the environment, connects to it directly.
 * 2. Otherwise, if Docker is available, launches Testcontainers FirestoreEmulatorContainer.
 * 3. If neither is available, skips gracefully without failing the build.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(FirestoreEmulatorSmokeTest.EmulatorOrDockerCondition.class)
class FirestoreEmulatorSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(FirestoreEmulatorSmokeTest.class);
    private static final String PROJECT_ID = "lankapay-test";
    private static final String TEST_COLLECTION = "_smoke_test_accounts";
    private static final String TEST_DOC_ID = "acc_smoke_1001";

    private static FirestoreEmulatorContainer container;
    private static Firestore firestore;
    private static FirestoreTransactionHelper transactionHelper;
    private static String emulatorHost;

    @BeforeAll
    static void initFirestore() {
        String existingHost = System.getenv("FIRESTORE_EMULATOR_HOST");
        if (existingHost == null || existingHost.isBlank()) {
            existingHost = System.getProperty("FIRESTORE_EMULATOR_HOST");
        }

        if (existingHost != null && !existingHost.isBlank()) {
            emulatorHost = existingHost;
            log.info("Using pre-configured FIRESTORE_EMULATOR_HOST: [{}]", emulatorHost);
        } else {
            log.info("Starting Testcontainers FirestoreEmulatorContainer...");
            container = new FirestoreEmulatorContainer(
                    DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:441.0.0-emulators")
            );
            container.start();
            emulatorHost = container.getEmulatorEndpoint();
            log.info("Started Testcontainers Firestore emulator at: [{}]", emulatorHost);
        }

        System.setProperty("FIRESTORE_EMULATOR_HOST", emulatorHost);

        FirestoreOptions options = FirestoreOptions.getDefaultInstance().toBuilder()
                .setProjectId(PROJECT_ID)
                .setHost(emulatorHost)
                .setCredentials(NoCredentials.getInstance())
                .build();

        firestore = options.getService();
        transactionHelper = new FirestoreTransactionHelper(firestore);
    }

    @AfterAll
    static void cleanup() {
        if (firestore != null) {
            try {
                firestore.collection(TEST_COLLECTION).document(TEST_DOC_ID).delete().get();
                firestore.close();
            } catch (Exception ignored) {
            }
        }
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Write monetary document to Firestore emulator (cents + currency)")
    void testWriteDocument() throws Exception {
        Map<String, Object> accountData = new HashMap<>();
        accountData.put("accountNumberMasked", "****1001");
        accountData.put("accountHolderName", "Kasun Perera");
        accountData.put("balanceMinor", MoneyHelper.toMinorUnits(new BigDecimal("1500.50"))); // 150050L
        accountData.put("currency", "LKR");
        accountData.put("status", "VERIFIED");
        accountData.put("updatedAt", FieldValue.serverTimestamp());

        WriteResult writeResult = firestore.collection(TEST_COLLECTION)
                .document(TEST_DOC_ID)
                .set(accountData)
                .get();

        assertThat(writeResult.getUpdateTime()).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("Read document back and verify MoneyHelper conversion")
    void testReadDocument() throws Exception {
        DocumentSnapshot snapshot = firestore.collection(TEST_COLLECTION)
                .document(TEST_DOC_ID)
                .get()
                .get();

        assertThat(snapshot.exists()).isTrue();
        assertThat(snapshot.getString("accountNumberMasked")).isEqualTo("****1001");
        assertThat(snapshot.getString("currency")).isEqualTo("LKR");

        Long balanceMinor = snapshot.getLong("balanceMinor");
        assertThat(balanceMinor).isEqualTo(150050L);

        BigDecimal balanceDecimal = MoneyHelper.toBigDecimal(balanceMinor);
        assertThat(balanceDecimal).isEqualByComparingTo("1500.50");
    }

    @Test
    @Order(3)
    @DisplayName("Execute atomic credit transaction using FirestoreTransactionHelper")
    void testAtomicTransaction() {
        long creditAmountMinor = MoneyHelper.toMinorUnits(new BigDecimal("500.00")); // 50000L

        Long newBalance = transactionHelper.runTransaction(tx -> {
            DocumentReference docRef = firestore.collection(TEST_COLLECTION).document(TEST_DOC_ID);
            DocumentSnapshot snap = tx.get(docRef).get();

            Long currentBalance = snap.getLong("balanceMinor");
            assertThat(currentBalance).isNotNull();

            long updated = currentBalance + creditAmountMinor;
            tx.update(docRef, "balanceMinor", updated);
            return updated;
        });

        assertThat(newBalance).isEqualTo(200050L);

        BigDecimal newBalanceDecimal = MoneyHelper.toBigDecimal(newBalance);
        assertThat(newBalanceDecimal).isEqualByComparingTo("2000.50");
    }

    /**
     * Skips test execution if neither a live emulator host nor Docker is available.
     */
    static class EmulatorOrDockerCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            String host = System.getenv("FIRESTORE_EMULATOR_HOST");
            if (host == null || host.isBlank()) {
                host = System.getProperty("FIRESTORE_EMULATOR_HOST");
            }
            if (host != null && !host.isBlank()) {
                return ConditionEvaluationResult.enabled("FIRESTORE_EMULATOR_HOST is configured: " + host);
            }

            try {
                DockerClientFactory.instance().client();
                return ConditionEvaluationResult.enabled("Docker is available for Firestore emulator container");
            } catch (Exception e) {
                return ConditionEvaluationResult.disabled(
                        "Skipping FirestoreEmulatorSmokeTest: No FIRESTORE_EMULATOR_HOST and Docker is unavailable: " + e.getMessage()
                );
            }
        }
    }
}

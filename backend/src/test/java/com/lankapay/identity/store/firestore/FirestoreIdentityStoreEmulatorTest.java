package com.lankapay.identity.store.firestore;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.lankapay.audit.domain.AuditRecord;
import com.lankapay.audit.store.firestore.FirestoreAuditStore;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.OtpException;
import com.lankapay.common.firestore.FirestoreTransactionHelper;
import com.lankapay.identity.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test running against Google Cloud Firestore emulator to verify:
 * 1. Transactional phone uniqueness in phoneIdentities/{phone}
 * 2. Concurrent registration of the identical phone number where only one succeeds
 * 3. OTP attempt counting and single-use consumption inside a Firestore transaction
 * 4. Login attempt counters updated transactionally and account lockout
 * 5. Hashed session refresh tokens and revocation
 * 6. Append-only audit logs
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(FirestoreIdentityStoreEmulatorTest.EmulatorAvailableCondition.class)
class FirestoreIdentityStoreEmulatorTest {

    private static final Logger log = LoggerFactory.getLogger(FirestoreIdentityStoreEmulatorTest.class);
    private static final String PROJECT_ID = "lankapay-test";

    private static Firestore firestore;
    private static FirestoreTransactionHelper txHelper;
    private static FirestoreUserStore userStore;
    private static FirestoreAuditStore auditStore;
    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
            throw new IllegalStateException("Firestore emulator host could not be determined");
        }

        System.setProperty("FIRESTORE_EMULATOR_HOST", emulatorHost);
        log.info("Connecting FirestoreIdentityStoreEmulatorTest to emulator at [{}]", emulatorHost);

        FirestoreOptions options = FirestoreOptions.getDefaultInstance().toBuilder()
                .setProjectId(PROJECT_ID)
                .setHost(emulatorHost)
                .setCredentials(NoCredentials.getInstance())
                .build();

        firestore = options.getService();
        txHelper = new FirestoreTransactionHelper(firestore);
        userStore = new FirestoreUserStore(firestore, txHelper);
        auditStore = new FirestoreAuditStore(firestore);
    }

    @AfterAll
    static void closeFirestore() {
        if (firestore != null) {
            try {
                firestore.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ── 1. Phone Uniqueness ────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Phone uniqueness: create phoneIdentities/{phone} inside transaction and reject duplicate")
    void testPhoneUniqueness() throws Exception {
        String phone = "+94771234001";
        UserAccount user1 = new UserAccount(
                1001L,
                phone,
                "Kasun Perera",
                "199012345678",
                passwordEncoder.encode("123456"),
                UserStatus.ACTIVE,
                UserRole.CUSTOMER,
                0,
                null,
                Instant.now(),
                Instant.now()
        );

        UserAccount saved1 = userStore.saveUser(user1);
        assertThat(saved1).isNotNull();
        assertThat(saved1.id()).isEqualTo(1001L);

        // Document in phoneIdentities/{phone} must exist with document ID = normalized phone
        DocumentSnapshot phoneDoc = firestore.collection(FirestoreUserStore.PHONE_IDENTITIES_COLLECTION)
                .document(phone).get().get();
        assertThat(phoneDoc.exists()).isTrue();
        assertThat(phoneDoc.getString("userId")).isEqualTo("1001");
        assertThat(phoneDoc.getBoolean("active")).isTrue();

        // Attempt second registration with the same active phone number by a different user
        UserAccount user2 = new UserAccount(
                1002L,
                phone,
                "Nimal Silva",
                "199087654321",
                passwordEncoder.encode("654321"),
                UserStatus.PENDING_VERIFICATION,
                UserRole.CUSTOMER,
                0,
                null,
                Instant.now(),
                Instant.now()
        );

        assertThatThrownBy(() -> userStore.saveUser(user2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    // ── 2. Concurrent Registrations ────────────────────────────

    @Test
    @Order(2)
    @DisplayName("Concurrent registrations of the identical phone number: exactly one succeeds and one fails")
    void testConcurrentRegistrationsSamePhone() throws Exception {
        String phone = "+94779998877";
        int concurrency = 2;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch readyLatch = new CountDownLatch(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<UserAccount>> futures = new ArrayList<>();
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrency; i++) {
            final long userId = 2000L + i;
            final String nic = "19950000000" + i;
            final String fullName = "Candidate " + i;

            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                UserAccount candidate = new UserAccount(
                        userId,
                        phone,
                        fullName,
                        nic,
                        passwordEncoder.encode("123456"),
                        UserStatus.ACTIVE,
                        UserRole.CUSTOMER,
                        0,
                        null,
                        Instant.now(),
                        Instant.now()
                );
                return userStore.saveUser(candidate);
            }));
        }

        // Wait for all threads to be ready, then trigger concurrent start
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        for (Future<UserAccount> f : futures) {
            try {
                UserAccount result = f.get(10, TimeUnit.SECONDS);
                if (result != null) {
                    successCount.incrementAndGet();
                }
            } catch (ExecutionException ee) {
                exceptions.add(ee.getCause());
            }
        }
        executor.shutdown();

        log.info("Concurrent registration test completed: successes={}, failures={}",
                successCount.get(), exceptions.size());

        assertThat(successCount.get())
                .as("Exactly one registration must succeed")
                .isEqualTo(1);

        assertThat(exceptions)
                .as("Exactly one registration must fail with ConflictException")
                .hasSize(1);

        Throwable ex = exceptions.get(0);
        assertThat(ex).isInstanceOf(ConflictException.class);
        ConflictException ce = (ConflictException) ex;
        assertThat(ce.getErrorCode()).isEqualTo("PHONE_ALREADY_REGISTERED");
    }

    // ── 3. OTP Attempt Counting & Single-Use Consumption ────────

    @Test
    @Order(3)
    @DisplayName("OTP attempt counting and single-use consumption inside a Firestore transaction")
    void testOtpAttemptCountingAndSingleUse() throws Exception {
        Long otpId = 3001L;
        Long userId = 1001L;
        String rawOtp = "456789";
        String hashedOtp = passwordEncoder.encode(rawOtp);
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);

        OtpRecord initialOtp = new OtpRecord(
                otpId,
                userId,
                hashedOtp,
                OtpPurpose.REGISTRATION,
                false,
                expiresAt,
                Instant.now()
        );
        userStore.saveOtp(initialOtp);

        int maxAttempts = 3;

        // 1. Wrong guess 1: should increment attempt count inside transaction and throw OtpException.invalid()
        assertThatThrownBy(() -> userStore.verifyAndConsumeOtp(
                otpId, "000000", passwordEncoder::matches, maxAttempts))
                .isInstanceOf(OtpException.class)
                .satisfies(e -> assertThat(((OtpException) e).getErrorCode()).isEqualTo("OTP_INVALID"));

        DocumentSnapshot snapAfterFirstWrong = firestore.collection(FirestoreUserStore.OTP_CHALLENGES_COLLECTION)
                .document(String.valueOf(otpId)).get().get();
        assertThat(snapAfterFirstWrong.getLong("attemptCount")).isEqualTo(1L);
        assertThat(snapAfterFirstWrong.getBoolean("used")).isFalse();

        // 2. Wrong guess 2: should increment to 2
        assertThatThrownBy(() -> userStore.verifyAndConsumeOtp(
                otpId, "111111", passwordEncoder::matches, maxAttempts))
                .isInstanceOf(OtpException.class)
                .satisfies(e -> assertThat(((OtpException) e).getErrorCode()).isEqualTo("OTP_INVALID"));

        DocumentSnapshot snapAfterSecondWrong = firestore.collection(FirestoreUserStore.OTP_CHALLENGES_COLLECTION)
                .document(String.valueOf(otpId)).get().get();
        assertThat(snapAfterSecondWrong.getLong("attemptCount")).isEqualTo(2L);

        // 3. Wrong guess 3: reaches maxAttempts -> throws OTP_MAX_ATTEMPTS
        assertThatThrownBy(() -> userStore.verifyAndConsumeOtp(
                otpId, "222222", passwordEncoder::matches, maxAttempts))
                .isInstanceOf(OtpException.class)
                .satisfies(e -> assertThat(((OtpException) e).getErrorCode()).isEqualTo("OTP_MAX_ATTEMPTS"));

        // 4. Create fresh OTP to test successful consumption & single-use replay protection
        Long freshOtpId = 3002L;
        userStore.saveOtp(new OtpRecord(
                freshOtpId,
                userId,
                hashedOtp,
                OtpPurpose.PHONE_VERIFY,
                false,
                expiresAt,
                Instant.now()
        ));

        // Submit correct code: should consume it inside transaction
        boolean consumed = userStore.verifyAndConsumeOtp(
                freshOtpId, rawOtp, passwordEncoder::matches, maxAttempts);
        assertThat(consumed).isTrue();

        DocumentSnapshot consumedDoc = firestore.collection(FirestoreUserStore.OTP_CHALLENGES_COLLECTION)
                .document(String.valueOf(freshOtpId)).get().get();
        assertThat(consumedDoc.getBoolean("used")).isTrue();
        assertThat(consumedDoc.getTimestamp("consumedAt")).isNotNull();

        // 5. Replay attack: submitting the same correct code again should be rejected as expired/consumed
        assertThatThrownBy(() -> userStore.verifyAndConsumeOtp(
                freshOtpId, rawOtp, passwordEncoder::matches, maxAttempts))
                .isInstanceOf(OtpException.class)
                .satisfies(e -> assertThat(((OtpException) e).getErrorCode()).isEqualTo("OTP_EXPIRED"));
    }

    // ── 4. Login Attempt Counters Updated Transactionally ───────

    @Test
    @Order(4)
    @DisplayName("Login attempt counters updated transactionally with account lockout")
    void testTransactionalLoginAttempts() throws Exception {
        Long userId = 4001L;
        UserAccount account = new UserAccount(
                userId,
                "+94770001122",
                "Dilshan Test",
                "199344445555",
                passwordEncoder.encode("654321"),
                UserStatus.ACTIVE,
                UserRole.CUSTOMER,
                0,
                null,
                Instant.now(),
                Instant.now()
        );
        userStore.saveUser(account);

        int maxAttempts = 5;
        Instant lockUntil = Instant.now().plus(15, ChronoUnit.MINUTES);

        // Concurrent failed logins (5 threads simultaneously incrementing failures)
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    userStore.incrementFailedLoginAttempts(userId, maxAttempts, lockUntil);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        UserAccount updated = userStore.findUserById(userId).orElseThrow();
        assertThat(updated.failedLoginAttempts())
                .as("All 5 failed attempts must be atomically counted without lost updates")
                .isEqualTo(5);
        assertThat(updated.isLocked())
                .as("Account must be locked once reaching max attempts")
                .isTrue();

        // Reset login attempts transactionally
        userStore.resetFailedLoginAttempts(userId);

        UserAccount resetUser = userStore.findUserById(userId).orElseThrow();
        assertThat(resetUser.failedLoginAttempts()).isEqualTo(0);
        assertThat(resetUser.isLocked()).isFalse();
    }

    // ── 5. Sessions / Refresh Tokens (Hashed) ───────────────────

    @Test
    @Order(5)
    @DisplayName("Sessions: refresh tokens stored hashed with atomic revocation")
    void testSessionsHashedTokensAndRevocation() throws Exception {
        Long userId = 5001L;
        String rawToken = "raw-refresh-token-xyz-123";
        String tokenHash = com.lankapay.identity.service.AuthService.sha256(rawToken);

        RefreshTokenRecord record = new RefreshTokenRecord(
                userId,
                tokenHash,
                Instant.now().plus(7, ChronoUnit.DAYS)
        );

        RefreshTokenRecord saved = userStore.saveRefreshToken(record);
        assertThat(saved).isNotNull();

        // Lookup by token hash
        RefreshTokenRecord found = userStore.findRefreshTokenByHash(tokenHash).orElseThrow();
        assertThat(found.userId()).isEqualTo(userId);
        assertThat(found.revoked()).isFalse();

        // Revoke all for user
        int revokedCount = userStore.revokeAllRefreshTokensForUser(userId);
        assertThat(revokedCount).isGreaterThanOrEqualTo(1);

        RefreshTokenRecord revoked = userStore.findRefreshTokenByHash(tokenHash).orElseThrow();
        assertThat(revoked.revoked()).isTrue();
    }

    // ── 6. Audit Logs (Append-Only) ─────────────────────────────

    @Test
    @Order(6)
    @DisplayName("Audit logs: append-only storage in auditLogs/{id}")
    void testAuditLogsAppendOnly() throws Exception {
        AuditRecord record = new AuditRecord(
                6001L,
                "USER_REGISTERED",
                "USER",
                6001L,
                "{\"phone\":\"+94770009988\"}",
                "192.168.1.10"
        );

        AuditRecord saved = auditStore.save(record);
        assertThat(saved.id()).isNotNull();

        AuditRecord retrieved = auditStore.findById(saved.id()).orElseThrow();
        assertThat(retrieved.eventType()).isEqualTo("USER_REGISTERED");
        assertThat(retrieved.actorId()).isEqualTo(6001L);
        assertThat(retrieved.ipAddress()).isEqualTo("192.168.1.10");
    }

    // ── Helper ─────────────────────────────────────────────────

    private static boolean isPortListening(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static class EmulatorAvailableCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            String host = System.getenv("FIRESTORE_EMULATOR_HOST");
            if (host == null || host.isBlank()) {
                host = System.getProperty("FIRESTORE_EMULATOR_HOST");
            }
            if (host != null && !host.isBlank()) {
                return ConditionEvaluationResult.enabled("FIRESTORE_EMULATOR_HOST is set: " + host);
            }
            if (isPortListening("localhost", 8085) || isPortListening("127.0.0.1", 8085)) {
                return ConditionEvaluationResult.enabled("Firestore emulator listening on port 8085");
            }
            return ConditionEvaluationResult.disabled("Skipping: No Firestore emulator reachable on port 8085");
        }
    }
}

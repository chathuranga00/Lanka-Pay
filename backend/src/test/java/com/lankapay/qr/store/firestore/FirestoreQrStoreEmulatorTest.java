package com.lankapay.qr.store.firestore;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.store.firestore.FirestoreUserStore;
import com.lankapay.qr.domain.*;
import com.lankapay.qr.service.PaymentRequestService;
import com.lankapay.qr.service.QrService;
import com.lankapay.qr.web.dto.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test suite for QR references and payment requests against Firestore emulator.
 *
 * Verifies:
 * 1. qrReferences collection stores opaque references (document ID = opaque reference).
 * 2. Dynamic QR amount, expiry, and one-time use invariants.
 * 3. Static QR reusability.
 * 4. Scan only resolves recipient (no money moved on scan).
 * 5. QR expiry rejection & automatic EXPIRED status transition.
 * 6. Tampered or unknown reference rejection (404 ResourceNotFound).
 * 7. Dynamic one-time QR reuse rejection (409 Conflict).
 * 8. Payment request creation with linked dynamic QR and unguessable reference.
 * 9. Single-use payment request consumed in a transaction (cannot be consumed twice).
 * 10. Expired payment request cannot be fulfilled.
 * 11. Object-level authorization for payment requests (requester and payer allowed, third party rejected).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(FirestoreQrStoreEmulatorTest.EmulatorAvailableCondition.class)
class FirestoreQrStoreEmulatorTest {

    private static final Logger log = LoggerFactory.getLogger(FirestoreQrStoreEmulatorTest.class);
    private static final String PROJECT_ID = "lankapay-test";

    private static Firestore firestore;
    private static FirestoreQrStore qrStore;
    private static FirestoreUserStore userStore;
    private static QrService qrService;
    private static PaymentRequestService paymentRequestService;

    private static User merchantUser;
    private static User payerUser;
    private static User thirdPartyUser;

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
        com.lankapay.common.firestore.FirestoreTransactionHelper txHelper =
                new com.lankapay.common.firestore.FirestoreTransactionHelper(firestore);
        qrStore = new FirestoreQrStore(firestore);
        userStore = new FirestoreUserStore(firestore, txHelper);

        qrService = new QrService(qrStore, userStore);
        paymentRequestService = new PaymentRequestService(qrStore, userStore);

        long suffix = System.currentTimeMillis() % 1000000L;

        merchantUser = new User("+9477" + (1000000 + suffix), "Merchant " + suffix, "1990" + (10000000 + suffix), "hash");
        merchantUser.setId(100000L + suffix);
        merchantUser.setStatus(UserStatus.ACTIVE);
        merchantUser.setRole(UserRole.MERCHANT);
        userStore.saveUser(new UserAccount(
                merchantUser.getId(), merchantUser.getPhone(), merchantUser.getFullName(),
                merchantUser.getNicNumber(), merchantUser.getPasswordHash(),
                UserStatus.ACTIVE, UserRole.MERCHANT, 0, null, Instant.now(), Instant.now()
        ));

        payerUser = new User("+9477" + (2000000 + suffix), "Payer " + suffix, "1992" + (10000000 + suffix), "hash");
        payerUser.setId(200000L + suffix);
        payerUser.setStatus(UserStatus.ACTIVE);
        payerUser.setRole(UserRole.CUSTOMER);
        userStore.saveUser(new UserAccount(
                payerUser.getId(), payerUser.getPhone(), payerUser.getFullName(),
                payerUser.getNicNumber(), payerUser.getPasswordHash(),
                UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now()
        ));

        thirdPartyUser = new User("+9477" + (3000000 + suffix), "Third Party " + suffix, "1995" + (10000000 + suffix), "hash");
        thirdPartyUser.setId(300000L + suffix);
        thirdPartyUser.setStatus(UserStatus.ACTIVE);
        thirdPartyUser.setRole(UserRole.CUSTOMER);
        userStore.saveUser(new UserAccount(
                thirdPartyUser.getId(), thirdPartyUser.getPhone(), thirdPartyUser.getFullName(),
                thirdPartyUser.getNicNumber(), thirdPartyUser.getPasswordHash(),
                UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now()
        ));
    }

    @Test
    @Order(1)
    @DisplayName("1. Create dynamic QR: stored with opaque reference as document ID, amount in minor units, no PII")
    void testCreateDynamicQr() throws Exception {
        CreateQrReferenceRequest req = new CreateQrReferenceRequest(
                QrType.DYNAMIC, new BigDecimal("1750.50"), "LKR", "Table 12 lunch", 15);

        QrReferenceResponse response = qrService.createQrReference(req, merchantUser);

        assertThat(response.reference()).startsWith("QREF-");
        assertThat(response.qrType()).isEqualTo(QrType.DYNAMIC);
        assertThat(response.amount()).isEqualByComparingTo("1750.50");
        assertThat(response.currency()).isEqualTo("LKR");
        assertThat(response.oneTimeUse()).isTrue();
        assertThat(response.status()).isEqualTo(QrStatus.ACTIVE);
        assertThat(response.expiresAt()).isAfter(Instant.now());

        // Invariant: QR payload contains no PII, no account numbers, no auth tokens
        assertThat(response.qrPayload()).isEqualTo("LANKAPAY:QR:" + response.reference());
        assertThat(response.qrPayload()).doesNotContain(merchantUser.getNicNumber());
        assertThat(response.qrPayload()).doesNotContain("bearer");

        // Verify direct Firestore document lookup by opaque reference
        DocumentSnapshot doc = firestore.collection(FirestoreQrStore.QR_REFERENCES_COLLECTION)
                .document(response.reference())
                .get()
                .get();

        assertThat(doc.exists()).isTrue();
        assertThat(doc.getId()).isEqualTo(response.reference());
        assertThat(doc.getLong("amountMinor")).isEqualTo(175050L);
        assertThat(doc.getString("currency")).isEqualTo("LKR");
        assertThat(doc.getString("status")).isEqualTo("ACTIVE");
    }

    @Test
    @Order(2)
    @DisplayName("2. Create static QR: reusable, indefinite validity, document ID = opaque reference")
    void testCreateStaticQr() throws Exception {
        CreateQrReferenceRequest req = new CreateQrReferenceRequest(
                QrType.STATIC, null, "LKR", "Counter stand", null);

        QrReferenceResponse response = qrService.createQrReference(req, merchantUser);

        assertThat(response.reference()).startsWith("QREF-STATIC-");
        assertThat(response.qrType()).isEqualTo(QrType.STATIC);
        assertThat(response.amount()).isNull();
        assertThat(response.expiresAt()).isNull();
        assertThat(response.oneTimeUse()).isFalse();
        assertThat(response.status()).isEqualTo(QrStatus.ACTIVE);

        DocumentSnapshot doc = firestore.collection(FirestoreQrStore.QR_REFERENCES_COLLECTION)
                .document(response.reference())
                .get()
                .get();

        assertThat(doc.exists()).isTrue();
        assertThat(doc.getId()).isEqualTo(response.reference());
        assertThat(doc.getBoolean("oneTimeUse")).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("3. Scan only resolves recipient: funds are never moved upon scanning")
    void testScanResolvesRecipient() {
        CreateQrReferenceRequest req = new CreateQrReferenceRequest(
                QrType.DYNAMIC, new BigDecimal("3200.00"), "LKR", "Dinner bill", 30);
        QrReferenceResponse created = qrService.createQrReference(req, merchantUser);

        ResolvedQrRecipientResponse resolved = qrService.resolveQrReference(created.reference());

        assertThat(resolved.recipientId()).isEqualTo(merchantUser.getId());
        assertThat(resolved.recipientDisplayName()).isEqualTo(merchantUser.getFullName());
        assertThat(resolved.amount()).isEqualByComparingTo("3200.00");
        assertThat(resolved.status()).isEqualTo(QrStatus.ACTIVE);
        assertThat(resolved.message()).contains("Recipient resolved successfully");

        // Document status remains ACTIVE (scan does not consume funds)
        var qrOpt = qrStore.findQrByReference(created.reference());
        assertThat(qrOpt).isPresent();
        assertThat(qrOpt.get().status()).isEqualTo(QrStatus.ACTIVE);
    }

    @Test
    @Order(4)
    @DisplayName("4. QR Expiry test: scanning expired QR throws QR_EXPIRED and transitions status to EXPIRED")
    void testQrExpiryRejection() {
        Instant past = Instant.now().minus(10, ChronoUnit.MINUTES);
        String ref = "QREF-EXP-" + UUID.randomUUID();
        QrCodeRecord expiredRecord = new QrCodeRecord(
                null, merchantUser.getId(), "QR-EXP-1", ref, QrType.DYNAMIC,
                new BigDecimal("500.00"), "LKR", "Expired snack", QrStatus.ACTIVE,
                true, "LANKAPAY:QR:" + ref, past, past.minus(1, ChronoUnit.HOURS)
        );
        qrStore.saveQr(expiredRecord);

        assertThatThrownBy(() -> qrService.resolveQrReference(ref))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("QR code has expired");

        // Verify status in Firestore updated to EXPIRED
        var qrOpt = qrStore.findQrByReference(ref);
        assertThat(qrOpt).isPresent();
        assertThat(qrOpt.get().status()).isEqualTo(QrStatus.EXPIRED);
    }

    @Test
    @Order(5)
    @DisplayName("5. Tampered reference test: unknown or manipulated reference throws QR_NOT_FOUND")
    void testTamperedReferenceNotFound() {
        String tamperedRef = "QREF-TAMPERED-" + UUID.randomUUID();

        assertThatThrownBy(() -> qrService.resolveQrReference(tamperedRef))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("QR reference is invalid or unknown");
    }

    @Test
    @Order(6)
    @DisplayName("6. Dynamic QR reuse test: one-time QR already marked USED throws QR_ALREADY_USED")
    void testDynamicQrReuseRejected() {
        CreateQrReferenceRequest req = new CreateQrReferenceRequest(
                QrType.DYNAMIC, new BigDecimal("800.00"), "LKR", "Coffee", 20);
        QrReferenceResponse created = qrService.createQrReference(req, merchantUser);

        // Mark as used after simulated payment
        qrService.markQrUsed(created.reference());

        // Second resolution attempt must be rejected
        assertThatThrownBy(() -> qrService.resolveQrReference(created.reference()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("one-time QR code has already been used");
    }

    @Test
    @Order(7)
    @DisplayName("7. Static QR reusability: static QR can be scanned multiple times without becoming USED")
    void testStaticQrMultipleScansAllowed() {
        CreateQrReferenceRequest req = new CreateQrReferenceRequest(
                QrType.STATIC, null, "LKR", "Shop tip jar", null);
        QrReferenceResponse created = qrService.createQrReference(req, merchantUser);

        ResolvedQrRecipientResponse scan1 = qrService.resolveQrReference(created.reference());
        ResolvedQrRecipientResponse scan2 = qrService.resolveQrReference(created.reference());

        assertThat(scan1.status()).isEqualTo(QrStatus.ACTIVE);
        assertThat(scan2.status()).isEqualTo(QrStatus.ACTIVE);

        var qrOpt = qrStore.findQrByReference(created.reference());
        assertThat(qrOpt).isPresent();
        assertThat(qrOpt.get().status()).isEqualTo(QrStatus.ACTIVE);
    }

    @Test
    @Order(8)
    @DisplayName("8. Create payment request: unguessable PREQ- reference and linked dynamic QR")
    void testCreatePaymentRequest() {
        CreatePaymentRequestDto dto = new CreatePaymentRequestDto(
                new BigDecimal("4500.00"), "LKR", payerUser.getId(), null, "Invoice #9012", 20);

        PaymentRequestResponse response = paymentRequestService.createPaymentRequest(dto, merchantUser);

        assertThat(response.reference()).startsWith("PREQ-");
        assertThat(response.requesterId()).isEqualTo(merchantUser.getId());
        assertThat(response.payerId()).isEqualTo(payerUser.getId());
        assertThat(response.amount()).isEqualByComparingTo("4500.00");
        assertThat(response.status()).isEqualTo(PaymentRequestStatus.PENDING);
        assertThat(response.qrReference()).startsWith("QREF-");
        assertThat(response.qrPayload()).isEqualTo("LANKAPAY:QR:" + response.qrReference());
    }

    @Test
    @Order(9)
    @DisplayName("9. Single-use payment request consumed in a transaction: reuse attempt rejected with 409 Conflict")
    void testSingleUsePaymentRequestConsumedInTransaction() {
        CreatePaymentRequestDto dto = new CreatePaymentRequestDto(
                new BigDecimal("2100.00"), "LKR", payerUser.getId(), null, "Service fee", 30);
        PaymentRequestResponse created = paymentRequestService.createPaymentRequest(dto, merchantUser);

        // First consumption inside transaction succeeds
        Long transferId = 987654321L;
        PaymentRequestRecord consumed = qrStore.consumePaymentRequest(created.reference(), transferId);

        assertThat(consumed.status()).isEqualTo(PaymentRequestStatus.COMPLETED);
        assertThat(consumed.fulfilledTransferId()).isEqualTo(transferId);

        // Linked dynamic QR is marked USED
        var qrOpt = qrStore.findQrByReference(created.qrReference());
        assertThat(qrOpt).isPresent();
        assertThat(qrOpt.get().status()).isEqualTo(QrStatus.USED);

        // Replay/reuse attempt of the single-use request must be rejected with 409 Conflict
        assertThatThrownBy(() -> qrStore.consumePaymentRequest(created.reference(), 987654322L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Payment request has already been fulfilled");
    }

    @Test
    @Order(10)
    @DisplayName("10. Expired payment request cannot be fulfilled: throws PAYMENT_REQUEST_EXPIRED")
    void testExpiredPaymentRequestCannotBeConsumed() {
        Instant past = Instant.now().minus(15, ChronoUnit.MINUTES);
        String ref = "PREQ-EXP-" + UUID.randomUUID();
        PaymentRequestRecord expiredReq = new PaymentRequestRecord(
                null, merchantUser.getId(), payerUser.getId(), null, ref,
                new BigDecimal("1500.00"), "LKR", "Expired invoice",
                PaymentRequestStatus.PENDING, past, null, past.minus(1, ChronoUnit.HOURS)
        );
        qrStore.savePaymentRequest(expiredReq);

        assertThatThrownBy(() -> qrStore.consumePaymentRequest(ref, 12345L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Payment request has expired");

        var checkOpt = qrStore.findPaymentRequestByReference(ref);
        assertThat(checkOpt).isPresent();
        assertThat(checkOpt.get().status()).isEqualTo(PaymentRequestStatus.EXPIRED);
    }

    @Test
    @Order(11)
    @DisplayName("11. Object-level authorization: requester and payer can view; third-party rejected with 403 Forbidden")
    void testPaymentRequestObjectLevelAuthorization() {
        CreatePaymentRequestDto dto = new CreatePaymentRequestDto(
                new BigDecimal("750.00"), "LKR", payerUser.getId(), null, "Private bill", 25);
        PaymentRequestResponse created = paymentRequestService.createPaymentRequest(dto, merchantUser);

        // Requester access allowed
        PaymentRequestResponse reqView = paymentRequestService.getPaymentRequest(created.id(), merchantUser.getId());
        assertThat(reqView.id()).isEqualTo(created.id());

        // Payer access allowed
        PaymentRequestResponse payerView = paymentRequestService.getPaymentRequest(created.id(), payerUser.getId());
        assertThat(payerView.id()).isEqualTo(created.id());

        // Third-party user rejected with 403 Forbidden
        assertThatThrownBy(() -> paymentRequestService.getPaymentRequest(created.id(), thirdPartyUser.getId()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("You do not have authorization to view this payment request");
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

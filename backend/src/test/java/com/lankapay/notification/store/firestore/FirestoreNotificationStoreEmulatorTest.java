package com.lankapay.notification.store.firestore;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.lankapay.audit.service.AuditService;
import com.lankapay.audit.store.firestore.FirestoreAuditStore;
import com.lankapay.bank.adapter.*;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.store.firestore.FirestoreLinkedAccountStore;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.store.firestore.FirestoreUserStore;
import com.lankapay.notification.channel.NotificationChannel;
import com.lankapay.notification.domain.*;
import com.lankapay.notification.service.NotificationMessageFormatter;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.domain.TransferInitiatedVia;
import com.lankapay.transfer.service.DailyLimitService;
import com.lankapay.transfer.service.TransferProcessor;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test suite for notifications and preferences against Firestore emulator.
 *
 * Verifies:
 * 1. Notification persistence in notifications collection (id, userId, channel, type, status).
 * 2. Mark read, mark all read, and unread count queries.
 * 3. Notification preferences in notificationPreferences collection.
 * 4. Critical invariant: Notification delivery failure does NOT affect payment outcome.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(FirestoreNotificationStoreEmulatorTest.EmulatorAvailableCondition.class)
class FirestoreNotificationStoreEmulatorTest {

    private static final Logger log = LoggerFactory.getLogger(FirestoreNotificationStoreEmulatorTest.class);
    private static final String PROJECT_ID = "lankapay-test";

    private static Firestore firestore;
    private static FirestoreNotificationStore notificationStore;
    private static FirestoreUserStore userStore;
    private static FirestoreAuditStore auditStore;
    private static FirestoreTransactionStore transactionStore;
    private static FirestoreLinkedAccountStore linkedAccountStore;

    private static AuditService auditService;
    private static DailyLimitService dailyLimitService;
    private static NotificationService notificationService;
    private static BankAdapter mockBankAdapter;
    private static BankAdapterRegistry adapterRegistry;
    private static TransferProcessor transferProcessor;

    private static User senderUser;
    private static User receiverUser;
    private static BankAccount senderAccount;
    private static BankAccount receiverAccount;

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
        notificationStore = new FirestoreNotificationStore(firestore);
        userStore = new FirestoreUserStore(firestore, txHelper);
        auditStore = new FirestoreAuditStore(firestore);
        transactionStore = new FirestoreTransactionStore(firestore);
        linkedAccountStore = new FirestoreLinkedAccountStore(firestore, txHelper);

        auditService = new AuditService(auditStore);
        dailyLimitService = new DailyLimitService(transactionStore);

        long suffix = System.currentTimeMillis() % 1000000L;

        senderUser = new User("+9477" + (4000000 + suffix), "Sender " + suffix, "1991" + (10000000 + suffix), "hash");
        senderUser.setId(400000L + suffix);
        senderUser.setStatus(UserStatus.ACTIVE);
        senderUser.setRole(UserRole.CUSTOMER);
        userStore.saveUser(new UserAccount(
                senderUser.getId(), senderUser.getPhone(), senderUser.getFullName(),
                senderUser.getNicNumber(), senderUser.getPasswordHash(),
                UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now()
        ));

        receiverUser = new User("+9477" + (5000000 + suffix), "Receiver " + suffix, "1993" + (10000000 + suffix), "hash");
        receiverUser.setId(500000L + suffix);
        receiverUser.setStatus(UserStatus.ACTIVE);
        receiverUser.setRole(UserRole.CUSTOMER);
        userStore.saveUser(new UserAccount(
                receiverUser.getId(), receiverUser.getPhone(), receiverUser.getFullName(),
                receiverUser.getNicNumber(), receiverUser.getPasswordHash(),
                UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now()
        ));

        senderAccount = mock(BankAccount.class);
        when(senderAccount.getBankCode()).thenReturn("BOC");
        when(senderAccount.getMaskedNumber()).thenReturn("****1234");

        receiverAccount = mock(BankAccount.class);
        when(receiverAccount.getBankCode()).thenReturn("COMB");
        when(receiverAccount.getMaskedNumber()).thenReturn("****5678");

        mockBankAdapter = mock(BankAdapter.class);
        when(mockBankAdapter.checkTransferEligibility(any())).thenReturn(
                AdapterResponse.success("ELIGIBLE-REF", "ELIGIBLE")
        );
        when(mockBankAdapter.initiateTransfer(any())).thenReturn(
                AdapterResponse.success("SIM-BANK-REF-99", "PROVIDER-REF-99")
        );

        adapterRegistry = mock(BankAdapterRegistry.class);
        when(adapterRegistry.get("BOC")).thenReturn(mockBankAdapter);

        // Register a failing notification channel to simulate external delivery outages
        NotificationChannel failingChannel = new NotificationChannel() {
            @Override
            public NotificationChannelType getChannelType() {
                return NotificationChannelType.PUSH;
            }

            @Override
            public void deliver(Notification notification, User user) {
                throw new RuntimeException("Push Gateway Error 504 Gateway Timeout: connection reset by peer");
            }
        };

        notificationService = new NotificationService(
                notificationStore,
                new NotificationMessageFormatter(),
                List.of(failingChannel)
        );

        transferProcessor = new TransferProcessor(
                transactionStore,
                adapterRegistry,
                dailyLimitService,
                auditService,
                notificationService
        );
    }

    @Test
    @Order(1)
    @DisplayName("1. Notification persistence in Firestore: save, query by userId, verify fields")
    void testNotificationPersistence() {
        NotificationRecord rec = new NotificationRecord(
                null, senderUser.getId(), NotificationChannelType.IN_APP,
                NotificationType.PAYMENT_SENT, "Payment Sent", "You sent 2500.00 LKR",
                NotificationStatus.SENT, null, false, null, Instant.now(), false
        );

        NotificationRecord saved = notificationStore.saveNotification(rec);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.userId()).isEqualTo(senderUser.getId());
        assertThat(saved.title()).isEqualTo("Payment Sent");
        assertThat(saved.status()).isEqualTo(NotificationStatus.SENT);

        Optional<NotificationRecord> found = notificationStore.findNotificationByIdAndUserId(saved.id(), senderUser.getId());
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Payment Sent");
    }

    @Test
    @Order(2)
    @DisplayName("2. Mark notification read & mark all read & count unread")
    void testNotificationMarkReadAndUnreadCount() {
        NotificationRecord n1 = notificationStore.saveNotification(new NotificationRecord(
                null, senderUser.getId(), NotificationChannelType.IN_APP,
                NotificationType.PAYMENT_RECEIVED, "Payment Received", "Received payment",
                NotificationStatus.SENT, null, false, null, Instant.now(), false
        ));
        NotificationRecord n2 = notificationStore.saveNotification(new NotificationRecord(
                null, senderUser.getId(), NotificationChannelType.IN_APP,
                NotificationType.SECURITY_ALERT, "Security Alert", "New login",
                NotificationStatus.SENT, null, false, null, Instant.now(), false
        ));

        long unreadBefore = notificationStore.countUnreadNotifications(senderUser.getId());
        assertThat(unreadBefore).isGreaterThanOrEqualTo(2L);

        // Mark individual notification read
        notificationStore.saveNotification(n1.markRead(Instant.now()));
        Optional<NotificationRecord> checked = notificationStore.findNotificationByIdAndUserId(n1.id(), senderUser.getId());
        assertThat(checked).isPresent();
        assertThat(checked.get().read()).isTrue();

        // Mark all as read
        int updated = notificationStore.markAllNotificationsAsReadForUser(senderUser.getId(), Instant.now());
        assertThat(updated).isGreaterThanOrEqualTo(1);

        long unreadAfter = notificationStore.countUnreadNotifications(senderUser.getId());
        assertThat(unreadAfter).isEqualTo(0L);
    }

    @Test
    @Order(3)
    @DisplayName("3. Notification preferences: save and retrieve in notificationPreferences collection")
    void testNotificationPreferences() {
        NotificationPreferenceRecord defaultPref = NotificationPreferenceRecord.defaultForUser(receiverUser.getId());
        notificationStore.savePreference(defaultPref);

        Optional<NotificationPreferenceRecord> fetched = notificationStore.findPreferenceByUserId(receiverUser.getId());
        assertThat(fetched).isPresent();
        assertThat(fetched.get().inAppEnabled()).isTrue();
        assertThat(fetched.get().pushEnabled()).isTrue();
        assertThat(fetched.get().paymentAlerts()).isTrue();

        // Update preferences
        NotificationPreferenceRecord updated = fetched.get().update(
                true, false, true, false, true, true
        );
        notificationStore.savePreference(updated);

        Optional<NotificationPreferenceRecord> reFetched = notificationStore.findPreferenceByUserId(receiverUser.getId());
        assertThat(reFetched).isPresent();
        assertThat(reFetched.get().pushEnabled()).isFalse();
        assertThat(reFetched.get().emailEnabled()).isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("4. Notification delivery failure does NOT affect payment outcome: transfer still completes successfully")
    void testNotificationDeliveryFailureDoesNotAffectPaymentOutcome() {
        Transfer transfer = new Transfer(
                UUID.randomUUID().toString(),
                senderUser,
                receiverUser,
                new BigDecimal("3500.00"),
                "LKR",
                TransferInitiatedVia.APP
        );

        String idemKey = "IDEM-NOTIF-FAIL-" + UUID.randomUUID();
        TransactionRecord txRecord = new TransactionRecord(
                null,
                transfer.getReference(),
                idemKey,
                senderUser.getId(),
                senderUser.getPhone(),
                senderUser.getFullName(),
                receiverUser.getId(),
                receiverUser.getPhone(),
                receiverUser.getFullName(),
                "BOC",
                "COMB",
                new BigDecimal("3500.00"),
                "LKR",
                TransactionStatus.PENDING,
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
        txRecord = transactionStore.createTransferWithIdempotency(txRecord, idemKey, "HASH-123");
        transfer.setId(txRecord.id());

        // Process transfer: even though push gateway fails and throws RuntimeException,
        // TransferProcessor catches it safely and the payment outcome completes!
        Transfer result = transferProcessor.process(
                transfer, senderAccount, receiverAccount, null, "127.0.0.1");

        // 1. Invariant: Transfer status must be COMPLETED
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.getBankTxRef()).isEqualTo("SIM-BANK-REF-99");

        // 2. Transaction store confirms COMPLETED
        var storedOpt = transactionStore.findTransferById(result.getId());
        assertThat(storedOpt).isPresent();
        assertThat(storedOpt.get().status()).isEqualTo(TransactionStatus.COMPLETED);

        // 3. Daily limit spend was recorded
        BigDecimal dailySent = transactionStore.findDailyLimit(senderUser.getId(), java.time.LocalDate.now())
                .map(com.lankapay.transfer.domain.DailyLimitRecord::totalSent)
                .orElse(BigDecimal.ZERO);
        assertThat(dailySent).isGreaterThanOrEqualTo(new BigDecimal("3500.00"));

        // 4. Audit trail was written
        var auditLogs = auditStore.findByActorId(senderUser.getId(), PageRequest.of(0, 10));
        assertThat(auditLogs.getContent()).anyMatch(a -> "TRANSFER_COMPLETED".equals(a.eventType()));
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

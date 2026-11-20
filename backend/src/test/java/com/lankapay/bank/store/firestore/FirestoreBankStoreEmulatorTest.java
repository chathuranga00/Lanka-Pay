package com.lankapay.bank.store.firestore;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.QuerySnapshot;
import com.lankapay.audit.service.AuditService;
import com.lankapay.audit.store.firestore.FirestoreAuditStore;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.adapter.simulated.SimulatedAccountStore;
import com.lankapay.bank.adapter.simulated.SimulatedBankAdapterA;
import com.lankapay.bank.adapter.simulated.SimulatedBankAdapterB;
import com.lankapay.bank.adapter.simulated.SimulatedBankAdapterC;
import com.lankapay.bank.crypto.AccountReferenceEncryptor;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.service.BankAccountService;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.common.firestore.FirestoreTransactionHelper;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.store.firestore.FirestoreUserStore;
import com.lankapay.registry.domain.LookupResultType;
import com.lankapay.registry.domain.PhoneIdentity;
import com.lankapay.registry.domain.RegistryAccountRecord;
import com.lankapay.registry.service.LookupRateLimiter;
import com.lankapay.registry.service.LookupService;
import com.lankapay.registry.store.firestore.FirestorePhoneIdentityStore;
import com.lankapay.registry.web.LookupResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests running against the Google Cloud Firestore emulator to verify:
 * 1. Bank institutions pre-seeded and queryable in Firestore.
 * 2. Linked bank accounts with encrypted reference, masked number, flags, version, and consents.
 * 3. Payment registry synchronization with "accounts" subcollection safe view in SAME transaction.
 * 4. Multi-account linking per user.
 * 5. Atomic setDefaultAccount unsetting old and setting new default in ONE transaction under concurrency.
 * 6. Object-level authorization preventing cross-user account access and mutations.
 * 7. Lookup privacy exposing only masked display name and safe view without sensitive data.
 * 8. Account revocation blocking new routing while preserving document for pending transactions.
 * 9. Simulated bank accounts in collection "simulatedBankAccounts".
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(FirestoreBankStoreEmulatorTest.EmulatorAvailableCondition.class)
class FirestoreBankStoreEmulatorTest {

    private static final Logger log = LoggerFactory.getLogger(FirestoreBankStoreEmulatorTest.class);
    private static final String PROJECT_ID = "lankapay-test";

    // Valid 32-byte Base64-URL test key for AES-GCM
    private static final String TEST_KEY =
            Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    private static Firestore firestore;
    private static FirestoreTransactionHelper txHelper;
    private static FirestoreUserStore userStore;
    private static FirestorePhoneIdentityStore phoneIdentityStore;
    private static FirestoreLinkedAccountStore linkedAccountStore;
    private static FirestoreAuditStore auditStore;
    private static AuditService auditService;
    private static AccountReferenceEncryptor encryptor;
    private static SimulatedAccountStore simulatedAccountStore;
    private static BankAdapterRegistry adapterRegistry;
    private static BankAccountService bankAccountService;
    private static LookupService lookupService;

    private static UserAccount user1;
    private static UserAccount user2;
    private static BankAccount user1AccountA;
    private static BankAccount user1AccountB;

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
        log.info("Connecting FirestoreBankStoreEmulatorTest to emulator at [{}]", emulatorHost);

        FirestoreOptions options = FirestoreOptions.getDefaultInstance().toBuilder()
                .setProjectId(PROJECT_ID)
                .setHost(emulatorHost)
                .setCredentials(NoCredentials.getInstance())
                .build();

        firestore = options.getService();
        txHelper = new FirestoreTransactionHelper(firestore);

        userStore = new FirestoreUserStore(firestore, txHelper);
        phoneIdentityStore = new FirestorePhoneIdentityStore(firestore, txHelper);
        linkedAccountStore = new FirestoreLinkedAccountStore(firestore, txHelper);
        auditStore = new FirestoreAuditStore(firestore);
        auditService = new AuditService(auditStore);
        encryptor = new AccountReferenceEncryptor(TEST_KEY);

        // Simulated adapters and store backed by Firestore
        simulatedAccountStore = new SimulatedAccountStore(firestore);
        SimulatedBankAdapterA adapterA = new SimulatedBankAdapterA(simulatedAccountStore);
        SimulatedBankAdapterB adapterB = new SimulatedBankAdapterB(simulatedAccountStore);
        SimulatedBankAdapterC adapterC = new SimulatedBankAdapterC(simulatedAccountStore);
        adapterRegistry = new BankAdapterRegistry(List.of(adapterA, adapterB, adapterC));

        bankAccountService = new BankAccountService(
                linkedAccountStore,
                userStore,
                adapterRegistry,
                encryptor,
                auditService
        );

        LookupRateLimiter rateLimiter = new LookupRateLimiter(100, 100);
        lookupService = new LookupService(
                phoneIdentityStore,
                rateLimiter,
                auditService,
                50
        );

        // Pre-create test users in Firestore userStore (which transactionally creates phoneIdentities)
        long suffix = System.currentTimeMillis() % 10000000L;
        String phone1 = "+9477" + String.format("%07d", suffix);
        String nic1 = "1990" + String.format("%08d", suffix);
        String phone2 = "+9478" + String.format("%07d", suffix);
        String nic2 = "1992" + String.format("%08d", suffix);

        user1 = userStore.saveUser(new UserAccount(
                null,
                phone1,
                "Kamal Perera",
                nic1,
                "$2a$10$abcdefghijklmnopqrstuvwxyz123456",
                UserStatus.ACTIVE,
                UserRole.CUSTOMER,
                0,
                null,
                Instant.now(),
                Instant.now()
        ));

        user2 = userStore.saveUser(new UserAccount(
                null,
                phone2,
                "Nimal Silva",
                nic2,
                "$2a$10$abcdefghijklmnopqrstuvwxyz123456",
                UserStatus.ACTIVE,
                UserRole.CUSTOMER,
                0,
                null,
                Instant.now(),
                Instant.now()
        ));

        clearCollection("linkedBankAccounts");
        clearCollection("consents");
    }

    private static void clearCollection(String collectionName) {
        try {
            var docs = firestore.collection(collectionName).get().get().getDocuments();
            for (var doc : docs) {
                doc.getReference().delete().get();
            }
        } catch (Exception ignored) {
        }
    }

    @AfterAll
    static void closeFirestore() {
        if (firestore != null) {
            clearCollection("linkedBankAccounts");
            clearCollection("consents");
            try {
                firestore.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ── 1. Bank Institutions Collection ─────────────────────────

    @Test
    @Order(1)
    @DisplayName("1. Bank institutions collection seeded and queryable in Firestore")
    void testBankInstitutionsSeeded() throws Exception {
        var instOpt = linkedAccountStore.findActiveInstitutionByBankCode("SIMULATED_BANK_A");
        assertThat(instOpt).isPresent();
        assertThat(instOpt.get().displayName()).isEqualTo("Simulated Bank Alpha");
        assertThat(instOpt.get().active()).isTrue();

        DocumentSnapshot doc = firestore.collection("bankInstitutions")
                .document("SIMULATED_BANK_A").get().get();
        assertThat(doc.exists()).isTrue();
        assertThat(doc.getString("bankName")).isEqualTo("Simulated Bank Alpha");
        assertThat(doc.getBoolean("active")).isTrue();
    }

    // ── 2. Link Account & Transactional Registry Sync ───────────

    @Test
    @Order(2)
    @DisplayName("2. Link account: encrypted ref, masked number, consent, & registry subcollection sync")
    void testLinkAccount_EncryptedReference_AndRegistrySync() throws Exception {
        user1AccountA = bankAccountService.linkAccount(
                user1.id(),
                "SIMULATED_BANK_A",
                "101001",
                "Kamal Perera",
                "My Primary Account",
                true,
                "192.168.1.10"
        );

        assertThat(user1AccountA).isNotNull();
        assertThat(user1AccountA.getId()).isNotNull();
        assertThat(user1AccountA.getMaskedNumber()).isEqualTo("****1001");
        assertThat(user1AccountA.getStatus()).isEqualTo(BankAccountStatus.ACTIVE);
        // BankAccountService returns the account, but plain account number is not there
        assertThat(user1AccountA.getEncryptedAccountReference()).isNotNull();

        // Verify linkedBankAccounts in Firestore
        DocumentSnapshot accountDoc = firestore.collection("linkedBankAccounts")
                .document(String.valueOf(user1AccountA.getId())).get().get();
        assertThat(accountDoc.exists()).isTrue();
        assertThat(accountDoc.getLong("userId")).isEqualTo(user1.id());
        assertThat(accountDoc.getString("bankCode")).isEqualTo("SIMULATED_BANK_A");
        assertThat(accountDoc.getString("maskedNumber")).isEqualTo("****1001");
        assertThat(accountDoc.getLong("version")).isEqualTo(1L);
        assertThat(accountDoc.getString("encryptedAccountReference")).isNotEqualTo("101001");

        // Verify consents collection in Firestore
        QuerySnapshot consentsSnap = firestore.collection("consents")
                .whereEqualTo("userId", user1.id())
                .whereEqualTo("bankAccountId", user1AccountA.getId())
                .get().get();
        assertThat(consentsSnap.isEmpty()).isFalse();
        DocumentSnapshot consentDoc = consentsSnap.getDocuments().get(0);
        assertThat(consentDoc.getString("purpose")).isEqualTo("LINK_ACCOUNT");
        assertThat(consentDoc.getString("status")).isEqualTo("GRANTED");

        // Verify paymentRegistry/{phone}/accounts/{accountId} subcollection in Firestore
        DocumentSnapshot regAccountDoc = firestore.collection("paymentRegistry")
                .document(user1.phone())
                .collection("accounts")
                .document(String.valueOf(user1AccountA.getId()))
                .get().get();

        assertThat(regAccountDoc.exists()).isTrue();
        assertThat(regAccountDoc.getString("bankName")).isEqualTo("Simulated Bank Alpha");
        assertThat(regAccountDoc.getString("maskedNumber")).isEqualTo("****1001");
        assertThat(regAccountDoc.getBoolean("discoverable")).isTrue();
        assertThat(regAccountDoc.getBoolean("receivingEnabled")).isTrue();
        // Zero plain account numbers or encrypted references in the registry
        assertThat(regAccountDoc.getString("encryptedAccountReference")).isNull();
        assertThat(regAccountDoc.getString("accountNumber")).isNull();

        // Duplicate account linking rejection check
        assertThatThrownBy(() -> bankAccountService.linkAccount(
                user1.id(),
                "SIMULATED_BANK_A",
                "101001",
                "Kamal Perera",
                "Duplicate",
                true,
                "192.168.1.10"
        )).isInstanceOf(ConflictException.class);
    }

    // ── 3. Multi-Account Linking ────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("3. Multi-account linking per user")
    void testMultiAccountLinking() throws Exception {
        user1AccountB = bankAccountService.linkAccount(
                user1.id(),
                "SIMULATED_BANK_B",
                "201001",
                "Priya Rajapaksa",
                "My Secondary Account",
                true,
                "192.168.1.10"
        );

        assertThat(user1AccountB).isNotNull();
        assertThat(user1AccountB.getId()).isNotEqualTo(user1AccountA.getId());
        assertThat(user1AccountB.getMaskedNumber()).isEqualTo("****1001");

        List<BankAccount> accounts = bankAccountService.listAccounts(user1.id());
        assertThat(accounts).hasSize(2);

        // Verify paymentRegistry subcollection has both accounts
        QuerySnapshot regAccounts = firestore.collection("paymentRegistry")
                .document(user1.phone())
                .collection("accounts")
                .get().get();
        assertThat(regAccounts.size()).isEqualTo(2);
    }

    // ── 4. Set-Default Under Concurrency in ONE Transaction ─────

    @Test
    @Order(4)
    @DisplayName("4. set-default: unset old & set new in ONE transaction under concurrency")
    void testDefaultSelectionUnderConcurrency() throws Exception {
        // First set account A as default
        bankAccountService.setDefault(user1AccountA.getId(), user1.id(), "192.168.1.10");

        // Concurrently alternate setting account A and account B as default
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final Long targetAccountId = (i % 2 == 0) ? user1AccountA.getId() : user1AccountB.getId();
            executor.submit(() -> {
                try {
                    startLatch.await();
                    bankAccountService.setDefault(targetAccountId, user1.id(), "192.168.1.10");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Concurrent setDefault error: {}", e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).isTrue();

        // Verify in linkedBankAccounts: EXACTLY ONE default account exists
        List<LinkedAccount> userAccounts = linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(user1.id());
        long defaultCount = userAccounts.stream().filter(LinkedAccount::isDefault).count();
        assertThat(defaultCount).isEqualTo(1L);

        // Verify in paymentRegistry/{phone}/accounts subcollection: EXACTLY ONE default doc exists
        List<RegistryAccountRecord> regAccounts = phoneIdentityStore.findAccountsInRegistry(user1.phone());
        long regDefaultCount = regAccounts.stream().filter(RegistryAccountRecord::isDefault).count();
        assertThat(regDefaultCount).isEqualTo(1L);

        // Matching default account ID in both collections
        Long activeDefaultId = userAccounts.stream().filter(LinkedAccount::isDefault).findFirst().get().id();
        Long regDefaultId = regAccounts.stream().filter(RegistryAccountRecord::isDefault).findFirst().get().accountId();
        assertThat(regDefaultId).isEqualTo(activeDefaultId);
    }

    // ── 5. Object-Level Authorization ───────────────────────────

    @Test
    @Order(5)
    @DisplayName("5. Object-level authorization: blocks cross-user account access/mutations")
    void testObjectLevelAuthorization() {
        // User 2 attempts to patch User 1's account
        assertThatThrownBy(() -> bankAccountService.patchAccount(
                user1AccountA.getId(),
                user2.id(),
                "Hacked Label",
                true,
                true,
                "192.168.1.10"
        )).isInstanceOf(AuthorizationException.class);

        // User 2 attempts to set default on User 1's account
        assertThatThrownBy(() -> bankAccountService.setDefault(
                user1AccountA.getId(),
                user2.id(),
                "192.168.1.10"
        )).isInstanceOf(AuthorizationException.class);

        // User 2 attempts to remove User 1's account
        assertThatThrownBy(() -> bankAccountService.removeAccount(
                user1AccountA.getId(),
                user2.id(),
                true,
                "192.168.1.10"
        )).isInstanceOf(AuthorizationException.class);
    }

    // ── 6. Lookup Privacy & Safe View ───────────────────────────

    @Test
    @Order(6)
    @DisplayName("6. Lookup privacy: exposes only masked display name and safe account view")
    void testLookupPrivacy() {
        // Ensure account A is default and active
        bankAccountService.setDefault(user1AccountA.getId(), user1.id(), "192.168.1.10");

        // Perform lookup
        LookupResponse response = lookupService.lookup(user1.phone(), user2.id(), "10.0.0.1");
        assertThat(response.resultType()).isEqualTo(LookupResultType.FOUND);
        assertThat(response.displayName()).isEqualTo("Kamal P.");
        assertThat(response.accounts()).isNotEmpty();

        var summary = response.accounts().get(0);
        assertThat(summary.institutionCode()).isEqualTo("SIMULATED_BANK_A");
        assertThat(summary.bankDisplayName()).isEqualTo("Simulated Bank Alpha");
        assertThat(summary.maskedNumber()).isEqualTo("****1001");

        // When receiving is disabled for all accounts
        bankAccountService.patchAccount(user1AccountA.getId(), user1.id(), null, true, false, "192.168.1.10");
        bankAccountService.patchAccount(user1AccountB.getId(), user1.id(), null, true, false, "192.168.1.10");

        LookupResponse noReceiveResponse = lookupService.lookup(user1.phone(), user2.id(), "10.0.0.1");
        assertThat(noReceiveResponse.resultType()).isEqualTo(LookupResultType.NO_ELIGIBLE_ACCOUNT);

        // Restore receiving on account A
        bankAccountService.patchAccount(user1AccountA.getId(), user1.id(), null, true, true, "192.168.1.10");

        // When user-level discoverability is disabled
        PhoneIdentity id1 = phoneIdentityStore.findByPhone(user1.phone()).orElseThrow();
        phoneIdentityStore.save(id1.withDiscoverable(false));
        LookupResponse disabledResponse = lookupService.lookup(user1.phone(), user2.id(), "10.0.0.1");
        assertThat(disabledResponse.resultType()).isEqualTo(LookupResultType.DISCOVERY_DISABLED);

        // Re-enable discoverability
        phoneIdentityStore.save(id1.withDiscoverable(true));
    }

    // ── 7. Revoke Account (Blocks Routing, Preserves Pending) ───

    @Test
    @Order(7)
    @DisplayName("7. Revoking account: blocks new routing but preserves document for pending tx")
    void testAccountRevocation_BlocksRoutingPreservesPending() throws Exception {
        bankAccountService.removeAccount(user1AccountB.getId(), user1.id(), true, "192.168.1.10");

        // Document is preserved in linkedBankAccounts
        DocumentSnapshot doc = firestore.collection("linkedBankAccounts")
                .document(String.valueOf(user1AccountB.getId())).get().get();
        assertThat(doc.exists()).isTrue();
        assertThat(doc.getString("status")).isEqualTo("REMOVED");
        assertThat(doc.getBoolean("receivingEnabled")).isFalse();
        assertThat(doc.getBoolean("isDefault")).isFalse();

        // Subcollection in paymentRegistry reflects status REMOVED and receivingEnabled false
        DocumentSnapshot regSubDoc = firestore.collection("paymentRegistry")
                .document(user1.phone())
                .collection("accounts")
                .document(String.valueOf(user1AccountB.getId()))
                .get().get();
        assertThat(regSubDoc.exists()).isTrue();
        assertThat(regSubDoc.getString("status")).isEqualTo("REMOVED");
        assertThat(regSubDoc.getBoolean("receivingEnabled")).isFalse();

        // Pending transactions can still resolve the account by ID
        Optional<LinkedAccount> retrieved = linkedAccountStore.findAccountById(user1AccountB.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().canReceive()).isFalse();
        assertThat(retrieved.get().isActive()).isFalse();

        // Routing is blocked: requireReceivableAccount throws ValidationException
        assertThatThrownBy(() -> bankAccountService.requireReceivableAccount(user1AccountB.getId(), user1.id()))
                .isInstanceOf(ValidationException.class);

        // Account list reflects status REMOVED and receiving disabled
        List<BankAccount> allAccounts = bankAccountService.listAccounts(user1.id());
        BankAccount removedAcct = allAccounts.stream()
                .filter(a -> a.getId().equals(user1AccountB.getId()))
                .findFirst().orElseThrow();
        assertThat(removedAcct.getStatus()).isEqualTo(BankAccountStatus.REMOVED);
        assertThat(removedAcct.isReceivingEnabled()).isFalse();
        assertThat(removedAcct.isDefault()).isFalse();
    }

    // ── 8. Simulated Bank Accounts Collection ───────────────────

    @Test
    @Order(8)
    @DisplayName("8. Simulated bank accounts stored in simulatedBankAccounts collection")
    void testSimulatedBankAccountsInFirestore() throws Exception {
        QuerySnapshot simulatedSnap = firestore.collection("simulatedBankAccounts").get().get();
        assertThat(simulatedSnap.isEmpty()).isFalse();

        DocumentSnapshot acctDoc = firestore.collection("simulatedBankAccounts")
                .document("SIMULATED_BANK_A_101001").get().get();
        assertThat(acctDoc.exists()).isTrue();
        assertThat(acctDoc.getString("holderName")).isEqualTo("Kamal Perera");
        assertThat(acctDoc.getLong("balanceMinor")).isGreaterThan(0L);

        // Store lookup works
        var foundOpt = simulatedAccountStore.find("SIMULATED_BANK_A", "101001");
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().holderName()).isEqualTo("Kamal Perera");

        var allForA = simulatedAccountStore.allForInstitution("SIMULATED_BANK_A");
        assertThat(allForA).isNotEmpty();
    }

    // ── Helper ──────────────────────────────────────────────────

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

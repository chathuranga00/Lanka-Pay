package com.lankapay.bank.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.lankapay.bank.domain.*;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.common.firestore.FirestoreTransactionHelper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Firestore implementation of {@link LinkedAccountStore}.
 *
 * Enforces:
 * - Secure collections: bankInstitutions, linkedBankAccounts, consents, paymentRegistry/{phone}/accounts/{accountId}.
 * - Denormalized safe view (bank name + masked number) in paymentRegistry subcollection without encrypted references.
 * - Single-transaction atomic setDefault: unsets old default and sets new default in ONE transaction.
 * - Continuous transactional synchronization between linkedBankAccounts and paymentRegistry.
 * - Soft-delete revocation (status=REMOVED, receivingEnabled=false) preserving account records for pending transfers.
 */
@Component
public class FirestoreLinkedAccountStore implements LinkedAccountStore {

    private static final Logger log = LoggerFactory.getLogger(FirestoreLinkedAccountStore.class);

    public static final String BANK_INSTITUTIONS_COLLECTION = "bankInstitutions";
    public static final String LINKED_BANK_ACCOUNTS_COLLECTION = "linkedBankAccounts";
    public static final String CONSENTS_COLLECTION = "consents";
    public static final String PAYMENT_REGISTRY_COLLECTION = "paymentRegistry";
    public static final String USERS_COLLECTION = "users";

    private final Firestore firestore;
    private final FirestoreTransactionHelper txHelper;
    private final AtomicLong accountIdGen = new AtomicLong(System.currentTimeMillis() % 100000000L);
    private final AtomicLong consentIdGen = new AtomicLong(System.currentTimeMillis() % 100000000L);

    public FirestoreLinkedAccountStore(Firestore firestore, FirestoreTransactionHelper txHelper) {
        this.firestore = firestore;
        this.txHelper = txHelper;
        seedInstitutionsIfEmpty();
    }

    @PostConstruct
    public void init() {
        seedInstitutionsIfEmpty();
    }

    public void seedInstitutionsIfEmpty() {
        try {
            seedInstitution("SIMULATED_BANK_A", "Simulated Bank Alpha", true, true);
            seedInstitution("SIMULATED_BANK_B", "Simulated Bank Beta", true, true);
            seedInstitution("SIMULATED_BANK_C", "Simulated Bank Gamma", true, true);
        } catch (Exception e) {
            log.warn("[FIRESTORE] Failed to check/seed bank institutions", e);
        }
    }

    private void seedInstitution(String bankCode, String displayName, boolean simulated, boolean active) {
        try {
            DocumentReference ref = firestore.collection(BANK_INSTITUTIONS_COLLECTION).document(bankCode);
            Map<String, Object> data = new HashMap<>();
            data.put("bankCode", bankCode);
            data.put("bankName", displayName);
            data.put("displayName", displayName);
            data.put("simulated", simulated);
            data.put("active", active);
            data.put("createdAt", FieldValue.serverTimestamp());
            ref.set(data, SetOptions.merge()).get();
        } catch (Exception e) {
            log.warn("[FIRESTORE] Failed to seed institution: {}", bankCode, e);
        }
    }

    private Long nextAccountId() {
        return accountIdGen.incrementAndGet();
    }

    private Long nextConsentId() {
        return consentIdGen.incrementAndGet();
    }

    // ── Save Account ───────────────────────────────────────────

    @Override
    public LinkedAccount saveAccount(LinkedAccount account) {
        return txHelper.runTransaction(tx -> {
            Long accountId = account.id();
            boolean isNew = (accountId == null);
            if (isNew) {
                accountId = nextAccountId();

                // Duplicate check by hash inside transaction
                Query query = firestore.collection(LINKED_BANK_ACCOUNTS_COLLECTION)
                        .whereEqualTo("accountNumberHash", account.accountNumberHash());
                QuerySnapshot existing = tx.get(query).get();
                if (!existing.isEmpty()) {
                    throw new ConflictException("ACCOUNT_ALREADY_LINKED",
                            "This bank account is already linked");
                }
            }

            DocumentReference accountRef = firestore.collection(LINKED_BANK_ACCOUNTS_COLLECTION)
                    .document(String.valueOf(accountId));
            DocumentSnapshot existingSnap = tx.get(accountRef).get();

            long currentVersion = 0L;
            if (existingSnap.exists() && existingSnap.getLong("version") != null) {
                currentVersion = existingSnap.getLong("version");
            }

            // Look up user phone and display name for registry synchronization
            String phone = null;
            String userFullName = null;
            DocumentReference userRef = firestore.collection(USERS_COLLECTION)
                    .document(String.valueOf(account.userId()));
            DocumentSnapshot userSnap = tx.get(userRef).get();
            if (userSnap.exists()) {
                phone = userSnap.getString("phone");
                userFullName = userSnap.getString("fullName");
            }

            // Look up institution display name for denormalized safe view
            String bankName = getBankDisplayNameInternal(account.bankCode(), tx);

            // Read registry document BEFORE any writes (Firestore requirement: all reads before all writes)
            DocumentReference regDocRef = null;
            DocumentSnapshot regSnap = null;
            if (phone != null && !phone.isBlank()) {
                regDocRef = firestore.collection(PAYMENT_REGISTRY_COLLECTION).document(phone);
                regSnap = tx.get(regDocRef).get();
            }

            // 1. Write linkedBankAccounts document
            Map<String, Object> accountData = new HashMap<>();
            accountData.put("id", accountId);
            accountData.put("userId", account.userId());
            accountData.put("bankCode", account.bankCode());
            accountData.put("accountNumberHash", account.accountNumberHash());
            accountData.put("maskedNumber", account.maskedNumber());
            accountData.put("accountName", account.accountName());
            accountData.put("label", account.label());
            accountData.put("discoverable", account.discoverable());
            accountData.put("receivingEnabled", account.receivingEnabled());
            accountData.put("encryptedAccountReference", account.encryptedAccountReference());
            accountData.put("status", account.status().name());
            accountData.put("isDefault", account.isDefault());
            accountData.put("version", currentVersion + 1);
            accountData.put("updatedAt", FieldValue.serverTimestamp());
            if (isNew) {
                accountData.put("linkedAt", FieldValue.serverTimestamp());
            }

            tx.set(accountRef, accountData, SetOptions.merge());

            // 2. Synchronize paymentRegistry and "accounts" subcollection in SAME transaction
            if (regDocRef != null) {
                if (!regSnap.exists()) {
                    Map<String, Object> regData = new HashMap<>();
                    regData.put("phone", phone);
                    regData.put("userId", account.userId());
                    regData.put("displayName", userFullName != null ? userFullName : "User");
                    regData.put("discoverable", true);
                    regData.put("updatedAt", FieldValue.serverTimestamp());
                    tx.set(regDocRef, regData);
                }

                DocumentReference subDocRef = regDocRef.collection("accounts").document(String.valueOf(accountId));
                Map<String, Object> subData = new HashMap<>();
                subData.put("accountId", accountId);
                subData.put("userId", account.userId());
                subData.put("bankCode", account.bankCode());
                subData.put("bankName", bankName);
                subData.put("maskedNumber", account.maskedNumber());
                subData.put("accountName", account.accountName());
                subData.put("label", account.label());
                subData.put("discoverable", account.discoverable());
                subData.put("receivingEnabled", account.receivingEnabled());
                subData.put("isDefault", account.isDefault());
                subData.put("status", account.status().name());
                subData.put("updatedAt", FieldValue.serverTimestamp());

                tx.set(subDocRef, subData, SetOptions.merge());
            }

            return new LinkedAccount(
                    accountId,
                    account.userId(),
                    account.bankCode(),
                    account.accountNumberHash(),
                    account.maskedNumber(),
                    account.accountName(),
                    account.label(),
                    account.discoverable(),
                    account.receivingEnabled(),
                    account.encryptedAccountReference(),
                    account.status(),
                    account.isDefault(),
                    account.linkedAt() != null ? account.linkedAt() : Instant.now(),
                    Instant.now()
            );
        });
    }

    // ── Set Default in ONE Transaction ────────────────────────

    @Override
    public LinkedAccount setDefaultAccount(Long accountId, Long userId) {
        List<DocumentReference> userAccountRefs;
        try {
            userAccountRefs = firestore.collection(LINKED_BANK_ACCOUNTS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .get().get()
                    .getDocuments().stream()
                    .map(DocumentSnapshot::getReference)
                    .toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        return txHelper.runTransaction(tx -> {
            DocumentReference targetRef = firestore.collection(LINKED_BANK_ACCOUNTS_COLLECTION)
                    .document(String.valueOf(accountId));
            DocumentSnapshot targetSnap = tx.get(targetRef).get();

            if (!targetSnap.exists() || !Objects.equals(targetSnap.getLong("userId"), userId)) {
                throw new AuthorizationException("BANK_ACCOUNT_ACCESS_DENIED",
                        "Bank account not found or does not belong to this user");
            }

            String status = targetSnap.getString("status");
            if (!"ACTIVE".equalsIgnoreCase(status)) {
                throw new ValidationException("ACCOUNT_NOT_ACTIVE",
                        "This bank account is no longer active");
            }

            // Get user's phone for registry subcollection updates
            String phone = null;
            DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(String.valueOf(userId));
            DocumentSnapshot userSnap = tx.get(userRef).get();
            if (userSnap.exists()) {
                phone = userSnap.getString("phone");
            }

            // Read other account snapshots of this user inside the transaction (all reads before writes)
            Map<DocumentReference, DocumentSnapshot> otherAcctSnaps = new HashMap<>();
            for (DocumentReference ref : userAccountRefs) {
                if (!ref.getId().equals(String.valueOf(accountId))) {
                    otherAcctSnaps.put(ref, tx.get(ref).get());
                }
            }

            // 1. Unset old defaults on other accounts
            for (Map.Entry<DocumentReference, DocumentSnapshot> entry : otherAcctSnaps.entrySet()) {
                DocumentReference ref = entry.getKey();
                DocumentSnapshot snap = entry.getValue();
                Boolean wasDefault = snap.getBoolean("isDefault");
                if (Boolean.TRUE.equals(wasDefault)) {
                    long oldVer = snap.getLong("version") != null ? snap.getLong("version") : 0L;
                    tx.update(ref, Map.of(
                            "isDefault", false,
                            "version", oldVer + 1,
                            "updatedAt", FieldValue.serverTimestamp()
                    ));

                    if (phone != null && !phone.isBlank()) {
                        DocumentReference oldRegSub = firestore.collection(PAYMENT_REGISTRY_COLLECTION)
                                .document(phone)
                                .collection("accounts")
                                .document(ref.getId());
                        tx.set(oldRegSub, Map.of(
                                "isDefault", false,
                                "updatedAt", FieldValue.serverTimestamp()
                        ), SetOptions.merge());
                    }
                }
            }

            // 2. Set target account as default
            long targetVer = targetSnap.getLong("version") != null ? targetSnap.getLong("version") : 0L;
            tx.update(targetRef, Map.of(
                    "isDefault", true,
                    "version", targetVer + 1,
                    "updatedAt", FieldValue.serverTimestamp()
            ));

            if (phone != null && !phone.isBlank()) {
                DocumentReference targetRegSub = firestore.collection(PAYMENT_REGISTRY_COLLECTION)
                        .document(phone)
                        .collection("accounts")
                        .document(String.valueOf(accountId));
                tx.set(targetRegSub, Map.of(
                        "isDefault", true,
                        "updatedAt", FieldValue.serverTimestamp()
                ), SetOptions.merge());
            }

            LinkedAccount current = toDomain(targetSnap);
            return current.withDefault(true);
        });
    }

    // ── Queries ───────────────────────────────────────────────

    @Override
    public Optional<LinkedAccount> findAccountById(Long id) {
        if (id == null) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(LINKED_BANK_ACCOUNTS_COLLECTION)
                    .document(String.valueOf(id)).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(toDomain(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<LinkedAccount> findAccountByIdAndUserId(Long id, Long userId) {
        if (id == null || userId == null) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(LINKED_BANK_ACCOUNTS_COLLECTION)
                    .document(String.valueOf(id)).get().get();
            if (!snap.exists()) return Optional.empty();
            if (!Objects.equals(snap.getLong("userId"), userId)) {
                return Optional.empty();
            }
            return Optional.of(toDomain(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<LinkedAccount> findAccountByAccountNumberHash(String accountNumberHash) {
        if (accountNumberHash == null || accountNumberHash.isBlank()) return Optional.empty();
        try {
            QuerySnapshot snap = firestore.collection(LINKED_BANK_ACCOUNTS_COLLECTION)
                    .whereEqualTo("accountNumberHash", accountNumberHash)
                    .limit(1)
                    .get().get();
            if (snap.isEmpty()) return Optional.empty();
            return Optional.of(toDomain(snap.getDocuments().get(0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<LinkedAccount> findAccountsByUserIdAndStatus(Long userId, BankAccountStatus status) {
        if (userId == null) return List.of();
        try {
            Query query = firestore.collection(LINKED_BANK_ACCOUNTS_COLLECTION)
                    .whereEqualTo("userId", userId);
            if (status != null) {
                query = query.whereEqualTo("status", status.name());
            }
            QuerySnapshot snap = query.get().get();
            return snap.getDocuments().stream().map(this::toDomain).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<LinkedAccount> findAccountsByUserIdOrderByLinkedAtDesc(Long userId) {
        if (userId == null) return List.of();
        try {
            QuerySnapshot snap = firestore.collection(LINKED_BANK_ACCOUNTS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .get().get();
            return snap.getDocuments().stream()
                    .map(this::toDomain)
                    .sorted((a, b) -> {
                        Instant t1 = a.linkedAt() != null ? a.linkedAt() : Instant.EPOCH;
                        Instant t2 = b.linkedAt() != null ? b.linkedAt() : Instant.EPOCH;
                        return t2.compareTo(t1);
                    })
                    .toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<LinkedAccount> findActiveDefaultAccount(Long userId) {
        if (userId == null) return Optional.empty();
        try {
            QuerySnapshot snap = firestore.collection(LINKED_BANK_ACCOUNTS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("isDefault", true)
                    .whereEqualTo("status", BankAccountStatus.ACTIVE.name())
                    .limit(1)
                    .get().get();
            if (snap.isEmpty()) return Optional.empty();
            return Optional.of(toDomain(snap.getDocuments().get(0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int clearDefaultAccountForUser(Long userId) {
        if (userId == null) return 0;
        List<DocumentReference> userAccountRefs;
        try {
            userAccountRefs = firestore.collection(LINKED_BANK_ACCOUNTS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .get().get()
                    .getDocuments().stream()
                    .map(DocumentSnapshot::getReference)
                    .toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        return txHelper.runTransaction(tx -> {
            String phone = null;
            DocumentSnapshot userSnap = tx.get(firestore.collection(USERS_COLLECTION).document(String.valueOf(userId))).get();
            if (userSnap.exists()) {
                phone = userSnap.getString("phone");
            }

            Map<DocumentReference, DocumentSnapshot> acctSnaps = new HashMap<>();
            for (DocumentReference ref : userAccountRefs) {
                acctSnaps.put(ref, tx.get(ref).get());
            }

            int count = 0;
            for (Map.Entry<DocumentReference, DocumentSnapshot> entry : acctSnaps.entrySet()) {
                DocumentReference ref = entry.getKey();
                DocumentSnapshot doc = entry.getValue();
                if (Boolean.TRUE.equals(doc.getBoolean("isDefault"))) {
                    long v = doc.getLong("version") != null ? doc.getLong("version") : 0L;
                    tx.update(ref, Map.of(
                            "isDefault", false,
                            "version", v + 1,
                            "updatedAt", FieldValue.serverTimestamp()
                    ));
                    if (phone != null && !phone.isBlank()) {
                        DocumentReference subRef = firestore.collection(PAYMENT_REGISTRY_COLLECTION)
                                .document(phone)
                                .collection("accounts")
                                .document(ref.getId());
                        tx.set(subRef, Map.of(
                                "isDefault", false,
                                "updatedAt", FieldValue.serverTimestamp()
                        ), SetOptions.merge());
                    }
                    count++;
                }
            }
            return count;
        });
    }

    @Override
    public long countActiveAccounts(Long userId) {
        if (userId == null) return 0L;
        try {
            QuerySnapshot snap = firestore.collection(LINKED_BANK_ACCOUNTS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", BankAccountStatus.ACTIVE.name())
                    .get().get();
            return snap.size();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // ── Consents ──────────────────────────────────────────────

    @Override
    public AccountConsentRecord saveConsent(AccountConsentRecord consent) {
        Long consentId = consent.id() != null ? consent.id() : nextConsentId();
        DocumentReference ref = firestore.collection(CONSENTS_COLLECTION).document(String.valueOf(consentId));

        Map<String, Object> data = new HashMap<>();
        data.put("id", consentId);
        data.put("userId", consent.userId());
        data.put("bankAccountId", consent.bankAccountId());
        data.put("consentType", consent.consentType().name());
        data.put("purpose", consent.consentType().name());
        data.put("consentGiven", consent.consentGiven());
        data.put("status", consent.consentGiven() ? "GRANTED" : "REVOKED");
        data.put("consentIp", consent.consentIp());
        data.put("consentedAt", FieldValue.serverTimestamp());

        try {
            ref.set(data, SetOptions.merge()).get();
            return new AccountConsentRecord(
                    consentId,
                    consent.userId(),
                    consent.bankAccountId(),
                    consent.consentType(),
                    consent.consentGiven(),
                    consent.consentIp(),
                    Instant.now()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<AccountConsentRecord> findConsentsByBankAccountId(Long bankAccountId) {
        if (bankAccountId == null) return List.of();
        try {
            QuerySnapshot snap = firestore.collection(CONSENTS_COLLECTION)
                    .whereEqualTo("bankAccountId", bankAccountId)
                    .get().get();
            return snap.getDocuments().stream().map(this::toConsentDomain).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<AccountConsentRecord> findConsentsByUserIdAndType(Long userId, ConsentType consentType) {
        if (userId == null) return List.of();
        try {
            Query query = firestore.collection(CONSENTS_COLLECTION).whereEqualTo("userId", userId);
            if (consentType != null) {
                query = query.whereEqualTo("consentType", consentType.name());
            }
            QuerySnapshot snap = query.get().get();
            return snap.getDocuments().stream().map(this::toConsentDomain).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // ── Bank Institutions ─────────────────────────────────────

    @Override
    public Optional<BankInstitutionRecord> findActiveInstitutionByBankCode(String bankCode) {
        if (bankCode == null) return Optional.empty();
        return findInstitutionByBankCode(bankCode).filter(BankInstitutionRecord::active);
    }

    @Override
    public Optional<BankInstitutionRecord> findInstitutionByBankCode(String bankCode) {
        if (bankCode == null) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(BANK_INSTITUTIONS_COLLECTION).document(bankCode).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(toInstitutionDomain(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<BankInstitutionRecord> findAllInstitutions() {
        try {
            QuerySnapshot snap = firestore.collection(BANK_INSTITUTIONS_COLLECTION).get().get();
            return snap.getDocuments().stream().map(this::toInstitutionDomain).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BankInstitutionRecord saveInstitution(BankInstitutionRecord institution) {
        try {
            DocumentReference ref = firestore.collection(BANK_INSTITUTIONS_COLLECTION).document(institution.bankCode());
            Map<String, Object> data = new HashMap<>();
            data.put("bankCode", institution.bankCode());
            data.put("bankName", institution.displayName());
            data.put("displayName", institution.displayName());
            data.put("simulated", institution.simulated());
            data.put("active", institution.active());
            data.put("updatedAt", FieldValue.serverTimestamp());
            ref.set(data, SetOptions.merge()).get();
            return institution;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private String getBankDisplayNameInternal(String bankCode, Transaction tx) {
        if (bankCode == null) return "";
        try {
            DocumentReference instRef = firestore.collection(BANK_INSTITUTIONS_COLLECTION).document(bankCode);
            DocumentSnapshot instSnap = tx.get(instRef).get();
            if (instSnap.exists() && instSnap.getString("displayName") != null) {
                return instSnap.getString("displayName");
            }
        } catch (Exception ignored) {}
        return bankCode;
    }

    public LinkedAccount toDomain(DocumentSnapshot snap) {
        Long id = snap.getLong("id");
        if (id == null) {
            try {
                id = Long.parseLong(snap.getId());
            } catch (NumberFormatException ignored) {}
        }
        String statusStr = snap.getString("status");
        BankAccountStatus status = statusStr != null ? BankAccountStatus.valueOf(statusStr) : BankAccountStatus.ACTIVE;
        Boolean isDefault = snap.getBoolean("isDefault");
        Boolean discoverable = snap.getBoolean("discoverable");
        Boolean receiving = snap.getBoolean("receivingEnabled");

        return new LinkedAccount(
                id,
                snap.getLong("userId"),
                snap.getString("bankCode"),
                snap.getString("accountNumberHash"),
                snap.getString("maskedNumber"),
                snap.getString("accountName"),
                snap.getString("label"),
                discoverable != null && discoverable,
                receiving != null && receiving,
                snap.getString("encryptedAccountReference"),
                status,
                isDefault != null && isDefault,
                toInstant(snap.getTimestamp("linkedAt")),
                toInstant(snap.getTimestamp("updatedAt"))
        );
    }

    public AccountConsentRecord toConsentDomain(DocumentSnapshot snap) {
        Long id = snap.getLong("id");
        String typeStr = snap.getString("consentType");
        if (typeStr == null) {
            typeStr = snap.getString("purpose");
        }
        ConsentType type = typeStr != null ? ConsentType.valueOf(typeStr) : ConsentType.LINK_ACCOUNT;
        Boolean given = snap.getBoolean("consentGiven");
        if (given == null && snap.getString("status") != null) {
            given = "GRANTED".equalsIgnoreCase(snap.getString("status"));
        }

        return new AccountConsentRecord(
                id,
                snap.getLong("userId"),
                snap.getLong("bankAccountId"),
                type,
                given != null && given,
                snap.getString("consentIp"),
                toInstant(snap.getTimestamp("consentedAt"))
        );
    }

    public BankInstitutionRecord toInstitutionDomain(DocumentSnapshot snap) {
        String bankCode = snap.getString("bankCode") != null ? snap.getString("bankCode") : snap.getId();
        Boolean simulated = snap.getBoolean("simulated");
        Boolean active = snap.getBoolean("active");

        return new BankInstitutionRecord(
                snap.getLong("id"),
                bankCode,
                snap.getString("displayName"),
                simulated != null && simulated,
                active != null && active,
                toInstant(snap.getTimestamp("createdAt"))
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()) : Instant.now();
    }
}

package com.lankapay.bank.adapter.simulated;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.lankapay.common.util.MoneyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store of simulated test bank accounts, backed by in-memory cache and
 * Firestore collection "simulatedBankAccounts" when running in firestore mode.
 *
 * ⚠ SIMULATED ONLY — active when {@code app.bank-adapter.mode=SIMULATED}.
 */
@Component
@ConditionalOnProperty(name = "app.bank-adapter.mode", havingValue = "SIMULATED",
        matchIfMissing = true)
public class SimulatedAccountStore {

    private static final Logger log = LoggerFactory.getLogger(SimulatedAccountStore.class);
    public static final String SIMULATED_ACCOUNTS_COLLECTION = "simulatedBankAccounts";

    /** Key: "INSTITUTION_CODE:accountNumber" */
    private final Map<String, SimulatedTestAccount> accounts = new ConcurrentHashMap<>();

    private final Firestore firestore;
    private final boolean firestoreMode;

    public SimulatedAccountStore() {
        this(null);
    }

    @Autowired
    public SimulatedAccountStore(@Autowired(required = false) Firestore firestore) {
        this.firestore = firestore;
        this.firestoreMode = firestore != null;
        seed();
        if (firestoreMode) {
            syncToFirestore();
        }
        log.info("[SIMULATED] SimulatedAccountStore seeded with {} test accounts (firestoreMode={})",
                accounts.size(), firestoreMode);
    }

    public void syncToFirestore() {
        if (firestore == null) return;
        try {
            WriteBatch batch = firestore.batch();
            boolean hasWrites = false;
            for (SimulatedTestAccount a : accounts.values()) {
                String docId = a.institutionCode() + "_" + a.accountNumber();
                DocumentReference ref = firestore.collection(SIMULATED_ACCOUNTS_COLLECTION).document(docId);
                DocumentSnapshot snap = ref.get().get();
                if (!snap.exists()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("institutionCode", a.institutionCode());
                    data.put("accountNumber", a.accountNumber());
                    data.put("maskedNumber", a.maskedNumber());
                    data.put("holderName", a.holderName());
                    data.put("balanceMinor", MoneyHelper.toMinorUnits(a.balance()));
                    data.put("balance", a.balance().toPlainString());
                    data.put("scenario", a.scenarioOverride() != null ? a.scenarioOverride().name() : null);
                    data.put("active", a.active());
                    data.put("updatedAt", FieldValue.serverTimestamp());
                    batch.set(ref, data);
                    hasWrites = true;
                }
            }
            if (hasWrites) {
                batch.commit().get();
            }
        } catch (Exception e) {
            log.warn("Failed to sync simulatedBankAccounts to Firestore", e);
        }
    }

    // ── Lookup ─────────────────────────────────────────────────

    public Optional<SimulatedTestAccount> find(String institutionCode, String accountNumber) {
        if (firestoreMode) {
            String docId = institutionCode + "_" + accountNumber;
            try {
                DocumentSnapshot snap = firestore.collection(SIMULATED_ACCOUNTS_COLLECTION).document(docId).get().get();
                if (snap.exists()) {
                    return Optional.of(mapFromFirestore(snap));
                }
            } catch (Exception e) {
                log.warn("Failed to read simulated account from Firestore: {}", docId, e);
            }
        }
        return Optional.ofNullable(accounts.get(key(institutionCode, accountNumber)));
    }

    public Collection<SimulatedTestAccount> allForInstitution(String institutionCode) {
        if (firestoreMode) {
            try {
                QuerySnapshot snap = firestore.collection(SIMULATED_ACCOUNTS_COLLECTION)
                        .whereEqualTo("institutionCode", institutionCode)
                        .get().get();
                if (!snap.isEmpty()) {
                    return snap.getDocuments().stream().map(this::mapFromFirestore).toList();
                }
            } catch (Exception e) {
                log.warn("Failed to query simulated accounts from Firestore: {}", institutionCode, e);
            }
        }
        return accounts.values().stream()
                .filter(a -> a.institutionCode().equals(institutionCode))
                .toList();
    }

    public void save(SimulatedTestAccount account) {
        accounts.put(key(account.institutionCode(), account.accountNumber()), account);
        if (firestoreMode) {
            try {
                String docId = account.institutionCode() + "_" + account.accountNumber();
                DocumentReference ref = firestore.collection(SIMULATED_ACCOUNTS_COLLECTION).document(docId);
                Map<String, Object> data = new HashMap<>();
                data.put("institutionCode", account.institutionCode());
                data.put("accountNumber", account.accountNumber());
                data.put("maskedNumber", account.maskedNumber());
                data.put("holderName", account.holderName());
                data.put("balanceMinor", MoneyHelper.toMinorUnits(account.balance()));
                data.put("balance", account.balance().toPlainString());
                data.put("scenario", account.scenarioOverride() != null ? account.scenarioOverride().name() : null);
                data.put("active", account.active());
                data.put("updatedAt", FieldValue.serverTimestamp());
                ref.set(data).get();
            } catch (Exception e) {
                log.warn("Failed to save simulated account to Firestore", e);
            }
        }
    }

    private SimulatedTestAccount mapFromFirestore(DocumentSnapshot snap) {
        String instCode = snap.getString("institutionCode");
        String accNum = snap.getString("accountNumber");
        String holder = snap.getString("holderName");
        String balStr = snap.getString("balance");
        Long balMinor = snap.getLong("balanceMinor");
        BigDecimal balance = balStr != null ? new BigDecimal(balStr) : (balMinor != null ? MoneyHelper.toBigDecimal(balMinor) : BigDecimal.ZERO);
        String scenarioStr = snap.getString("scenario");
        TransferScenario scenario = scenarioStr != null ? TransferScenario.valueOf(scenarioStr) : null;
        Boolean active = snap.getBoolean("active");
        String masked = snap.getString("maskedNumber");
        if (masked == null && accNum != null) {
            masked = "****" + accNum.substring(Math.max(0, accNum.length() - 4));
        }
        return new SimulatedTestAccount(instCode, accNum, masked, holder, balance, scenario, active == null || active);
    }

    // ── Seed ───────────────────────────────────────────────────

    private void seed() {
        // ── Bank A – normal accounts ───────────────────────────
        add(SimulatedTestAccount.of("SIMULATED_BANK_A", "101001", "Kamal Perera",       "250000.00"));
        add(SimulatedTestAccount.of("SIMULATED_BANK_A", "101002", "Nimali Silva",        "80000.00"));
        add(SimulatedTestAccount.of("SIMULATED_BANK_A", "101003", "Saman Fernando",      "15000.00"));

        // Bank A – scenario-override accounts
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_A", "100100", "Test InsufficientA",
                "0.00",       TransferScenario.INSUFFICIENT_FUNDS));
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_A", "100200", "Test TimeoutA",
                "500000.00",  TransferScenario.TIMEOUT_SUCCEEDED));
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_A", "100300", "Test DelayedA",
                "500000.00",  TransferScenario.DELAYED_CONFIRMATION));
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_A", "100400", "Test HardFailA",
                "500000.00",  TransferScenario.HARD_FAILURE));
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_A", "100500", "Test ReversalA",
                "500000.00",  TransferScenario.LATER_REVERSAL));

        // ── Bank B – normal accounts ───────────────────────────
        add(SimulatedTestAccount.of("SIMULATED_BANK_B", "201001", "Priya Rajapaksa",    "120000.00"));
        add(SimulatedTestAccount.of("SIMULATED_BANK_B", "201002", "Asanka Bandara",     "35000.00"));
        add(SimulatedTestAccount.of("SIMULATED_BANK_B", "201003", "Chamari Jayasinghe", "9000.00"));

        // Bank B – scenario-override accounts
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_B", "200100", "Test InsufficientB",
                "0.00",       TransferScenario.INSUFFICIENT_FUNDS));
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_B", "200200", "Test TimeoutB",
                "500000.00",  TransferScenario.TIMEOUT_SUCCEEDED));
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_B", "200300", "Test DelayedB",
                "500000.00",  TransferScenario.DELAYED_CONFIRMATION));
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_B", "200400", "Test HardFailB",
                "500000.00",  TransferScenario.HARD_FAILURE));
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_B", "200500", "Test ReversalB",
                "500000.00",  TransferScenario.LATER_REVERSAL));

        // ── Bank C – normal accounts ───────────────────────────
        add(SimulatedTestAccount.of("SIMULATED_BANK_C", "301001", "Dinesh Wijesinghe",  "75000.00"));
        add(SimulatedTestAccount.of("SIMULATED_BANK_C", "301002", "Sewwandi Kumari",    "200000.00"));
        add(SimulatedTestAccount.of("SIMULATED_BANK_C", "301003", "Malith Gunasekara",  "4500.00"));

        // Bank C – scenario-override accounts
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_C", "300100", "Test InsufficientC",
                "0.00",       TransferScenario.INSUFFICIENT_FUNDS));
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_C", "300200", "Test TimeoutC",
                "500000.00",  TransferScenario.TIMEOUT_SUCCEEDED));
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_C", "300300", "Test DelayedC",
                "500000.00",  TransferScenario.DELAYED_CONFIRMATION));
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_C", "300400", "Test HardFailC",
                "500000.00",  TransferScenario.HARD_FAILURE));
        add(SimulatedTestAccount.withScenario("SIMULATED_BANK_C", "300500", "Test ReversalC",
                "500000.00",  TransferScenario.LATER_REVERSAL));
    }

    private void add(SimulatedTestAccount account) {
        accounts.put(key(account.institutionCode(), account.accountNumber()), account);
    }

    private static String key(String institutionCode, String accountNumber) {
        return institutionCode + ":" + accountNumber;
    }
}

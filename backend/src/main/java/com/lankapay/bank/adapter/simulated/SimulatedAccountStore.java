package com.lankapay.bank.adapter.simulated;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of simulated test bank accounts.
 *
 * ⚠ SIMULATED ONLY — active when {@code app.bank-adapter.mode=SIMULATED}.
 *
 * Accounts are seeded at construction time with a fixed set of test accounts
 * across all three simulated banks.  Account numbers follow a simple convention:
 *
 *   Bank A accounts:  10xxxx
 *   Bank B accounts:  20xxxx
 *   Bank C accounts:  30xxxx
 *
 * Special scenario accounts (one per bank):
 *   x00100  → INSUFFICIENT_FUNDS override
 *   x00200  → TIMEOUT_SUCCEEDED override
 *   x00300  → DELAYED_CONFIRMATION override
 *   x00400  → HARD_FAILURE override
 *   x00500  → LATER_REVERSAL override
 *
 * All other accounts trigger scenario resolution by amount (magic amounts).
 */
@Component
@ConditionalOnProperty(name = "app.bank-adapter.mode", havingValue = "SIMULATED",
        matchIfMissing = true)
public class SimulatedAccountStore {

    private static final Logger log = LoggerFactory.getLogger(SimulatedAccountStore.class);

    /** Key: "INSTITUTION_CODE:accountNumber" */
    private final Map<String, SimulatedTestAccount> accounts = new ConcurrentHashMap<>();

    public SimulatedAccountStore() {
        seed();
        log.info("[SIMULATED] SimulatedAccountStore seeded with {} test accounts", accounts.size());
    }

    // ── Lookup ─────────────────────────────────────────────────

    public Optional<SimulatedTestAccount> find(String institutionCode, String accountNumber) {
        return Optional.ofNullable(accounts.get(key(institutionCode, accountNumber)));
    }

    public Collection<SimulatedTestAccount> allForInstitution(String institutionCode) {
        return accounts.values().stream()
                .filter(a -> a.institutionCode().equals(institutionCode))
                .toList();
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

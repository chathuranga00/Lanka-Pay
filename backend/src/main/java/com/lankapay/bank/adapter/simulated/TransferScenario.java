package com.lankapay.bank.adapter.simulated;

import java.math.BigDecimal;

/**
 * Deterministic test scenarios triggered by magic amounts or account flags.
 *
 * ⚠ SIMULATED ONLY — these scenarios never apply to real bank adapters.
 *
 * Magic amount trigger table:
 *
 *   Amount (LKR)         Scenario
 *   ──────────────────── ──────────────────────────────────────────
 *   0.01 – 9999.99       SUCCESS                (normal path)
 *   10000.00             INSUFFICIENT_FUNDS     (balance too low)
 *   20000.00             TIMEOUT_SUCCEEDED      (timeout reported, but bank actually
 *                                                committed – caller must poll)
 *   30000.00             DELAYED_CONFIRMATION   (PENDING first, COMPLETED on poll)
 *   40000.00             HARD_FAILURE           (bank rejects permanently)
 *   50000.00             LATER_REVERSAL         (COMPLETED initially, REVERSED on poll)
 *   Any other amount     SUCCESS                (default)
 *
 * Account flag triggers (set on the SimulatedTestAccount row):
 *   scenarioOverride != null  →  use the stored scenario regardless of amount
 */
public enum TransferScenario {

    SUCCESS,
    INSUFFICIENT_FUNDS,
    TIMEOUT_SUCCEEDED,
    DELAYED_CONFIRMATION,
    HARD_FAILURE,
    LATER_REVERSAL;

    // ── Magic trigger amounts ──────────────────────────────────

    private static final BigDecimal AMT_INSUFFICIENT  = new BigDecimal("10000.00");
    private static final BigDecimal AMT_TIMEOUT       = new BigDecimal("20000.00");
    private static final BigDecimal AMT_DELAYED       = new BigDecimal("30000.00");
    private static final BigDecimal AMT_HARD_FAIL     = new BigDecimal("40000.00");
    private static final BigDecimal AMT_REVERSAL      = new BigDecimal("50000.00");

    /**
     * Resolve the scenario to use for a transfer request.
     *
     * @param amount           The transfer amount in LKR
     * @param accountOverride  Per-account override stored in SimulatedTestAccount
     *                         (null means "use amount-based logic")
     * @return The scenario that will be applied
     */
    public static TransferScenario resolve(BigDecimal amount, TransferScenario accountOverride) {
        if (accountOverride != null) {
            return accountOverride;
        }
        if (amount.compareTo(AMT_INSUFFICIENT) == 0) return INSUFFICIENT_FUNDS;
        if (amount.compareTo(AMT_TIMEOUT)      == 0) return TIMEOUT_SUCCEEDED;
        if (amount.compareTo(AMT_DELAYED)      == 0) return DELAYED_CONFIRMATION;
        if (amount.compareTo(AMT_HARD_FAIL)    == 0) return HARD_FAILURE;
        if (amount.compareTo(AMT_REVERSAL)     == 0) return LATER_REVERSAL;
        return SUCCESS;
    }
}

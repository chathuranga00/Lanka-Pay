package com.lankapay.bank.adapter.simulated;

import java.math.BigDecimal;

/**
 * An in-memory test account used by simulated bank adapters.
 *
 * ⚠ SIMULATED ONLY — this class has no JPA entity; accounts live in a
 * static map inside {@link SimulatedAccountStore} seeded at startup.
 *
 * Fields:
 *   institutionCode   e.g. "SIMULATED_BANK_A"
 *   accountNumber     Plain account number used as the lookup key
 *   maskedNumber      Last 4 digits, e.g. "****1001"
 *   holderName        Account holder display name
 *   balance           Current simulated balance (BigDecimal, LKR)
 *   scenarioOverride  If non-null, forces this scenario regardless of amount
 *   active            Whether the account accepts transactions
 */
public record SimulatedTestAccount(
        String institutionCode,
        String accountNumber,
        String maskedNumber,
        String holderName,
        BigDecimal balance,
        TransferScenario scenarioOverride,
        boolean active
) {
    /** Convenience factory for a normal active account with no scenario override. */
    public static SimulatedTestAccount of(String institutionCode, String accountNumber,
                                          String holderName, String balanceStr) {
        String masked = "****" + accountNumber.substring(
                Math.max(0, accountNumber.length() - 4));
        return new SimulatedTestAccount(
                institutionCode, accountNumber, masked,
                holderName, new BigDecimal(balanceStr), null, true);
    }

    /** Factory with an explicit scenario override. */
    public static SimulatedTestAccount withScenario(String institutionCode,
                                                    String accountNumber,
                                                    String holderName,
                                                    String balanceStr,
                                                    TransferScenario override) {
        String masked = "****" + accountNumber.substring(
                Math.max(0, accountNumber.length() - 4));
        return new SimulatedTestAccount(
                institutionCode, accountNumber, masked,
                holderName, new BigDecimal(balanceStr), override, true);
    }
}

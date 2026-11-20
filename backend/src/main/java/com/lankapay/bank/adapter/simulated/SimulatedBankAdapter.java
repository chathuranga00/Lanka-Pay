package com.lankapay.bank.adapter.simulated;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Legacy single-instance simulated adapter — kept for backward compatibility
 * with any code that injected {@code BankAdapter} directly before the registry
 * was introduced.
 *
 * ⚠ Prefer using {@link com.lankapay.bank.adapter.BankAdapterRegistry} to route
 * by institution code. Direct injection of this bean is deprecated.
 *
 * Delegates all operations to the SIMULATED_BANK_A implementation.
 *
 * Active when: app.bank-adapter.mode=SIMULATED (default).
 */
public class SimulatedBankAdapter extends AbstractSimulatedBankAdapter {

    public SimulatedBankAdapter(SimulatedAccountStore store) {
        super(store);
    }

    @Override
    public String getInstitutionCode() {
        // Acts as Bank A when injected directly
        return "SIMULATED_BANK_A";
    }
}

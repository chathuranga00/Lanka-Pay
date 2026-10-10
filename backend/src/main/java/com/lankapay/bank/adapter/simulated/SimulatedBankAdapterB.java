package com.lankapay.bank.adapter.simulated;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SIMULATED Bank Adapter — Simulated Bank B.
 *
 * ⚠ UNIVERSITY PROTOTYPE — NOT a real bank.
 * Institution code: SIMULATED_BANK_B
 * Test accounts:    201001, 201002, 201003 (normal); 200100–200500 (scenario overrides)
 */
@Component
@ConditionalOnProperty(name = "app.bank-adapter.mode", havingValue = "SIMULATED",
        matchIfMissing = true)
public class SimulatedBankAdapterB extends AbstractSimulatedBankAdapter {

    public SimulatedBankAdapterB(SimulatedAccountStore store) {
        super(store);
    }

    @Override
    public String getInstitutionCode() {
        return "SIMULATED_BANK_B";
    }
}

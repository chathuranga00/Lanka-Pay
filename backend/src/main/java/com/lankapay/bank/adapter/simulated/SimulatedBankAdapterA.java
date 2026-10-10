package com.lankapay.bank.adapter.simulated;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SIMULATED Bank Adapter — Simulated Bank A.
 *
 * ⚠ UNIVERSITY PROTOTYPE — NOT a real bank.
 * Institution code: SIMULATED_BANK_A
 * Test accounts:    101001, 101002, 101003 (normal); 100100–100500 (scenario overrides)
 */
@Component
@ConditionalOnProperty(name = "app.bank-adapter.mode", havingValue = "SIMULATED",
        matchIfMissing = true)
public class SimulatedBankAdapterA extends AbstractSimulatedBankAdapter {

    public SimulatedBankAdapterA(SimulatedAccountStore store) {
        super(store);
    }

    @Override
    public String getInstitutionCode() {
        return "SIMULATED_BANK_A";
    }
}

package com.lankapay.bank.adapter.simulated;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SIMULATED Bank Adapter — Simulated Bank C.
 *
 * ⚠ UNIVERSITY PROTOTYPE — NOT a real bank.
 * Institution code: SIMULATED_BANK_C
 * Test accounts:    301001, 301002, 301003 (normal); 300100–300500 (scenario overrides)
 */
@Component
@ConditionalOnProperty(name = "app.bank-adapter.mode", havingValue = "SIMULATED",
        matchIfMissing = true)
public class SimulatedBankAdapterC extends AbstractSimulatedBankAdapter {

    public SimulatedBankAdapterC(SimulatedAccountStore store) {
        super(store);
    }

    @Override
    public String getInstitutionCode() {
        return "SIMULATED_BANK_C";
    }
}

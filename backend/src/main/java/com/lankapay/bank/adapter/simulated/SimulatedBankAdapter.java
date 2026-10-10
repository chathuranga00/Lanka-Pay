package com.lankapay.bank.adapter.simulated;

import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRequest;
import com.lankapay.bank.adapter.BankAdapterResponse;
import com.lankapay.common.util.SimulatedResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SIMULATED bank adapter.
 *
 * ⚠ UNIVERSITY PROTOTYPE – this does NOT connect to any real bank.
 * All operations always return success with a SIMULATED transaction reference.
 *
 * Active when: BANK_ADAPTER_MODE=SIMULATED (default).
 */
@Component
@ConditionalOnProperty(name = "app.bank-adapter.mode", havingValue = "SIMULATED", matchIfMissing = true)
public class SimulatedBankAdapter implements BankAdapter {

    private static final Logger log = LoggerFactory.getLogger(SimulatedBankAdapter.class);

    @Override
    public BankAdapterResponse verifyAccount(BankAdapterRequest request) {
        log.info("[SIMULATED] verifyAccount bankCode={} maskedAccount={}",
                request.bankCode(), request.maskedAccountNumber());
        return BankAdapterResponse.success(SimulatedResponseUtil.generateSimulatedTxRef());
    }

    @Override
    public BankAdapterResponse debit(BankAdapterRequest request) {
        log.info("[SIMULATED] debit bankCode={} maskedAccount={} amount={} {}",
                request.bankCode(), request.maskedAccountNumber(),
                request.amount(), request.currency());
        return BankAdapterResponse.success(SimulatedResponseUtil.generateSimulatedTxRef());
    }

    @Override
    public BankAdapterResponse credit(BankAdapterRequest request) {
        log.info("[SIMULATED] credit bankCode={} maskedAccount={} amount={} {}",
                request.bankCode(), request.maskedAccountNumber(),
                request.amount(), request.currency());
        return BankAdapterResponse.success(SimulatedResponseUtil.generateSimulatedTxRef());
    }
}

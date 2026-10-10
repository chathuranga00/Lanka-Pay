package com.lankapay.bank.adapter;

/**
 * Contract for all bank interactions.
 *
 * Rules:
 * - This is the ONLY interface any module may call for bank operations.
 * - No module outside com.lankapay.bank may call a bank API directly.
 * - All implementations must set simulated=true in BankAdapterResponse
 *   while BANK_ADAPTER_MODE=SIMULATED.
 */
public interface BankAdapter {

    /**
     * Verify that an account exists and the name matches.
     * Used during bank account linking.
     */
    BankAdapterResponse verifyAccount(BankAdapterRequest request);

    /**
     * Debit funds from a bank account.
     * NEVER mark a transfer as succeeded until this returns success=true.
     */
    BankAdapterResponse debit(BankAdapterRequest request);

    /**
     * Credit funds to a bank account.
     * NEVER mark a transfer as succeeded until this returns success=true.
     */
    BankAdapterResponse credit(BankAdapterRequest request);
}

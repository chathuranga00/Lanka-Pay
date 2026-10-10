package com.lankapay.bank.adapter;

/**
 * Contract for all bank interactions in Lanka Pay.
 *
 * Rules:
 * - This is the ONLY interface any code outside {@code com.lankapay.bank} may call.
 * - No module may call a bank API directly.
 * - All implementations MUST set {@code simulated=true} while
 *   {@code app.bank-adapter.mode=SIMULATED}.
 * - NEVER mark a transfer as succeeded until {@link #initiateTransfer} or
 *   {@link #queryTransferStatus} returns {@code transferStatus=COMPLETED}.
 */
public interface BankAdapter {

    /**
     * Institution code this adapter handles (e.g. "SIMULATED_BANK_A").
     * Used by {@link com.lankapay.bank.adapter.BankAdapterRegistry} for routing.
     */
    String getInstitutionCode();

    /**
     * Verify that a bank account exists and the account holder name matches.
     * Called during bank-account linking.
     */
    AdapterResponse verifyOwnership(AccountOwnershipRequest request);

    /**
     * Resolve a payment identifier (phone, alias) to a payable account reference
     * at this institution. Used before presenting a payee to the sender.
     */
    AdapterResponse resolveAccountReference(AccountReferenceRequest request);

    /**
     * Pre-flight check: verify the account is active, unfrozen, and has
     * sufficient balance for the requested amount.
     * NOTE: the bank may still reject the actual debit due to race conditions.
     */
    AdapterResponse checkTransferEligibility(TransferEligibilityRequest request);

    /**
     * Get a fee/FX quote for a transfer. The quote reference may be included
     * in {@link #initiateTransfer} to lock in the rate.
     */
    AdapterResponse getQuote(QuoteRequest request);

    /**
     * Initiate a transfer (debit sender, credit receiver).
     * NEVER mark a LankaPay transfer as COMPLETED until this returns
     * {@code transferStatus=COMPLETED}. A {@code PENDING} or {@code TIMEOUT}
     * response must be reconciled via {@link #queryTransferStatus}.
     */
    AdapterResponse initiateTransfer(TransferRequest request);

    /**
     * Query the current status of a previously initiated transfer.
     * Used for polling after a timeout or async confirmation scenario.
     */
    AdapterResponse queryTransferStatus(TransferStatusRequest request);

    /**
     * Handle an inbound bank callback (webhook notification).
     * Returns the resolved transfer status.
     */
    AdapterResponse handleCallback(CallbackPayload payload);

    /**
     * Reconcile all transactions for the given settlement date.
     * Returns a summary response; detailed rows are persisted by the caller.
     */
    AdapterResponse reconcile(ReconcileRequest request);
}

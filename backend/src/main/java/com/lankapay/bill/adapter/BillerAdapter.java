package com.lankapay.bill.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Contract for interacting with institutional billers following the BankAdapter pattern.
 *
 * Rules:
 * - This is the ONLY interface any code outside the adapter layer may call for bill operations.
 * - All implementations must operate in simulated mode when app.features.environment is SANDBOX_SIMULATED.
 * - A timeout during payBill must move the transaction to REQUIRES_RECONCILIATION, never to FAILED.
 * - Never mark a bill payment as succeeded until {@link #payBill} or {@link #queryPayment} returns COMPLETED.
 */
public interface BillerAdapter {

    /**
     * Unique adapter code (e.g. "SIMULATED_BILLER_ADAPTER").
     */
    String getAdapterCode();

    /**
     * List all billers serviced by this adapter.
     */
    List<BillerInfo> listBillers();

    /**
     * Find a specific biller by ID.
     */
    Optional<BillerInfo> getBiller(String billerId);

    /**
     * Validate a customer reference string against biller format and existence rules.
     */
    BillValidationResult validateCustomerReference(String billerId, String customerReference);

    /**
     * Fetch bill details and amount due for a given customer reference.
     */
    BillDetails fetchBill(String billerId, String customerReference);

    /**
     * Execute a bill payment.
     * If the biller times out or confirmation is delayed, returns REQUIRES_RECONCILIATION or PENDING.
     */
    BillPaymentResponse payBill(BillPaymentRequest request);

    /**
     * Query status of a previous payment.
     */
    BillPaymentResponse queryPayment(String paymentReference);

    /**
     * Reconcile transactions for a given biller on a settlement date.
     */
    BillerReconcileResult reconcile(String billerId, LocalDate settlementDate);
}

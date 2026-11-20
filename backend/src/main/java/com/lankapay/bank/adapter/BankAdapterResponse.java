package com.lankapay.bank.adapter;

/**
 * Response from a BankAdapter implementation.
 * simulated is always true in this prototype.
 */
public record BankAdapterResponse(
        boolean success,
        String transactionReference,   // e.g. SIM-1234567890-5678
        String failureReason,          // null on success
        boolean simulated              // ALWAYS true in this prototype
) {
    public static BankAdapterResponse success(String txRef) {
        return new BankAdapterResponse(true, txRef, null, true);
    }

    public static BankAdapterResponse failure(String reason) {
        return new BankAdapterResponse(false, null, reason, true);
    }
}

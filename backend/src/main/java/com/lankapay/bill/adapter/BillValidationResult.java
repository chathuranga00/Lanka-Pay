package com.lankapay.bill.adapter;

/**
 * Result of validating customer reference against biller rules.
 */
public record BillValidationResult(
        boolean valid,
        String customerName,
        String errorMessage
) {
    public static BillValidationResult success(String customerName) {
        return new BillValidationResult(true, customerName, null);
    }

    public static BillValidationResult failure(String errorMessage) {
        return new BillValidationResult(false, null, errorMessage);
    }
}

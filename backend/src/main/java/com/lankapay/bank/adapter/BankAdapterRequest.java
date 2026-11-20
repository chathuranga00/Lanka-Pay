package com.lankapay.bank.adapter;

import java.math.BigDecimal;

/**
 * Request payload sent to a BankAdapter implementation.
 * Uses BigDecimal for amount – never double or float.
 */
public record BankAdapterRequest(
        String bankCode,
        String maskedAccountNumber,   // last 4 digits – never full account number
        BigDecimal amount,
        String currency,
        String operationReference     // UUID idempotency key
) {}

package com.lankapay.bank.adapter;

import java.math.BigDecimal;

/**
 * Request to check whether a transfer of the given amount is permitted
 * (sufficient balance, account active, not frozen, within limits).
 *
 * Note: this is a pre-flight check only. The bank may still reject the
 * actual debit if state changes between the check and the transfer.
 */
public record TransferEligibilityRequest(
        String institutionCode,
        String maskedAccountNumber,    // last 4 digits – never full number
        BigDecimal amount,
        String currency,
        String operationReference
) {}

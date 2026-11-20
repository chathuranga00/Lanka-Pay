package com.lankapay.bank.adapter;

import java.math.BigDecimal;

/**
 * Request to initiate a transfer (debit sender, credit receiver) at the bank.
 * Uses BigDecimal for amount — never double or float.
 *
 * Security: maskedAccountNumber is used for logging; the bank resolves
 * the full account via the accountReferenceId returned from resolveAccountReference.
 */
public record TransferRequest(
        String institutionCode,
        String maskedDebitAccount,
        String maskedCreditAccount,
        BigDecimal amount,
        String currency,
        String quoteReference,         // nullable – from a prior getQuote call
        String operationReference      // UUID idempotency key
) {}

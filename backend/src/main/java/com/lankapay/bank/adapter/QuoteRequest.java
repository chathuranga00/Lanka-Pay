package com.lankapay.bank.adapter;

import java.math.BigDecimal;

/**
 * Request for a fee/FX quote before initiating a transfer.
 */
public record QuoteRequest(
        String institutionCode,
        BigDecimal amount,
        String currency,
        String operationReference
) {}

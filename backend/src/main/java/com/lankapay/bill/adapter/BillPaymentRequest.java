package com.lankapay.bill.adapter;

import com.lankapay.transfer.domain.FundingType;

import java.math.BigDecimal;

/**
 * Instruction to execute a bill payment via BillerAdapter.
 */
public record BillPaymentRequest(
        String paymentReference,
        String billerId,
        String customerReference,
        BigDecimal amount,
        String currency,
        FundingType fundingType,
        Long fundingSourceId,
        String customerName
) {}

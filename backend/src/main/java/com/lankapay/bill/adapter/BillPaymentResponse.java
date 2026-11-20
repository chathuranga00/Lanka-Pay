package com.lankapay.bill.adapter;

import com.lankapay.transfer.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Result returned by BillerAdapter for a bill payment.
 */
public record BillPaymentResponse(
        String paymentReference,
        String billerPaymentRef,
        TransactionStatus status,
        String failureReason,
        BigDecimal amountPaid,
        Instant timestamp
) {}

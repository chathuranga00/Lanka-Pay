package com.lankapay.bill.adapter;

import com.lankapay.transfer.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Result of reconciling with a biller for a given date.
 */
public record BillerReconcileResult(
        String billerId,
        LocalDate settlementDate,
        int totalTransactions,
        BigDecimal totalAmount,
        List<BillerReconcileItem> items
) {
    public record BillerReconcileItem(
            String paymentReference,
            String billerPaymentRef,
            BigDecimal amount,
            TransactionStatus status
    ) {}
}

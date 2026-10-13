package com.lankapay.bill.domain;

import com.lankapay.bill.adapter.BillerCategory;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain record holding specific metadata for a bill payment transaction.
 */
public record BillPaymentRecord(
        Long id,
        String paymentReference,
        String idempotencyKey,
        Long userId,
        String billerId,
        String billerName,
        BillerCategory category,
        String customerReferenceEncrypted,
        String customerReferenceMasked,
        String customerName,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal totalAmount,
        String currency,
        TransactionStatus status,
        String failureReason,
        String billerConfirmationRef,
        FundingType fundingType,
        Long fundingSourceId,
        String fundingLabel,
        LocalDate billDueDate,
        Long transactionId,
        Instant createdAt,
        Instant updatedAt
) {
    public BillPaymentRecord withStatus(TransactionStatus newStatus, String newFailureReason) {
        return new BillPaymentRecord(
                id, paymentReference, idempotencyKey, userId, billerId, billerName, category,
                customerReferenceEncrypted, customerReferenceMasked, customerName,
                amount, fee, totalAmount, currency, newStatus, newFailureReason,
                billerConfirmationRef, fundingType, fundingSourceId, fundingLabel,
                billDueDate, transactionId, createdAt, Instant.now()
        );
    }

    public BillPaymentRecord withBillerConfirmation(String newConfirmationRef, TransactionStatus newStatus) {
        return new BillPaymentRecord(
                id, paymentReference, idempotencyKey, userId, billerId, billerName, category,
                customerReferenceEncrypted, customerReferenceMasked, customerName,
                amount, fee, totalAmount, currency, newStatus, failureReason,
                newConfirmationRef, fundingType, fundingSourceId, fundingLabel,
                billDueDate, transactionId, createdAt, Instant.now()
        );
    }
}

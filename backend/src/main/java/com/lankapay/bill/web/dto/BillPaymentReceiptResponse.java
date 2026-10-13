package com.lankapay.bill.web.dto;

import com.lankapay.bill.adapter.BillerCategory;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BillPaymentReceiptResponse(
        String receiptNumber,
        Long paymentId,
        String reference,
        String billerId,
        String billerName,
        BillerCategory category,
        String maskedReference,
        String customerName,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal totalAmount,
        String currency,
        TransactionStatus status,
        String billerConfirmationRef,
        String failureReason,
        FundingType fundingType,
        String fundingLabel,
        LocalDate billDueDate,
        Instant timestamp
) {}

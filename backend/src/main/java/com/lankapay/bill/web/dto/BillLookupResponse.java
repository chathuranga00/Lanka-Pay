package com.lankapay.bill.web.dto;

import com.lankapay.bill.adapter.BillerCategory;
import com.lankapay.bill.adapter.BillStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillLookupResponse(
        String billerId,
        String billerName,
        BillerCategory category,
        String customerReference,
        String maskedReference,
        String customerName,
        BigDecimal amountDue,
        BigDecimal minimumAmount,
        LocalDate dueDate,
        BillStatus billStatus,
        String statusMessage,
        BigDecimal serviceFee,
        BigDecimal totalAmount
) {}

package com.lankapay.bill.adapter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Details retrieved during a bill inquiry/lookup.
 */
public record BillDetails(
        String billerId,
        String billerName,
        BillerCategory category,
        String customerReference,
        String customerName,
        BigDecimal amountDue,
        BigDecimal minimumAmount,
        LocalDate dueDate,
        BillStatus billStatus,
        String statusMessage,
        BigDecimal serviceFee
) {}

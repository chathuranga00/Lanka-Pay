package com.lankapay.bill.adapter;

import java.math.BigDecimal;

/**
 * Public metadata for a supported biller.
 * NOTE: All billers in sandbox are clearly simulated and have no real company names or logos.
 */
public record BillerInfo(
        String id,
        String name,
        BillerCategory category,
        String referenceLabel,
        String referenceExample,
        String referencePattern,
        BigDecimal serviceFee,
        boolean partialPayAllowed,
        boolean requiresLookup
) {}

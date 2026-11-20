package com.lankapay.home.web.dto;

import java.time.Instant;

/**
 * Summary record of a recent payment or transfer for the home dashboard stream.
 */
public record RecentActivityItem(
        Long transferId,
        String reference,
        long amountMinor,
        String amountLkr,
        String currency,
        String status,
        String direction, // "INCOMING" or "OUTGOING"
        String counterpartyName,
        Instant createdAt,
        boolean isSimulated
) {}

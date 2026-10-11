package com.lankapay.admin.web.dto;

import com.lankapay.transfer.domain.TransactionEvent;

import java.time.Instant;

public record AdminTransactionEventResponse(
        Long id,
        String fromStatus,
        String toStatus,
        String reason,
        String detailJson,
        Instant occurredAt
) {
    public static AdminTransactionEventResponse from(TransactionEvent event) {
        return new AdminTransactionEventResponse(
                event.getId(),
                event.getFromStatus() != null ? event.getFromStatus().name() : null,
                event.getToStatus() != null ? event.getToStatus().name() : null,
                event.getReason(),
                event.getDetailJson(),
                event.getOccurredAt()
        );
    }
}

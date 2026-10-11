package com.lankapay.admin.domain;

import java.time.Instant;

/**
 * Plain Java domain record for fraud alerts (no JPA annotations).
 */
public record FraudAlertRecord(
        Long id,
        Long userId,
        String userPhone,
        String userFullName,
        Long transferId,
        String transferReference,
        FraudAlertType alertType,
        FraudAlertSeverity severity,
        FraudAlertStatus status,
        String description,
        String metadataJson,
        Instant createdAt,
        Instant resolvedAt,
        Long resolvedBy,
        String resolutionNotes
) {
}

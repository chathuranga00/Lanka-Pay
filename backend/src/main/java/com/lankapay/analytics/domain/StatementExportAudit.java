package com.lankapay.analytics.domain;

import java.time.Instant;

public record StatementExportAudit(
        String token,
        Long userId,
        String filename,
        String month,
        byte[] pdfBytes,
        Instant requestedAt,
        Instant expiresAt,
        String clientIp
) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}

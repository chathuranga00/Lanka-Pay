package com.lankapay.analytics.web.dto;

import java.time.Instant;

public record StatementResponse(
        String token,
        String downloadUrl,
        String filename,
        String month,
        Instant expiresAt
) {}

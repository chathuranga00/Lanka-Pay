package com.lankapay.insights.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Domain record representing an individual actionable insight.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InsightItem(
        String id,
        InsightType type,
        String title,
        String text,
        String actionDeepLink,
        Instant generatedAt
) {
    public InsightItem(String id, InsightType type, String title, String text, String actionDeepLink) {
        this(id, type, title, text, actionDeepLink, Instant.now());
    }
}

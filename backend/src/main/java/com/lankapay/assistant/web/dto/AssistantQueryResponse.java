package com.lankapay.assistant.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * AI Assistant query response with mandatory AI-generated disclaimer label.
 */
public record AssistantQueryResponse(
        String query,
        String answer,
        String disclaimer,
        List<String> suggestedActions,
        Instant timestamp
) {}

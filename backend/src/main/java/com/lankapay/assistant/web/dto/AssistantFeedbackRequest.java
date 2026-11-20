package com.lankapay.assistant.web.dto;

public record AssistantFeedbackRequest(
        String queryId,
        boolean helpful,
        String comment
) {}

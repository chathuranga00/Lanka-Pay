package com.lankapay.assistant.web.dto;

public record AssistantConsentResponse(
        Long userId,
        boolean consented
) {}

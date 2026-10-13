package com.lankapay.assistant.web.dto;

import jakarta.validation.constraints.NotNull;

public record AssistantConsentRequest(
        @NotNull(message = "consented flag is required")
        Boolean consented
) {}

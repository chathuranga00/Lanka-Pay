package com.lankapay.assistant.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantQueryRequest(
        @NotBlank(message = "Query must not be empty")
        @Size(max = 1000, message = "Query cannot exceed 1000 characters")
        String query
) {}

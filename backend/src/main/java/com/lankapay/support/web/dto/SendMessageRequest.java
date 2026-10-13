package com.lankapay.support.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Message text cannot be empty")
        @Size(max = 2000, message = "Message exceeds maximum length of 2000 characters")
        String text,
        String attachedTransactionRef
) {}

package com.lankapay.support.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminSupportReplyRequest(
        @NotBlank(message = "Reply message cannot be empty")
        @Size(max = 2000, message = "Reply exceeds maximum length of 2000 characters")
        String text
) {}

package com.lankapay.bill.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SavedBillerRequest(
        @NotBlank(message = "billerId is required")
        String billerId,

        @NotBlank(message = "customerReference is required")
        String customerReference,

        @NotBlank(message = "nickname is required")
        @Size(max = 50, message = "nickname must not exceed 50 characters")
        String nickname
) {}

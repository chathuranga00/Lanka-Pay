package com.lankapay.transfer.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to initiate a reversal for a completed transfer")
public record ReversalRequest(

        @Schema(description = "Reason for reversal", example = "Duplicate charge requested by customer")
        @NotBlank(message = "reason is required")
        @Size(max = 255, message = "reason must not exceed 255 characters")
        String reason
) {}

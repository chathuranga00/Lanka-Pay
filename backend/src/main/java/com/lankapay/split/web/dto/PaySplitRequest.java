package com.lankapay.split.web.dto;

import com.lankapay.transfer.domain.FundingSource;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body to pay a split share")
public record PaySplitRequest(
        @Schema(description = "4 or 6-digit PIN for step-up authentication", example = "1234")
        String pin,

        @Schema(description = "Funding source specification (ACCOUNT or CARD)")
        FundingSource fundingSource,

        @Schema(description = "Idempotency key to prevent double charging", example = "IDEMP-SPLIT-992")
        String idempotencyKey
) {}

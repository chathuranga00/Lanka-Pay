package com.lankapay.rewards.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RedeemPointsRequest(
        @NotBlank(message = "Reward type is required (e.g. VOUCHER, STATEMENT_CREDIT)")
        String rewardType,

        @NotNull(message = "Points to redeem is required")
        @Min(value = 1, message = "Points must be at least 1")
        Integer points,

        String voucherPartner,

        String note,

        String pin // step-up auth PIN if not passed via X-Step-Up-Pin header
) {
}

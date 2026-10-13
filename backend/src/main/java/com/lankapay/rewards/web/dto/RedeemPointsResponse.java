package com.lankapay.rewards.web.dto;

import java.time.Instant;

public record RedeemPointsResponse(
        String redemptionId,
        String rewardType,
        int pointsRedeemed,
        int remainingBalance,
        String voucherCode,
        String partner,
        String statementCreditLabel,
        String status,
        Instant redeemedAt,
        String disclaimer
) {
}

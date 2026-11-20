package com.lankapay.rewards.web.dto;

import com.lankapay.rewards.domain.RewardTier;
import java.util.List;

public record RewardsSummaryResponse(
        int pointsBalance,
        RewardTier tier,
        String tierDisplayName,
        int expiringSoonPoints,
        String disclaimer,
        int monthlyEarnedPoints,
        int dailyEarnedPoints,
        int dailyCapRemaining,
        int monthlyCapRemaining,
        boolean hasNewOffers
) {
    public static final String SIMULATED_DISCLAIMER = "Simulated points only. Points hold no monetary cash value and cannot be redeemed for fiat currency.";
}

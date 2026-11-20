package com.lankapay.rewards.domain;

/**
 * Configuration for rewards earning caps, rules, and anti-abuse policies.
 */
public record RewardsConfig(
        int dailyPointsCap,          // e.g. 500 simulated points per day
        int monthlyPointsCap,        // e.g. 5000 simulated points per month
        int maxPointsPerTransaction, // e.g. 250 simulated points per txn
        int defaultExpirationDays,   // e.g. 90 days
        int billPaymentFixedPoints,  // e.g. 25 points
        int qrPaymentPointsPer100Lkr,// e.g. 1 point per 100 LKR
        int firstCardBonusPoints,    // e.g. 100 points
        int referralBonusPoints,     // e.g. 50 points
        int velocityMaxTxnPerHour    // e.g. 10 transactions max per hour
) {
    public static RewardsConfig defaultConfig() {
        return new RewardsConfig(
                500,    // daily cap
                5000,   // monthly cap
                250,    // max per txn
                90,     // 90 days expiration
                25,     // bill payment
                1,      // 1 pt per 100 LKR
                100,    // first card bonus
                50,     // referral bonus
                10      // velocity cap: 10/hour
        );
    }
}

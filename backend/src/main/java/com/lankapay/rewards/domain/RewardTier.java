package com.lankapay.rewards.domain;

/**
 * Rewards tier determined by monthly confirmed activity.
 */
public enum RewardTier {
    BASIC(0, "Basic"),
    PLUS(5, "Plus");

    private final int minMonthlyTransactions;
    private final String displayName;

    RewardTier(int minMonthlyTransactions, String displayName) {
        this.minMonthlyTransactions = minMonthlyTransactions;
        this.displayName = displayName;
    }

    public int getMinMonthlyTransactions() {
        return minMonthlyTransactions;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static RewardTier fromMonthlyTransactionCount(int count) {
        if (count >= PLUS.minMonthlyTransactions) {
            return PLUS;
        }
        return BASIC;
    }
}

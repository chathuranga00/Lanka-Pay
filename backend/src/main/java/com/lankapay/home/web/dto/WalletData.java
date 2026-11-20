package com.lankapay.home.web.dto;

/**
 * Primary wallet summary including simulated balance, default linked account,
 * active card, and total counts.
 */
public record WalletData(
        long primaryBalanceMinor,
        String primaryBalanceLkr,
        String currency,
        boolean isSimulated,
        LinkedAccountSummary defaultAccount,
        CardSummary activeCard,
        int totalAccounts,
        int totalCards
) {}

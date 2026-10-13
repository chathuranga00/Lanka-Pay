package com.lankapay.home.web.dto;

/**
 * Highest-priority nudge for the wallet card bottom banner.
 *
 * Priority order (descending):
 *   1. VERIFY_PHONE      – phone not yet verified
 *   2. LINK_ACCOUNT      – no bank account linked
 *   3. VERIFY_CARD_OTP   – a card awaiting OTP verification
 *   4. TRANSFER_PENDING  – a transfer in PENDING/PROCESSING state
 *   5. BUDGET_WARNING    – spend > 80 % of a budget (future, placeholder)
 *   6. SECURITY_TIP      – rotating tip
 *
 * The nudge is dismissible; dismissal is tracked client-side only.
 */
public record NudgeData(
        String nudgeId,
        String type,
        String message,
        String actionLabel,
        String actionRoute
) {}

package com.lankapay.virtualcard.provider;

/**
 * Sensitive card details revealed for virtual cards only.
 * Handled in memory only — NEVER logged, NEVER persisted in Firestore.
 */
public record CardRevealDetails(
        String  cardToken,
        String  pan,
        String  cvv,
        String  expMonth,
        String  expYear,
        String  brandLabel,
        int     expiresInSeconds,
        boolean simulated
) {
    @Override
    public String toString() {
        String last4 = (pan != null && pan.length() >= 4) ? pan.substring(pan.length() - 4) : "****";
        return "CardRevealDetails[cardToken=" + cardToken + ", pan=****" + last4
                + ", cvv=***, expMonth=**, expYear=**, brandLabel=" + brandLabel
                + ", expiresInSeconds=" + expiresInSeconds + ", simulated=" + simulated + "]";
    }
}

package com.lankapay.qr.domain;

/**
 * Type of QR code.
 *
 * STATIC:
 *   - Reusable profile/merchant QR.
 *   - No fixed amount (amount is determined by payer at transfer time).
 *   - No expiration.
 *   - oneTimeUse = false.
 *
 * DYNAMIC:
 *   - Specific bill/transaction QR.
 *   - Fixed amount and currency.
 *   - Explicit expiry timestamp.
 *   - oneTimeUse = true (becomes USED upon payment).
 */
public enum QrType {
    STATIC,
    DYNAMIC
}

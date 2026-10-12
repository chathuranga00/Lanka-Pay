package com.lankapay.simvault.internal;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * In-memory record of a seeded test card inside the vault.
 *
 * ISOLATION: package-private — never exported outside com.lankapay.simvault.
 *
 * Security:
 * - encryptedPan: AES-256-GCM ciphertext; plaintext only lives during tokenize.
 * - cvvHash: BCrypt hash; original CVV discarded after tokenize.
 * - Only last4, expiry, scenario, and holderName are ever exposed outside the vault.
 */
record VaultCard(
        String     last4,
        String     encryptedPan,   // AES-256-GCM; decrypted only during tokenize validation
        String     cvvHash,        // BCrypt(cvv); original CVV discarded after tokenize
        String     expMonth,       // "MM"
        String     expYear,        // "YY"
        String     holderName,
        BigDecimal balance,
        VaultScenario scenario
) {
    boolean isExpired() {
        int mm = Integer.parseInt(expMonth);
        int yy = Integer.parseInt(expYear);
        YearMonth cardExpiry = YearMonth.of(2000 + yy, mm);
        return YearMonth.now().isAfter(cardExpiry);
    }
}

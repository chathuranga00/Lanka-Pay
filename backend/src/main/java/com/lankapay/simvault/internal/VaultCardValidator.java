package com.lankapay.simvault.internal;

import com.lankapay.common.exception.ValidationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

/**
 * Validates a tokenize attempt against format rules and the seed table.
 *
 * ISOLATION: package-private — never exported outside com.lankapay.simvault.
 *
 * Anti-enumeration: all failure paths throw the SAME generic exception with
 * the SAME message so callers cannot determine which check failed.
 */
final class VaultCardValidator {

    private static final String GENERIC_REJECT =
            "Test cards only. Never enter a real card number.";

    private static final BCryptPasswordEncoder CVV_ENCODER = new BCryptPasswordEncoder(10);

    private final List<VaultCard> seedCards;

    VaultCardValidator(List<VaultCard> seedCards) {
        this.seedCards = seedCards;
    }

    /**
     * Validate pan+expiry+cvv against format rules and the seed table.
     * Returns the matching VaultCard on success.
     * Throws a GENERIC 422 for ANY failure to prevent enumeration.
     *
     * @param pan      16-digit PAN (must pass Luhn)
     * @param expMonth 2-digit month string
     * @param expYear  2-digit year string
     * @param cvv      3–4 digit CVV
     * @return matching VaultCard
     * @throws ValidationException always with the same generic message
     */
    VaultCard validate(String pan, String expMonth, String expYear, String cvv) {

        // 1. Format checks — generic reject on any failure
        if (!isValidPanFormat(pan))    reject();
        if (!LuhnValidator.isValid(pan)) reject();
        if (!isValidExpiry(expMonth, expYear)) reject();
        if (!isValidCvvFormat(cvv))    reject();

        // 2. Expiry not in the past (skip for the CARD_EXPIRED seed card — it's intentionally past)
        //    We allow past expiry through validation so the seeded scenario can demonstrate it.
        //    The adapter layer handles the CARD_EXPIRED scenario response.

        // 3. Seed table lookup — same generic reject if not found
        VaultCard match = findInSeed(pan);
        if (match == null) reject();

        // 4. CVV match against stored hash
        if (!CVV_ENCODER.matches(cvv, match.cvvHash())) reject();

        return match;
    }

    private VaultCard findInSeed(String pan) {
        String panHash = VaultCardSeed.sha256(pan);
        for (VaultCard card : seedCards) {
            if (card.panHash().equals(panHash)) {
                return card;
            }
        }
        return null;
    }

    private static boolean isValidPanFormat(String pan) {
        return pan != null && pan.matches("\\d{16}");
    }

    private static boolean isValidExpiry(String mm, String yy) {
        if (mm == null || yy == null) return false;
        if (!mm.matches("0[1-9]|1[0-2]")) return false;
        if (!yy.matches("\\d{2}")) return false;
        return true; // past expiry is allowed through — scenario handles it
    }

    private static boolean isValidCvvFormat(String cvv) {
        return cvv != null && cvv.matches("\\d{3,4}");
    }

    private static void reject() {
        throw new ValidationException("TEST_CARD_REJECTED", GENERIC_REJECT);
    }
}

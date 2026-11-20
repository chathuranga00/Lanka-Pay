package com.lankapay.common.util;

import com.lankapay.common.exception.ValidationException;

import java.util.regex.Pattern;

/**
 * Normalises Sri Lankan mobile phone numbers to E.164 format (+94XXXXXXXXX).
 *
 * Accepted input formats (all map to the same E.164 output):
 *
 *   Local 10-digit   :  07X XXX XXXX  →  +947XXXXXXXX
 *   Local 9-digit    :  7X XXX XXXX   →  +947XXXXXXXX   (leading 0 omitted)
 *   International    :  +947XXXXXXXX  →  +947XXXXXXXX   (already canonical)
 *   International 00 :  00947XXXXXXXX →  +947XXXXXXXX
 *
 * Whitespace and hyphens are stripped before matching.
 *
 * Sri Lankan mobile prefixes (after country code 94):
 *   70, 71, 72, 74, 75, 76, 77, 78
 *   (Landlines are NOT supported — this is a mobile payment platform)
 *
 * Security rule: the normalised number is what is stored in the DB.
 * Never log the full number — always mask with {@link #mask(String)}.
 */
public final class PhoneNumberNormalizer {

    private PhoneNumberNormalizer() {}

    /** E.164 Sri Lanka mobile: +94 7[0-8 except 3] XXXXXXX */
    private static final Pattern E164_SL_MOBILE =
            Pattern.compile("^\\+94(7[01245678]\\d{7})$");

    /**
     * Strips whitespace/hyphens then normalises the input.
     *
     * @param raw Raw phone number supplied by the user
     * @return    Canonical E.164 string, e.g. {@code +94771234567}
     * @throws ValidationException if the number cannot be mapped to a valid SL mobile
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("PHONE_REQUIRED", "Phone number must not be blank");
        }

        // Strip whitespace, hyphens, and parentheses
        String cleaned = raw.replaceAll("[\\s\\-()]", "");

        // Already E.164 with + prefix
        if (cleaned.startsWith("+94")) {
            return validate(cleaned);
        }

        // 00-prefixed international
        if (cleaned.startsWith("0094")) {
            return validate("+" + cleaned.substring(2));
        }

        // Local 10-digit: 07XXXXXXXX
        if (cleaned.startsWith("0") && cleaned.length() == 10) {
            return validate("+94" + cleaned.substring(1));
        }

        // Local 9-digit: 7XXXXXXXX (no leading zero)
        if (cleaned.startsWith("7") && cleaned.length() == 9) {
            return validate("+94" + cleaned);
        }

        throw new ValidationException("INVALID_PHONE_FORMAT",
                "Phone number could not be recognised as a Sri Lankan mobile number");
    }

    /**
     * Returns a masked version safe for logs and API responses.
     * "+94771234567" → "+9477***4567"
     */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 7) return "***";
        // Show first 5 chars (+9477) and last 4 (4567)
        return e164.substring(0, 5) + "***" + e164.substring(e164.length() - 4);
    }

    // ── private ─────────────────────────────────────────────

    private static String validate(String e164) {
        if (!E164_SL_MOBILE.matcher(e164).matches()) {
            throw new ValidationException("INVALID_PHONE_FORMAT",
                    "Phone number is not a valid Sri Lankan mobile number");
        }
        return e164;
    }
}

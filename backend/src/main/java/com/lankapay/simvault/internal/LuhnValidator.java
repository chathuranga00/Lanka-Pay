package com.lankapay.simvault.internal;

/**
 * Standard Luhn check-digit validator.
 *
 * ISOLATION: package-private — never exported outside com.lankapay.simvault.
 */
final class LuhnValidator {

    private LuhnValidator() {}

    /**
     * Returns true if the given digit string passes the Luhn check.
     * Accepts digits only; spaces and hyphens are stripped first.
     */
    static boolean isValid(String pan) {
        if (pan == null) return false;
        String digits = pan.replaceAll("[\\s\\-]", "");
        if (!digits.matches("\\d{13,19}")) return false;

        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}

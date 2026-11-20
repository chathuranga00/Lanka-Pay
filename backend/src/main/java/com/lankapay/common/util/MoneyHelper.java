package com.lankapay.common.util;

import com.lankapay.common.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility helper for converting between {@link BigDecimal} / {@link Money}
 * and integer minor units (cents / cents-equivalent) stored as 64-bit {@code long}.
 *
 * This eliminates floating-point rounding errors and IEEE 754 precision drift
 * in NoSQL document stores such as Google Cloud Firestore.
 *
 * Example:
 * 500.25 LKR  <--->  50025 minor units (cents)
 */
public final class MoneyHelper {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneyHelper() {
        // Utility class
    }

    /**
     * Converts a BigDecimal amount to long minor units (cents).
     * Automatically scales to 2 decimal places using HALF_UP rounding.
     *
     * @param amount the decimal monetary amount (e.g., 500.25)
     * @return the amount in minor units (e.g., 50025L)
     */
    public static long toMinorUnits(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.setScale(2, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .longValueExact();
    }

    /**
     * Converts a {@link Money} domain object to long minor units.
     *
     * @param money the money value object
     * @return the amount in minor units
     */
    public static long toMinorUnits(Money money) {
        if (money == null) {
            return 0L;
        }
        return toMinorUnits(money.amount());
    }

    /**
     * Converts long minor units (cents) back to a {@link BigDecimal} with scale 2.
     *
     * @param minorUnits the minor units (e.g., 50025L)
     * @return the decimal amount with scale 2 (e.g., 500.25)
     */
    public static BigDecimal toBigDecimal(long minorUnits) {
        return BigDecimal.valueOf(minorUnits, 2);
    }

    /**
     * Converts long minor units (cents) to a {@link Money} object in the specified currency.
     *
     * @param minorUnits the minor units
     * @param currency   ISO-4217 currency code (defaults to LKR if null)
     * @return the Money domain object
     */
    public static Money toMoney(long minorUnits, String currency) {
        String curr = (currency != null && !currency.isBlank()) ? currency : Money.LKR;
        return Money.of(toBigDecimal(minorUnits), curr);
    }

    /**
     * Convenience method to convert long minor units to {@link Money} in Sri Lankan Rupees (LKR).
     *
     * @param minorUnits the minor units in cents
     * @return the Money object in LKR
     */
    public static Money toMoneyLkr(long minorUnits) {
        return toMoney(minorUnits, Money.LKR);
    }
}

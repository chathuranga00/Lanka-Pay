package com.lankapay.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable value object representing a monetary amount in a given currency.
 * Always use BigDecimal – never double or float for money.
 */
public record Money(BigDecimal amount, String currency) {

    public static final String LKR = "LKR";

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Monetary amount must have at most 2 decimal places");
        }
    }

    /** Factory – always scales to 2 dp. */
    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount.setScale(2, RoundingMode.HALF_UP), currency);
    }

    public static Money lkr(BigDecimal amount) {
        return of(amount, LKR);
    }

    public static Money lkr(String amount) {
        return lkr(new BigDecimal(amount));
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZeroOrNegative() {
        return amount.compareTo(BigDecimal.ZERO) <= 0;
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return of(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return of(amount.subtract(other.amount), currency);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: %s vs %s".formatted(currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return "%s %s".formatted(amount.toPlainString(), currency);
    }
}

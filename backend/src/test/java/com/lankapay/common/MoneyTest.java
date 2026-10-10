package com.lankapay.common;

import com.lankapay.common.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Money value object.
 * No Spring context required – runs on every mvn test invocation.
 */
class MoneyTest {

    @Test
    void factory_setsScaleToTwo() {
        Money m = Money.lkr("100");
        assertThat(m.amount().scale()).isEqualTo(2);
        assertThat(m.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void lkr_currencyIsAlwaysLKR() {
        Money m = Money.lkr(new BigDecimal("500"));
        assertThat(m.currency()).isEqualTo("LKR");
    }

    @Test
    void add_returnsSumWithSameCurrency() {
        Money a = Money.lkr("100.50");
        Money b = Money.lkr("49.50");
        assertThat(a.add(b).amount()).isEqualByComparingTo("150.00");
    }

    @Test
    void subtract_returnsDifferenceWithSameCurrency() {
        Money a = Money.lkr("200.00");
        Money b = Money.lkr("75.25");
        assertThat(a.subtract(b).amount()).isEqualByComparingTo("124.75");
    }

    @Test
    void isPositive_trueForPositiveAmount() {
        assertThat(Money.lkr("0.01").isPositive()).isTrue();
    }

    @Test
    void isZeroOrNegative_trueForZero() {
        assertThat(Money.lkr("0.00").isZeroOrNegative()).isTrue();
    }

    @Test
    void isZeroOrNegative_trueForNegative() {
        assertThat(Money.lkr("-1.00").isZeroOrNegative()).isTrue();
    }

    @Test
    void constructor_rejectsMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1.001"), "LKR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 decimal places");
    }

    @Test
    void add_throwsOnCurrencyMismatch() {
        Money lkr = Money.lkr("100.00");
        Money usd = Money.of(new BigDecimal("100.00"), "USD");
        assertThatThrownBy(() -> lkr.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void toString_includesAmountAndCurrency() {
        assertThat(Money.lkr("250.00").toString()).isEqualTo("250.00 LKR");
    }
}

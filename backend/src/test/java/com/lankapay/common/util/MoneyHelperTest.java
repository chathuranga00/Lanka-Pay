package com.lankapay.common.util;

import com.lankapay.common.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyHelperTest {

    @Test
    @DisplayName("Converts null BigDecimal and null Money to 0 minor units")
    void handlesNullInputs() {
        assertThat(MoneyHelper.toMinorUnits((BigDecimal) null)).isEqualTo(0L);
        assertThat(MoneyHelper.toMinorUnits((Money) null)).isEqualTo(0L);
    }

    @ParameterizedTest
    @CsvSource({
        "0.00, 0",
        "0.01, 1",
        "0.99, 99",
        "1.00, 100",
        "100.50, 10050",
        "500.25, 50025",
        "1234567.89, 123456789",
        "-50.25, -5025"
    })
    @DisplayName("Converts decimal strings to exact long minor units")
    void toMinorUnits_convertsExpectedly(String amountStr, long expectedMinor) {
        BigDecimal decimal = new BigDecimal(amountStr);
        assertThat(MoneyHelper.toMinorUnits(decimal)).isEqualTo(expectedMinor);

        Money money = Money.lkr(amountStr);
        assertThat(MoneyHelper.toMinorUnits(money)).isEqualTo(expectedMinor);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0.00",
        "1, 0.01",
        "99, 0.99",
        "100, 1.00",
        "10050, 100.50",
        "50025, 500.25",
        "123456789, 1234567.89",
        "-5025, -50.25"
    })
    @DisplayName("Converts minor units back to BigDecimal with scale 2")
    void toBigDecimal_convertsExpectedly(long minorUnits, String expectedStr) {
        BigDecimal result = MoneyHelper.toBigDecimal(minorUnits);
        assertThat(result).isEqualByComparingTo(expectedStr);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Round-trip conversion preserves exact value without drift")
    void roundTripFidelity() {
        BigDecimal original = new BigDecimal("987654.32");
        long minor = MoneyHelper.toMinorUnits(original);
        BigDecimal roundTripped = MoneyHelper.toBigDecimal(minor);

        assertThat(roundTripped).isEqualByComparingTo(original);
    }

    @Test
    @DisplayName("Converts minor units to Money object in LKR")
    void toMoneyLkr_createsValidMoneyObject() {
        Money money = MoneyHelper.toMoneyLkr(75000L); // 750.00 LKR
        assertThat(money.currency()).isEqualTo(Money.LKR);
        assertThat(money.amount()).isEqualByComparingTo("750.00");
    }

    @Test
    @DisplayName("Converts minor units with custom currency")
    void toMoney_customCurrency() {
        Money money = MoneyHelper.toMoney(12050L, "USD");
        assertThat(money.currency()).isEqualTo("USD");
        assertThat(money.amount()).isEqualByComparingTo("120.50");
    }
}

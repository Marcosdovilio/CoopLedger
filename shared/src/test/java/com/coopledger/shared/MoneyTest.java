package com.coopledger.shared;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1.00", "0.001", "1.230", "100000000000000000.00"})
    void rejectsInvalidAmounts(String value) {
        assertThatThrownBy(() -> Money.positive(new BigDecimal(value))).isInstanceOf(DomainException.class);
    }

    @Test void rejectsNull() { assertThatThrownBy(() -> Money.positive(null)).isInstanceOf(DomainException.class); }

    @Test void normalizesWithoutRounding() {
        assertThat(Money.positive(new BigDecimal("30"))).isEqualTo(new BigDecimal("30.00"));
        assertThat(Money.positive(Money.MAX)).isEqualTo(Money.MAX);
        assertThat(Money.positive(new BigDecimal("0.01"))).isEqualTo(new BigDecimal("0.01"));
    }
}

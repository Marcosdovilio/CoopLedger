package com.coopledger.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final BigDecimal MAX = new BigDecimal("99999999999999999.99");
    private Money() { }

    public static BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 2 || value.compareTo(MAX) > 0) {
            throw new DomainException("INVALID_AMOUNT", "O valor deve ser positivo, com até 17 dígitos inteiros e duas casas decimais.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}

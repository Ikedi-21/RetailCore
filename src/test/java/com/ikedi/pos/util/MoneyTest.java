package com.ikedi.pos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ikedi.pos.exception.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void roundUsesHalfUpAtScaleTwo() {
        assertEquals(new BigDecimal("0.13"), Money.round(new BigDecimal("0.125")));
    }

    @Test
    void roundDropsBelowHalf() {
        assertEquals(new BigDecimal("0.12"), Money.round(new BigDecimal("0.124")));
    }

    @Test
    void roundHandlesTheTaxExample() {
        assertEquals(new BigDecimal("25.00"), Money.round(new BigDecimal("24.99975")));
    }

    @Test
    void percentOfGivesExactResult() {
        assertEquals(new BigDecimal("75.00"), Money.percentOf(new BigDecimal("1000.00"), new BigDecimal("7.5")));
    }

    @Test
    void percentOfRoundsOnceAtTheEnd() {
        assertEquals(new BigDecimal("25.00"), Money.percentOf(new BigDecimal("333.33"), new BigDecimal("7.5")));
    }

    @Test
    void equalsIsScaleSensitiveSoWeUseCompareTo() {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2");
        assertNotEquals(a, b);
        assertEquals(0, a.compareTo(b));
    }

    @Test
    void parseKeepsTwoDecimals() {
        assertEquals(new BigDecimal("500.00"), Money.parse("500"));
        assertEquals(new BigDecimal("12.50"), Money.parse(" 12.5 "));
    }

    @Test
    void parseRejectsGarbageAndExtraDecimals() {
        assertThrows(ValidationException.class, () -> Money.parse("abc"));
        assertThrows(ValidationException.class, () -> Money.parse(""));
        assertThrows(ValidationException.class, () -> Money.parse(null));
        assertThrows(ValidationException.class, () -> Money.parse("10.005"));
    }

    @Test
    void formatShowsSymbolGroupingAndTwoDecimals() {
        assertEquals("\u20A61,075.00", Money.format(new BigDecimal("1075")));
        assertEquals("\u20A60.00", Money.format(Money.ZERO));
    }
}

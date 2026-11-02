package com.ikedi.pos.util;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void roundUsesHalfUpAtScaleTwo() {
        assertEquals(new BigDecimal("0.13"), Money.round(new BigDecimal("0.125")));
    }

    // in MoneyTest.java (values go here)
    @Test
    void percentOfGivesExactResult() {
        assertEquals(
            new BigDecimal("75.00"),                                          // expected
            Money.percentOf(new BigDecimal("1000.00"), new BigDecimal("7.5")) // input
        );
    }
}
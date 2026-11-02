package com.ikedi.pos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void roundUsesHalfUpAtScaleTwo() {
        assertEquals(new BigDecimal("0.13"), Money.round(new BigDecimal("0.125")));
    }
}
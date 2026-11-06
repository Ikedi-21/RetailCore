package com.ikedi.pos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikedi.pos.model.PerishableProduct;
import com.ikedi.pos.model.ProductStatus;
import com.ikedi.pos.model.StandardProduct;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Shared helpers for tests. */
public final class TestKit {

    public static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    public static final LocalDate PAST = LocalDate.of(2026, 1, 1);
    public static final LocalDate FUTURE = LocalDate.of(2027, 12, 31);

    private TestKit() {
    }

    public static Clock clock() {
        return Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);
    }

    public static BigDecimal bd(String text) {
        return new BigDecimal(text);
    }

    public static StandardProduct std(String sku, String price, String tax, String qty) {
        return new StandardProduct(sku, "Item " + sku, "General", "SUP-1", bd(price), bd("1.00"),
                bd(qty), bd("2"), bd(tax), ProductStatus.ACTIVE);
    }

    public static StandardProduct stdInCategory(String sku, String category, String price) {
        return new StandardProduct(sku, "Item " + sku, category, "SUP-1", bd(price), bd("1.00"),
                bd("100"), bd("2"), bd("0"), ProductStatus.ACTIVE);
    }

    public static PerishableProduct perishable(String sku, String price, String qty, LocalDate expiry) {
        return new PerishableProduct(sku, "Item " + sku, "Dairy", "SUP-1", bd(price), bd("1.00"),
                bd(qty), bd("2"), bd("0"), ProductStatus.ACTIVE, expiry);
    }

    /** Numeric comparison that ignores scale (2.0 equals 2). */
    public static void assertNum(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }
}

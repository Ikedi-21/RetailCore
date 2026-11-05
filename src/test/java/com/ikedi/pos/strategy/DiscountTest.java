package com.ikedi.pos.strategy;

import static com.ikedi.pos.TestKit.TODAY;
import static com.ikedi.pos.TestKit.bd;
import static com.ikedi.pos.TestKit.std;
import static com.ikedi.pos.TestKit.stdInCategory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Product;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DiscountTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 30);

    @Test
    void percentageOnAProduct() {
        PercentageDiscount d = new PercentageDiscount("D1", "10% off A1", START, END,
                PercentageDiscount.Target.PRODUCT, "A1", bd("10"));
        Product a1 = std("A1", "100.00", "0", "50");
        Product other = std("B1", "100.00", "0", "50");
        assertEquals(bd("20.00"), d.discountForLine(a1, bd("2"), a1.getSellingPrice()));
        assertEquals(bd("0.00"), d.discountForLine(other, bd("2"), other.getSellingPrice()));
    }

    @Test
    void percentageOnACategory() {
        PercentageDiscount d = new PercentageDiscount("D2", "5% off drinks", START, END,
                PercentageDiscount.Target.CATEGORY, "drinks", bd("5"));
        Product drink = stdInCategory("W1", "Drinks", "500.00");
        Product snack = stdInCategory("S1", "Snacks", "500.00");
        assertEquals(bd("50.00"), d.discountForLine(drink, bd("2"), drink.getSellingPrice()));
        assertEquals(bd("0.00"), d.discountForLine(snack, bd("2"), snack.getSellingPrice()));
    }

    @Test
    void buyTwoGetOneFree() {
        BuyXGetYFreeDiscount d = new BuyXGetYFreeDiscount("D3", "2+1", START, END, "A1", 2, 1);
        Product a1 = std("A1", "100.00", "0", "50");
        assertEquals(bd("0.00"), d.discountForLine(a1, bd("2"), a1.getSellingPrice()));
        assertEquals(bd("100.00"), d.discountForLine(a1, bd("3"), a1.getSellingPrice()));
        assertEquals(bd("200.00"), d.discountForLine(a1, bd("7"), a1.getSellingPrice())); // 2 full groups
    }

    @Test
    void bulkPriceAppliesAtTheThreshold() {
        BulkDiscount d = new BulkDiscount("D4", "bulk", START, END, "A1", bd("10"), bd("80.00"));
        Product a1 = std("A1", "100.00", "0", "50");
        assertEquals(bd("0.00"), d.discountForLine(a1, bd("9"), a1.getSellingPrice()));
        assertEquals(bd("200.00"), d.discountForLine(a1, bd("10"), a1.getSellingPrice()));
    }

    @Test
    void cartPercentNeedsTheMinimumSubtotal() {
        CartDiscount d = CartDiscount.percent("D5", "10% over 1000", START, END, bd("1000.00"), bd("10"));
        assertEquals(bd("100.00"), d.discountForCart(bd("1000.00")));
        assertEquals(bd("0.00"), d.discountForCart(bd("999.99")));
        assertEquals(DiscountStrategy.Scope.CART, d.scope());
    }

    @Test
    void cartFixedIsCappedAtTheSubtotal() {
        CartDiscount d = CartDiscount.fixed("D6", "150 off", START, END, bd("100.00"), bd("150.00"));
        assertEquals(bd("150.00"), d.discountForCart(bd("1500.00")));
        assertEquals(bd("120.00"), d.discountForCart(bd("120.00")));
    }

    @Test
    void ruleIsActiveOnlyWhenEnabledAndInsideItsDates() {
        BuyXGetYFreeDiscount d = new BuyXGetYFreeDiscount("D7", "x", START, END, "A1", 1, 1);
        assertTrue(d.isActive(TODAY));
        assertTrue(d.isActive(START));
        assertTrue(d.isActive(END));
        assertFalse(d.isActive(START.minusDays(1)));
        assertFalse(d.isActive(END.plusDays(1)));
        d.setEnabled(false);
        assertFalse(d.isActive(TODAY));
    }

    @Test
    void invalidRulesAreRejected() {
        assertThrows(ValidationException.class, () -> new BuyXGetYFreeDiscount("D8", "x", END, START, "A1", 1, 1));
        assertThrows(ValidationException.class, () -> new BuyXGetYFreeDiscount("D8", "x", START, END, "A1", 0, 1));
        assertThrows(ValidationException.class, () -> CartDiscount.percent("D9", "x", START, END, BigDecimal.ZERO, bd("150")));
        assertThrows(ValidationException.class, () -> new BulkDiscount(" ", "x", START, END, "A1", bd("5"), bd("1")));
    }
}

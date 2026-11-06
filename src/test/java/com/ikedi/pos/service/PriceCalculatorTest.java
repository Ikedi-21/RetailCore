package com.ikedi.pos.service;

import static com.ikedi.pos.TestKit.TODAY;
import static com.ikedi.pos.TestKit.bd;
import static com.ikedi.pos.TestKit.std;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikedi.pos.model.PricedCart;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.service.PriceCalculator.PricedLine;
import com.ikedi.pos.strategy.BulkDiscount;
import com.ikedi.pos.strategy.CartDiscount;
import com.ikedi.pos.strategy.DiscountStrategy;
import com.ikedi.pos.strategy.PercentageDiscount;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 30);
    private final PriceCalculator calculator = new PriceCalculator();

    private static PricedLine line(Product p, String qty) {
        return new PricedLine(p, bd(qty));
    }

    @Test
    void taxIsRoundedPerLine() {
        Product p = std("A1", "333.33", "7.5", "10");
        PricedCart cart = calculator.price(List.of(line(p, "1")), List.of(), TODAY);
        assertEquals(bd("333.33"), cart.subtotal());
        assertEquals(bd("25.00"), cart.taxTotal());
        assertEquals(bd("358.33"), cart.total());
    }

    @Test
    void worksTheSpecificationExampleWithMixedTaxRates() {
        Product a = std("A1", "1000.00", "7.5", "10");
        Product b = std("B1", "500.00", "0", "10");
        DiscountStrategy cart150 = CartDiscount.fixed("CART1", "150 off over 1000", START, END, bd("1000.00"), bd("150.00"));

        PricedCart cart = calculator.price(List.of(line(a, "1"), line(b, "1")), List.of(cart150), TODAY);

        assertEquals(bd("1500.00"), cart.subtotal());
        assertEquals(bd("150.00"), cart.discountTotal());
        assertEquals(bd("67.50"), cart.taxTotal());
        assertEquals(bd("1417.50"), cart.total());
        assertEquals(bd("100.00"), cart.items().get(0).cartDiscountShare());
        assertEquals(bd("50.00"), cart.items().get(1).cartDiscountShare());
        assertEquals(bd("900.00"), cart.items().get(0).taxableAmount());
        assertEquals(bd("450.00"), cart.items().get(1).taxableAmount());
    }

    @Test
    void lastLineAbsorbsTheRoundingRemainderOfACartDiscount() {
        Product a = std("A1", "100.00", "0", "10");
        Product b = std("B1", "100.00", "0", "10");
        Product c = std("C1", "100.00", "0", "10");
        DiscountStrategy fixed = CartDiscount.fixed("CART1", "100 off", START, END, bd("0"), bd("100.00"));

        PricedCart cart = calculator.price(List.of(line(a, "1"), line(b, "1"), line(c, "1")), List.of(fixed), TODAY);

        assertEquals(bd("33.33"), cart.items().get(0).cartDiscountShare());
        assertEquals(bd("33.33"), cart.items().get(1).cartDiscountShare());
        assertEquals(bd("33.34"), cart.items().get(2).cartDiscountShare());
        assertEquals(bd("100.00"), cart.discountTotal());
        assertEquals(bd("200.00"), cart.total());
    }

    @Test
    void discountsDoNotStackTheBestSingleLineDiscountWins() {
        Product a = std("A1", "100.00", "0", "100");
        DiscountStrategy tenPercent = new PercentageDiscount("PCT", "10%", START, END,
                PercentageDiscount.Target.PRODUCT, "A1", bd("10"));
        DiscountStrategy bulk = new BulkDiscount("BULK", "bulk", START, END, "A1", bd("10"), bd("85.00"));

        // 10 units: 10% = 100.00, bulk = 150.00, bulk wins and they do not add up
        PricedCart cart = calculator.price(List.of(line(a, "10")), List.of(tenPercent, bulk), TODAY);
        assertEquals(bd("150.00"), cart.discountTotal());
        assertEquals(bd("850.00"), cart.total());
        assertEquals("BULK", cart.items().get(0).discountLabel());
    }

    @Test
    void aTieGoesToTheLowerRuleId() {
        Product a = std("A1", "100.00", "0", "100");
        DiscountStrategy z = new PercentageDiscount("Z-RULE", "10%", START, END,
                PercentageDiscount.Target.PRODUCT, "A1", bd("10"));
        DiscountStrategy b = new PercentageDiscount("B-RULE", "10%", START, END,
                PercentageDiscount.Target.PRODUCT, "A1", bd("10"));
        PricedCart cart = calculator.price(List.of(line(a, "1")), List.of(z, b), TODAY);
        assertEquals("B-RULE", cart.items().get(0).discountLabel());
    }

    @Test
    void inactiveRulesAreIgnored() {
        Product a = std("A1", "100.00", "0", "100");
        DiscountStrategy expired = new PercentageDiscount("OLD", "old", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), PercentageDiscount.Target.PRODUCT, "A1", bd("50"));
        DiscountStrategy disabled = new PercentageDiscount("OFF", "off", START, END,
                PercentageDiscount.Target.PRODUCT, "A1", bd("50"));
        disabled.setEnabled(false);
        PricedCart cart = calculator.price(List.of(line(a, "1")), List.of(expired, disabled), TODAY);
        assertEquals(bd("0.00"), cart.discountTotal());
        assertEquals(bd("100.00"), cart.total());
    }

    @Test
    void cartMinimumIsTestedAfterLineDiscounts() {
        Product a = std("A1", "100.00", "0", "100");
        DiscountStrategy half = new PercentageDiscount("PCT", "50%", START, END,
                PercentageDiscount.Target.PRODUCT, "A1", bd("50"));
        DiscountStrategy cartRule = CartDiscount.percent("CART", "10% over 1000", START, END, bd("1000.00"), bd("10"));
        // 20 units = 2000.00 before discounts, 1000.00 after the 50% line discount (meets the minimum)
        PricedCart met = calculator.price(List.of(line(a, "20")), List.of(half, cartRule), TODAY);
        assertEquals(bd("1100.00"), met.discountTotal());
        // 19 units = 1900.00, 950.00 after the line discount (does not meet the minimum)
        PricedCart notMet = calculator.price(List.of(line(a, "19")), List.of(half, cartRule), TODAY);
        assertEquals(bd("950.00"), notMet.discountTotal());
    }
}

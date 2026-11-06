package com.ikedi.pos.model;

import java.math.BigDecimal;

/**
 * Immutable snapshot of one sale line at the moment of sale, so later price or name changes
 * never rewrite history. Includes the cost price for the profit report.
 */
public record SaleItem(String sku, String name, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal costPrice, BigDecimal taxRatePercent, BigDecimal lineSubtotal,
                       BigDecimal lineDiscount, BigDecimal cartDiscountShare, BigDecimal taxableAmount,
                       BigDecimal taxAmount, BigDecimal lineTotal, String discountLabel) {

    public BigDecimal totalDiscount() {
        return lineDiscount.add(cartDiscountShare);
    }
}

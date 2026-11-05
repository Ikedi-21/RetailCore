package com.ikedi.pos.model;

import com.ikedi.pos.exception.InvalidQuantityException;
import java.math.BigDecimal;

/** Sold by whole units. */
public class StandardProduct extends Product {

    public StandardProduct(String sku, String name, String category, String supplierId,
                           BigDecimal sellingPrice, BigDecimal costPrice, BigDecimal quantity,
                           BigDecimal reorderLevel, BigDecimal taxRatePercent, ProductStatus status) {
        super(sku, name, category, supplierId, sellingPrice, costPrice, quantity,
                reorderLevel, taxRatePercent, status);
    }

    @Override
    public ProductType type() {
        return ProductType.STANDARD;
    }

    @Override
    protected void validateQuantity(BigDecimal quantity) {
        if (quantity.stripTrailingZeros().scale() > 0) {
            throw new InvalidQuantityException("Quantity for " + getSku() + " must be a whole number");
        }
    }
}

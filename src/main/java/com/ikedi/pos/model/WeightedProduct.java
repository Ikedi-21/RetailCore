package com.ikedi.pos.model;

import com.ikedi.pos.exception.InvalidQuantityException;
import java.math.BigDecimal;
import java.util.Objects;

/** Sold by weight in a fixed unit (kilograms or grams); decimal quantities allowed. */
public class WeightedProduct extends Product {

    private static final int MAX_DECIMALS = 3;

    private final WeightUnit unit;

    public WeightedProduct(String sku, String name, String category, String supplierId,
                           BigDecimal sellingPrice, BigDecimal costPrice, BigDecimal quantity,
                           BigDecimal reorderLevel, BigDecimal taxRatePercent, ProductStatus status,
                           WeightUnit unit) {
        super(sku, name, category, supplierId, sellingPrice, costPrice, quantity,
                reorderLevel, taxRatePercent, status);
        this.unit = Objects.requireNonNull(unit, "unit");
    }

    public WeightUnit getUnit() {
        return unit;
    }

    @Override
    public ProductType type() {
        return ProductType.WEIGHTED;
    }

    @Override
    protected void validateQuantity(BigDecimal quantity) {
        if (quantity.stripTrailingZeros().scale() > MAX_DECIMALS) {
            throw new InvalidQuantityException("Quantity for " + getSku() + " can have at most "
                    + MAX_DECIMALS + " decimal places");
        }
    }
}

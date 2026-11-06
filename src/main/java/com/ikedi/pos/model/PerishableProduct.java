package com.ikedi.pos.model;

import com.ikedi.pos.exception.ExpiredProductException;
import com.ikedi.pos.exception.InvalidQuantityException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Sold by whole units and has an expiry date. A product is sellable on its expiry date
 * and expired the day after.
 */
public class PerishableProduct extends Product {

    private final LocalDate expiryDate;

    public PerishableProduct(String sku, String name, String category, String supplierId,
                             BigDecimal sellingPrice, BigDecimal costPrice, BigDecimal quantity,
                             BigDecimal reorderLevel, BigDecimal taxRatePercent, ProductStatus status,
                             LocalDate expiryDate) {
        super(sku, name, category, supplierId, sellingPrice, costPrice, quantity,
                reorderLevel, taxRatePercent, status);
        this.expiryDate = Objects.requireNonNull(expiryDate, "expiryDate");
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    @Override
    public ProductType type() {
        return ProductType.PERISHABLE;
    }

    @Override
    public boolean isExpired(LocalDate today) {
        return expiryDate.isBefore(today);
    }

    @Override
    protected void validateQuantity(BigDecimal quantity) {
        if (quantity.stripTrailingZeros().scale() > 0) {
            throw new InvalidQuantityException("Quantity for " + getSku() + " must be a whole number");
        }
    }

    @Override
    protected void validateShelfLife(LocalDate today) {
        if (isExpired(today)) {
            throw new ExpiredProductException("Product " + getSku() + " expired on " + expiryDate);
        }
    }
}

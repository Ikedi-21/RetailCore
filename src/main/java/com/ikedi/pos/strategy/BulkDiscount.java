package com.ikedi.pos.strategy;

import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A lower unit price for a product once the quantity reaches a threshold. */
public class BulkDiscount extends BaseDiscount {

    private final String sku;
    private final BigDecimal threshold;
    private final BigDecimal bulkUnitPrice;

    public BulkDiscount(String id, String description, LocalDate start, LocalDate end,
                        String sku, BigDecimal threshold, BigDecimal bulkUnitPrice) {
        super(id, description, start, end);
        if (sku == null || sku.isBlank()) {
            throw new ValidationException("A bulk rule needs a product SKU");
        }
        requirePositive(threshold, "Threshold");
        if (bulkUnitPrice == null || bulkUnitPrice.signum() < 0) {
            throw new ValidationException("Bulk unit price must be zero or more");
        }
        this.sku = sku.trim();
        this.threshold = threshold;
        this.bulkUnitPrice = bulkUnitPrice;
    }

    public String getSku() { return sku; }
    public BigDecimal getThreshold() { return threshold; }
    public BigDecimal getBulkUnitPrice() { return bulkUnitPrice; }

    @Override
    public Scope scope() { return Scope.LINE; }

    @Override
    public BigDecimal discountForLine(Product product, BigDecimal quantity, BigDecimal unitPrice) {
        if (!product.getSku().equals(sku) || quantity.compareTo(threshold) < 0
                || unitPrice.compareTo(bulkUnitPrice) <= 0) {
            return Money.ZERO;
        }
        return Money.round(unitPrice.subtract(bulkUnitPrice).multiply(quantity));
    }
}

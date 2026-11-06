package com.ikedi.pos.strategy;

import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Percentage off the line total for one product (by SKU) or one category. */
public class PercentageDiscount extends BaseDiscount {

    public enum Target { PRODUCT, CATEGORY }

    private final Target target;
    private final String key;
    private final BigDecimal percent;

    public PercentageDiscount(String id, String description, LocalDate start, LocalDate end,
                              Target target, String key, BigDecimal percent) {
        super(id, description, start, end);
        requirePositive(percent, "Percentage");
        if (percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ValidationException("Percentage cannot exceed 100");
        }
        if (key == null || key.isBlank()) {
            throw new ValidationException("A percentage discount needs a product SKU or a category");
        }
        this.target = target;
        this.key = key.trim();
        this.percent = percent;
    }

    public Target getTarget() { return target; }
    public String getKey() { return key; }
    public BigDecimal getPercent() { return percent; }

    @Override
    public Scope scope() { return Scope.LINE; }

    @Override
    public BigDecimal discountForLine(Product product, BigDecimal quantity, BigDecimal unitPrice) {
        boolean matches = target == Target.PRODUCT
                ? product.getSku().equals(key)
                : product.getCategory().equalsIgnoreCase(key);
        if (!matches) {
            return Money.ZERO;
        }
        return Money.percentOf(Money.round(unitPrice.multiply(quantity)), percent);
    }
}

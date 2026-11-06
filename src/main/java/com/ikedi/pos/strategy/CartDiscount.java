package com.ikedi.pos.strategy;

import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A percentage or fixed amount off the whole cart once the subtotal, after line discounts
 * (decision D7), reaches a minimum. Use {@link #percent} or {@link #fixed}.
 */
public class CartDiscount extends BaseDiscount {

    private final BigDecimal minimumSubtotal;
    private final BigDecimal percent;      // null for fixed
    private final BigDecimal fixedAmount;  // null for percentage

    private CartDiscount(String id, String description, LocalDate start, LocalDate end,
                         BigDecimal minimumSubtotal, BigDecimal percent, BigDecimal fixedAmount) {
        super(id, description, start, end);
        if (minimumSubtotal == null || minimumSubtotal.signum() < 0) {
            throw new ValidationException("Minimum subtotal must be zero or more");
        }
        this.minimumSubtotal = minimumSubtotal;
        this.percent = percent;
        this.fixedAmount = fixedAmount;
    }

    public static CartDiscount percent(String id, String description, LocalDate start, LocalDate end,
                                       BigDecimal minimumSubtotal, BigDecimal percent) {
        requirePositive(percent, "Percentage");
        if (percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ValidationException("Percentage cannot exceed 100");
        }
        return new CartDiscount(id, description, start, end, minimumSubtotal, percent, null);
    }

    public static CartDiscount fixed(String id, String description, LocalDate start, LocalDate end,
                                     BigDecimal minimumSubtotal, BigDecimal amount) {
        requirePositive(amount, "Amount");
        return new CartDiscount(id, description, start, end, minimumSubtotal, null, amount);
    }

    public BigDecimal getMinimumSubtotal() { return minimumSubtotal; }
    public BigDecimal getPercent() { return percent; }
    public BigDecimal getFixedAmount() { return fixedAmount; }

    @Override
    public Scope scope() { return Scope.CART; }

    @Override
    public BigDecimal discountForCart(BigDecimal discountedSubtotal) {
        if (discountedSubtotal.compareTo(minimumSubtotal) < 0) {
            return Money.ZERO;
        }
        if (percent != null) {
            return Money.percentOf(discountedSubtotal, percent);
        }
        return Money.round(fixedAmount.min(discountedSubtotal));
    }
}

package com.ikedi.pos.strategy;

import com.ikedi.pos.model.Product;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A discount rule. Each concrete class implements the calculation for one kind of discount;
 * the pricing code never branches on the kind. A rule has one scope: LINE rules look at a
 * single sale line, CART rules look at the discounted cart subtotal.
 */
public interface DiscountStrategy {

    enum Scope { LINE, CART }

    String id();

    String describe();

    Scope scope();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    /** Enabled and inside its start and end dates (both inclusive). */
    boolean isActive(LocalDate today);

    /** Discount amount for one line; zero when the rule does not apply. */
    default BigDecimal discountForLine(Product product, BigDecimal quantity, BigDecimal unitPrice) {
        return Money.ZERO;
    }

    /** Discount amount for the whole cart; zero when the rule does not apply. */
    default BigDecimal discountForCart(BigDecimal discountedSubtotal) {
        return Money.ZERO;
    }
}

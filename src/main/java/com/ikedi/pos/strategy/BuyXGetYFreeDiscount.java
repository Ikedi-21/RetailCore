package com.ikedi.pos.strategy;

import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Buy X, get Y free (decision D8): for every X+Y units in the cart, Y are free.
 * Example: buy 2 get 1 free, 7 units in the cart: 2 full groups, so 2 units are free.
 */
public class BuyXGetYFreeDiscount extends BaseDiscount {

    private final String sku;
    private final int buy;
    private final int free;

    public BuyXGetYFreeDiscount(String id, String description, LocalDate start, LocalDate end,
                                String sku, int buy, int free) {
        super(id, description, start, end);
        if (sku == null || sku.isBlank()) {
            throw new ValidationException("A buy-X-get-Y rule needs a product SKU");
        }
        if (buy < 1 || free < 1) {
            throw new ValidationException("Buy and free quantities must each be at least 1");
        }
        this.sku = sku.trim();
        this.buy = buy;
        this.free = free;
    }

    public String getSku() { return sku; }
    public int getBuy() { return buy; }
    public int getFree() { return free; }

    @Override
    public Scope scope() { return Scope.LINE; }

    @Override
    public BigDecimal discountForLine(Product product, BigDecimal quantity, BigDecimal unitPrice) {
        if (!product.getSku().equals(sku)) {
            return Money.ZERO;
        }
        BigDecimal groups = quantity.divideToIntegralValue(BigDecimal.valueOf((long) buy + free));
        BigDecimal freeUnits = groups.multiply(BigDecimal.valueOf(free));
        return Money.round(unitPrice.multiply(freeUnits));
    }
}

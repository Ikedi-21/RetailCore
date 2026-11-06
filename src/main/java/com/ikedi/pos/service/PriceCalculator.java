package com.ikedi.pos.service;

import com.ikedi.pos.model.PricedCart;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.model.SaleItem;
import com.ikedi.pos.strategy.DiscountStrategy;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Prices a cart. Calculation order (decision D5), identical for receipts and reports:
 * <ol>
 *   <li>per line: pick the single best active line discount (no stacking);
 *   <li>discounted subtotal = sum of line subtotals minus line discounts;
 *   <li>pick the single best active cart discount, tested against the discounted subtotal;
 *   <li>split the cart discount across lines in proportion to each line's discounted subtotal;
 *       the last line absorbs the rounding remainder (decision D6);
 *   <li>tax each line on its remaining taxable amount, rounded per line (decision D2).
 * </ol>
 */
public class PriceCalculator {

    public record PricedLine(Product product, BigDecimal quantity) {
    }

    private record Pick(String id, BigDecimal amount) {
    }

    public PricedCart price(List<PricedLine> lines, List<DiscountStrategy> discounts, LocalDate today) {
        int n = lines.size();
        if (n == 0) {
            return new PricedCart(List.of(), Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO);
        }
        BigDecimal[] subtotal = new BigDecimal[n];
        BigDecimal[] lineDiscount = new BigDecimal[n];
        BigDecimal[] discounted = new BigDecimal[n];
        String[] label = new String[n];
        BigDecimal discountedTotal = Money.ZERO;

        for (int i = 0; i < n; i++) {
            Product product = lines.get(i).product();
            BigDecimal quantity = lines.get(i).quantity();
            BigDecimal unit = product.getSellingPrice();
            subtotal[i] = Money.round(unit.multiply(quantity));

            Pick best = null;
            for (DiscountStrategy d : discounts) {
                if (d.scope() == DiscountStrategy.Scope.LINE && d.isActive(today)) {
                    BigDecimal amount = Money.round(d.discountForLine(product, quantity, unit));
                    if (amount.signum() > 0) {
                        best = better(best, new Pick(d.id(), amount));
                    }
                }
            }
            lineDiscount[i] = best == null ? Money.ZERO : best.amount().min(subtotal[i]);
            label[i] = best == null ? "" : best.id();
            discounted[i] = subtotal[i].subtract(lineDiscount[i]);
            discountedTotal = discountedTotal.add(discounted[i]);
        }

        Pick cartPick = null;
        for (DiscountStrategy d : discounts) {
            if (d.scope() == DiscountStrategy.Scope.CART && d.isActive(today)) {
                BigDecimal amount = Money.round(d.discountForCart(discountedTotal));
                if (amount.signum() > 0) {
                    cartPick = better(cartPick, new Pick(d.id(), amount));
                }
            }
        }
        BigDecimal cartDiscount = cartPick == null ? Money.ZERO : cartPick.amount().min(discountedTotal);

        BigDecimal[] share = new BigDecimal[n];
        BigDecimal allocated = Money.ZERO;
        for (int i = 0; i < n; i++) {
            if (i < n - 1) {
                share[i] = discountedTotal.signum() == 0 ? Money.ZERO
                        : cartDiscount.multiply(discounted[i]).divide(discountedTotal, Money.SCALE, Money.ROUNDING);
            } else {
                share[i] = cartDiscount.subtract(allocated).max(Money.ZERO).min(discounted[i]);
            }
            allocated = allocated.add(share[i]);
        }

        List<SaleItem> items = new ArrayList<>(n);
        BigDecimal subtotalSum = Money.ZERO;
        BigDecimal discountSum = Money.ZERO;
        BigDecimal taxSum = Money.ZERO;
        BigDecimal totalSum = Money.ZERO;
        for (int i = 0; i < n; i++) {
            Product p = lines.get(i).product();
            BigDecimal taxable = discounted[i].subtract(share[i]);
            BigDecimal tax = Money.percentOf(taxable, p.getTaxRatePercent());
            BigDecimal lineTotal = taxable.add(tax);
            String text = label[i];
            if (share[i].signum() > 0 && cartPick != null) {
                text = text.isEmpty() ? cartPick.id() : text + " + " + cartPick.id();
            }
            items.add(new SaleItem(p.getSku(), p.getName(), lines.get(i).quantity(), p.getSellingPrice(),
                    p.getCostPrice(), p.getTaxRatePercent(), subtotal[i], lineDiscount[i], share[i],
                    taxable, tax, lineTotal, text));
            subtotalSum = subtotalSum.add(subtotal[i]);
            discountSum = discountSum.add(lineDiscount[i]).add(share[i]);
            taxSum = taxSum.add(tax);
            totalSum = totalSum.add(lineTotal);
        }
        return new PricedCart(items, subtotalSum, discountSum, taxSum, totalSum);
    }

    /** Larger amount wins; on a tie the lower rule id wins, so the result is deterministic. */
    private static Pick better(Pick current, Pick candidate) {
        if (current == null) {
            return candidate;
        }
        int cmp = candidate.amount().compareTo(current.amount());
        if (cmp > 0 || (cmp == 0 && candidate.id().compareTo(current.id()) < 0)) {
            return candidate;
        }
        return current;
    }
}

package com.ikedi.pos.model;

import java.math.BigDecimal;
import java.util.List;

/** Result of pricing a set of lines: per-line snapshots plus the sale totals. */
public record PricedCart(List<SaleItem> items, BigDecimal subtotal, BigDecimal discountTotal,
                         BigDecimal taxTotal, BigDecimal total) {
}

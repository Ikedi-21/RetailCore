package com.ikedi.pos.observer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Published when a stock reduction leaves a product at or below its reorder level. */
public record LowStockEvent(String sku, String productName, BigDecimal quantity,
                            BigDecimal reorderLevel, LocalDateTime raisedAt) {
}

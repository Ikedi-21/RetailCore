package com.ikedi.pos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One immutable stock change. Append-only: no setters, and the repository interface offers
 * no update or delete. {@code quantity} is the signed change (a sale is negative), so the
 * sum of a product's movements always equals its stock change.
 */
public record StockMovement(String id, MovementType type, String sku, BigDecimal quantity,
                            String reason, String user, LocalDateTime timestamp, String saleId) {

    public StockMovement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}

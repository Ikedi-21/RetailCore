package com.ikedi.pos.model;

import com.ikedi.pos.exception.SaleStateException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A sale. While OPEN it holds sku-to-quantity lines that the checkout service keeps priced.
 * COMPLETED sales keep their item snapshots forever; VOIDED sales are never deleted.
 */
public class Sale {

    private final String id;
    private final String cashier;
    private final LocalDateTime createdAt;
    private SaleStatus status = SaleStatus.OPEN;
    private final Map<String, BigDecimal> lines = new LinkedHashMap<>();
    private List<SaleItem> items = List.of();
    private BigDecimal subtotal = Money.ZERO;
    private BigDecimal discountTotal = Money.ZERO;
    private BigDecimal taxTotal = Money.ZERO;
    private BigDecimal total = Money.ZERO;
    private PaymentMethod paymentMethod;
    private BigDecimal amountPaid = Money.ZERO;
    private BigDecimal change = Money.ZERO;
    private LocalDateTime completedAt;
    private String voidedBy;
    private LocalDateTime voidedAt;
    private String voidNote;

    public Sale(String id, String cashier, LocalDateTime createdAt) {
        this.id = id;
        this.cashier = cashier;
        this.createdAt = createdAt;
    }

    private void requireOpen() {
        if (status != SaleStatus.OPEN) {
            throw new SaleStateException("Sale " + id + " is " + status + " and can no longer be changed");
        }
    }

    public synchronized void setLine(String sku, BigDecimal quantity) {
        requireOpen();
        lines.put(sku, quantity);
    }

    public synchronized void removeLine(String sku) {
        requireOpen();
        if (lines.remove(sku) == null) {
            throw new ValidationException("Item " + sku + " is not in sale " + id);
        }
    }

    public synchronized Map<String, BigDecimal> lines() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(lines));
    }

    public synchronized void applyPricing(PricedCart cart) {
        requireOpen();
        this.items = List.copyOf(cart.items());
        this.subtotal = cart.subtotal();
        this.discountTotal = cart.discountTotal();
        this.taxTotal = cart.taxTotal();
        this.total = cart.total();
    }

    public synchronized void complete(PaymentMethod method, BigDecimal paid, BigDecimal change, LocalDateTime when) {
        requireOpen();
        this.paymentMethod = method;
        this.amountPaid = paid;
        this.change = change;
        this.completedAt = when;
        this.status = SaleStatus.COMPLETED;
    }

    public synchronized void markVoided(String by, LocalDateTime when, String note) {
        if (status != SaleStatus.COMPLETED) {
            throw new SaleStateException("Only a completed sale can be voided; sale " + id + " is " + status);
        }
        this.voidedBy = by;
        this.voidedAt = when;
        this.voidNote = note;
        this.status = SaleStatus.VOIDED;
    }

    public String getId() { return id; }
    public String getCashier() { return cashier; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public synchronized SaleStatus getStatus() { return status; }
    public synchronized List<SaleItem> getItems() { return items; }
    public synchronized BigDecimal getSubtotal() { return subtotal; }
    public synchronized BigDecimal getDiscountTotal() { return discountTotal; }
    public synchronized BigDecimal getTaxTotal() { return taxTotal; }
    public synchronized BigDecimal getTotal() { return total; }
    public synchronized PaymentMethod getPaymentMethod() { return paymentMethod; }
    public synchronized BigDecimal getAmountPaid() { return amountPaid; }
    public synchronized BigDecimal getChange() { return change; }
    public synchronized LocalDateTime getCompletedAt() { return completedAt; }
    public synchronized String getVoidedBy() { return voidedBy; }
    public synchronized LocalDateTime getVoidedAt() { return voidedAt; }
    public synchronized String getVoidNote() { return voidNote; }
}

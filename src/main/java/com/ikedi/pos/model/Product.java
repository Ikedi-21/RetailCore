package com.ikedi.pos.model;

import com.ikedi.pos.exception.InvalidQuantityException;
import com.ikedi.pos.exception.OutOfStockException;
import com.ikedi.pos.exception.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Shared data and behaviour for every product. Subclasses supply the rules that differ
 * (what quantities are valid, whether shelf life matters), so services never ask
 * "what type are you?".
 *
 * <p>Stock quantity is changed only through increaseQuantity and decreaseQuantity, which the
 * inventory service calls while holding that product's lock.
 */
public abstract class Product {

    private final String sku;
    private String name;
    private String category;
    private String supplierId;
    private BigDecimal sellingPrice;
    private BigDecimal costPrice;
    private BigDecimal quantity;
    private BigDecimal reorderLevel;
    private BigDecimal taxRatePercent;
    private ProductStatus status;

    protected Product(String sku, String name, String category, String supplierId,
                      BigDecimal sellingPrice, BigDecimal costPrice, BigDecimal quantity,
                      BigDecimal reorderLevel, BigDecimal taxRatePercent, ProductStatus status) {
        this.sku = requireText(sku, "SKU");
        this.name = requireText(name, "Name");
        this.category = requireText(category, "Category");
        this.supplierId = requireText(supplierId, "Supplier");
        this.sellingPrice = requireNonNegative(sellingPrice, "Selling price");
        this.costPrice = requireNonNegative(costPrice, "Cost price");
        this.quantity = requireNonNegative(quantity, "Quantity");
        this.reorderLevel = requireNonNegative(reorderLevel, "Reorder level");
        this.taxRatePercent = requireRate(taxRatePercent);
        this.status = Objects.requireNonNull(status, "status");
    }

    // ---- behaviour that differs by product type ----

    public abstract ProductType type();

    /** Type-specific rule for a quantity that is already known to be positive. */
    protected abstract void validateQuantity(BigDecimal quantity);

    /** Hook for products with a shelf life. Default: no restriction. */
    protected void validateShelfLife(LocalDate today) {
    }

    public boolean isExpired(LocalDate today) {
        return false;
    }

    // ---- shared rules ----

    /** Throws unless this product may be sold in the given quantity on the given day. */
    public final void validateSale(BigDecimal qty, LocalDate today) {
        if (status != ProductStatus.ACTIVE) {
            throw new ValidationException("Product " + sku + " is inactive and cannot be sold");
        }
        checkQuantity(qty);
        validateShelfLife(today);
    }

    /** Positive, and valid for this product type. */
    public final void checkQuantity(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new InvalidQuantityException("Quantity must be greater than zero");
        }
        validateQuantity(qty);
    }

    public boolean isLowStock() {
        return quantity.compareTo(reorderLevel) <= 0;
    }

    public void increaseQuantity(BigDecimal amount) {
        quantity = quantity.add(amount);
    }

    /** Removes stock. Stock never goes negative: on failure nothing changes. */
    public void decreaseQuantity(BigDecimal amount) {
        if (quantity.compareTo(amount) < 0) {
            throw new OutOfStockException("Not enough stock for " + sku + ": requested "
                    + amount.toPlainString() + ", available " + quantity.toPlainString());
        }
        quantity = quantity.subtract(amount);
    }

    // ---- getters and validated setters ----

    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getSupplierId() { return supplierId; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public BigDecimal getCostPrice() { return costPrice; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public BigDecimal getTaxRatePercent() { return taxRatePercent; }
    public ProductStatus getStatus() { return status; }

    public void setName(String name) { this.name = requireText(name, "Name"); }
    public void setCategory(String category) { this.category = requireText(category, "Category"); }
    public void setSupplierId(String supplierId) { this.supplierId = requireText(supplierId, "Supplier"); }
    public void setSellingPrice(BigDecimal price) { this.sellingPrice = requireNonNegative(price, "Selling price"); }
    public void setCostPrice(BigDecimal cost) { this.costPrice = requireNonNegative(cost, "Cost price"); }
    public void setReorderLevel(BigDecimal level) { this.reorderLevel = requireNonNegative(level, "Reorder level"); }
    public void setTaxRatePercent(BigDecimal rate) { this.taxRatePercent = requireRate(rate); }
    public void setStatus(ProductStatus status) { this.status = Objects.requireNonNull(status, "status"); }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(label + " is required");
        }
        return value.trim();
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) {
            throw new ValidationException(label + " must be zero or more");
        }
        return value;
    }

    private static BigDecimal requireRate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ValidationException("Tax rate must be between 0 and 100");
        }
        return rate;
    }
}

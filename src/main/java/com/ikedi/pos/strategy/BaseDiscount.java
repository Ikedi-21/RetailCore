package com.ikedi.pos.strategy;

import com.ikedi.pos.exception.ValidationException;
import java.time.LocalDate;

/** Shared id, description, date range and enabled flag for all concrete discounts. */
public abstract class BaseDiscount implements DiscountStrategy {

    private final String id;
    private final String description;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private volatile boolean enabled = true;

    protected BaseDiscount(String id, String description, LocalDate startDate, LocalDate endDate) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("A discount needs an id");
        }
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new ValidationException("A discount needs a start date and an end date on or after it");
        }
        this.id = id.trim();
        this.description = description == null ? this.id : description;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @Override
    public String id() { return id; }

    @Override
    public String describe() { return description; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDate getStartDate() { return startDate; }

    public LocalDate getEndDate() { return endDate; }

    @Override
    public boolean isActive(LocalDate today) {
        return enabled && !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    protected static void requirePositive(java.math.BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new ValidationException(label + " must be greater than zero");
        }
    }
}

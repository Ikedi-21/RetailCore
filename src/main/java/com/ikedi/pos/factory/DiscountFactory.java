package com.ikedi.pos.factory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.strategy.BulkDiscount;
import com.ikedi.pos.strategy.BuyXGetYFreeDiscount;
import com.ikedi.pos.strategy.CartDiscount;
import com.ikedi.pos.strategy.DiscountStrategy;
import com.ikedi.pos.strategy.PercentageDiscount;

/**
 * Rebuilds the correct DiscountStrategy subclass from a JSON object with a "type" field.
 *
 * <p>CartDiscount has two construction paths (percent / fixed). The factory detects which
 * one was used from the presence of "percent" vs "fixedAmount" in the stored JSON.
 */
public final class DiscountFactory {

    /** Values used as the "type" discriminator in the JSON file. */
    public enum DiscountType { PERCENTAGE, BUY_X_GET_Y, BULK, CART_PERCENT, CART_FIXED }

    private DiscountFactory() {
    }

    public static DiscountStrategy fromJson(JsonObject json, Gson gson) {
        String typeStr = json.get("type") == null ? null : json.get("type").getAsString();
        if (typeStr == null) {
            throw new ValidationException("Discount JSON is missing 'type'");
        }
        DiscountType type;
        try {
            type = DiscountType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown discount type: " + typeStr);
        }
        return switch (type) {
            case PERCENTAGE   -> gson.fromJson(json, PercentageDiscount.class);
            case BUY_X_GET_Y  -> gson.fromJson(json, BuyXGetYFreeDiscount.class);
            case BULK         -> gson.fromJson(json, BulkDiscount.class);
            case CART_PERCENT -> gson.fromJson(json, CartDiscount.class);
            case CART_FIXED   -> gson.fromJson(json, CartDiscount.class);
        };
    }

    public static JsonObject toJson(DiscountStrategy discount, Gson gson) {
        JsonElement element = gson.toJsonTree(discount);
        JsonObject obj = element.getAsJsonObject();
        String type;
        if (discount instanceof PercentageDiscount) {
            type = DiscountType.PERCENTAGE.name();
        } else if (discount instanceof BuyXGetYFreeDiscount) {
            type = DiscountType.BUY_X_GET_Y.name();
        } else if (discount instanceof BulkDiscount) {
            type = DiscountType.BULK.name();
        } else if (discount instanceof CartDiscount cd) {
            type = cd.getPercent() != null ? DiscountType.CART_PERCENT.name() : DiscountType.CART_FIXED.name();
        } else {
            throw new ValidationException("Unknown discount class: " + discount.getClass().getName());
        }
        obj.addProperty("type", type);
        return obj;
    }
}

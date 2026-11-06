package com.ikedi.pos.factory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.PerishableProduct;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.model.ProductType;
import com.ikedi.pos.model.StandardProduct;
import com.ikedi.pos.model.WeightedProduct;

/**
 * Rebuilds the correct Product subclass from a JSON object that contains a "type" field.
 *
 * <p>Why a factory? Gson can only deserialise into concrete classes. Because Product is
 * abstract and has three subclasses with different fields, the repository stores the type
 * discriminator and delegates reconstruction here (the Factory pattern required by the brief).
 */
public final class ProductFactory {

    private ProductFactory() {
    }

    /**
     * @param json the full JSON object from the file, with a "type" field
     * @param gson a fully configured Gson instance (with BigDecimal / LocalDate adapters)
     * @return the right subclass instance
     */
    public static Product fromJson(JsonObject json, Gson gson) {
        String typeStr = json.get("type") == null ? null : json.get("type").getAsString();
        if (typeStr == null) {
            throw new ValidationException("Product JSON is missing 'type'");
        }
        ProductType type;
        try {
            type = ProductType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown product type: " + typeStr);
        }
        return switch (type) {
            case STANDARD   -> gson.fromJson(json, StandardProduct.class);
            case WEIGHTED   -> gson.fromJson(json, WeightedProduct.class);
            case PERISHABLE -> gson.fromJson(json, PerishableProduct.class);
        };
    }

    /**
     * Wraps a Product in a JsonObject and injects the "type" discriminator so the
     * factory can reconstruct it on the next load.
     */
    public static JsonObject toJson(Product product, Gson gson) {
        JsonElement element = gson.toJsonTree(product);
        JsonObject obj = element.getAsJsonObject();
        obj.addProperty("type", product.type().name());
        return obj;
    }
}


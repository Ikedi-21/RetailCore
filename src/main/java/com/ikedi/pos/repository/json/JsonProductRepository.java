package com.ikedi.pos.repository.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ikedi.pos.factory.ProductFactory;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.repository.ProductRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists products as a JSON array. Each element has a "type" field so
 * {@link ProductFactory} can reconstruct the right subclass on load.
 */
public class JsonProductRepository implements ProductRepository {

    private final Path file;
    private final Map<String, Product> store = new ConcurrentHashMap<>();

    public JsonProductRepository(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        try {
            String raw = JsonFileStore.read(file);
            if ("{}".equals(raw.trim())) return;
            JsonArray array = JsonFileStore.GSON.fromJson(raw, JsonArray.class);
            if (array == null) return;
            for (JsonElement element : array) {
                Product p = ProductFactory.fromJson(element.getAsJsonObject(), JsonFileStore.GSON);
                store.put(p.getSku(), p);
            }
        } catch (Exception e) {
            JsonFileStore.handleCorrupt(file, e);
        }
    }

    private void persist() throws IOException {
        JsonArray array = new JsonArray();
        for (Product p : new TreeMap<>(store).values()) {
            array.add(ProductFactory.toJson(p, JsonFileStore.GSON));
        }
        JsonFileStore.write(file, JsonFileStore.GSON.toJson(array));
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        return Optional.ofNullable(store.get(sku));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(new TreeMap<>(store).values());
    }

    @Override
    public boolean existsBySku(String sku) {
        return store.containsKey(sku);
    }

    @Override
    public synchronized void save(Product product) {
        store.put(product.getSku(), product);
        try {
            persist();
        } catch (IOException e) {
            // Saving failed: undo the in-memory change and rethrow so the caller
            // knows the operation did not fully succeed.
            store.remove(product.getSku());
            throw new RuntimeException("Failed to save product " + product.getSku() + ": " + e.getMessage(), e);
        }
    }
}

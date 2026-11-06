package com.ikedi.pos.repository.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ikedi.pos.factory.DiscountFactory;
import com.ikedi.pos.repository.DiscountRepository;
import com.ikedi.pos.strategy.DiscountStrategy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists discount rules as a JSON array. Each element has a "type" discriminator
 * so {@link DiscountFactory} can rebuild the right subclass on load.
 */
public class JsonDiscountRepository implements DiscountRepository {

    private final Path file;
    private final Map<String, DiscountStrategy> store = new ConcurrentHashMap<>();

    public JsonDiscountRepository(Path file) {
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
                DiscountStrategy d = DiscountFactory.fromJson(element.getAsJsonObject(), JsonFileStore.GSON);
                store.put(d.id(), d);
            }
        } catch (Exception e) {
            JsonFileStore.handleCorrupt(file, e);
        }
    }

    private void persist() throws IOException {
        JsonArray array = new JsonArray();
        for (DiscountStrategy d : new TreeMap<>(store).values()) {
            JsonObject obj = DiscountFactory.toJson(d, JsonFileStore.GSON);
            array.add(obj);
        }
        JsonFileStore.write(file, JsonFileStore.GSON.toJson(array));
    }

    @Override
    public Optional<DiscountStrategy> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<DiscountStrategy> findAll() {
        return new ArrayList<>(new TreeMap<>(store).values());
    }

    @Override
    public synchronized void save(DiscountStrategy discount) {
        DiscountStrategy previous = store.put(discount.id(), discount);
        try {
            persist();
        } catch (IOException e) {
            if (previous == null) {
                store.remove(discount.id());
            } else {
                store.put(discount.id(), previous);
            }
            throw new RuntimeException("Failed to save discount " + discount.id() + ": " + e.getMessage(), e);
        }
    }
}


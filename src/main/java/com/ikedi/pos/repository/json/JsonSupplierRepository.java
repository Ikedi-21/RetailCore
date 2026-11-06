package com.ikedi.pos.repository.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.ikedi.pos.model.Supplier;
import com.ikedi.pos.repository.SupplierRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Persists suppliers as a JSON array. */
public class JsonSupplierRepository implements SupplierRepository {

    private final Path file;
    private final Map<String, Supplier> store = new ConcurrentHashMap<>();

    public JsonSupplierRepository(Path file) {
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
                Supplier s = JsonFileStore.GSON.fromJson(element, Supplier.class);
                store.put(s.getId(), s);
            }
        } catch (Exception e) {
            JsonFileStore.handleCorrupt(file, e);
        }
    }

    private void persist() throws IOException {
        JsonArray array = new JsonArray();
        for (Supplier s : new TreeMap<>(store).values()) {
            array.add(JsonFileStore.GSON.toJsonTree(s));
        }
        JsonFileStore.write(file, JsonFileStore.GSON.toJson(array));
    }

    @Override
    public Optional<Supplier> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Supplier> findAll() {
        return new ArrayList<>(new TreeMap<>(store).values());
    }

    @Override
    public synchronized void save(Supplier supplier) {
        Supplier previous = store.put(supplier.getId(), supplier);
        try {
            persist();
        } catch (IOException e) {
            if (previous == null) {
                store.remove(supplier.getId());
            } else {
                store.put(supplier.getId(), previous);
            }
            throw new RuntimeException("Failed to save supplier " + supplier.getId() + ": " + e.getMessage(), e);
        }
    }
}


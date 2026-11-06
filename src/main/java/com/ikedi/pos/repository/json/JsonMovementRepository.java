package com.ikedi.pos.repository.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.ikedi.pos.model.StockMovement;
import com.ikedi.pos.repository.MovementRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Persists stock movements as a JSON array. Movements are logically append-only
 * but the repository exposes {@link #removeByIds} as a narrow escape hatch used
 * exclusively by CheckoutService when a save fails and the stock change must be
 * rolled back (D12 gap fix).
 *
 * <p>Insertion order is preserved with LinkedHashMap so the JSON file reads
 * chronologically. The id counter always continues from the highest saved id (D14).
 */
public class JsonMovementRepository implements MovementRepository {

    private static final String PREFIX = "MOV-";

    private final Path file;
    // LinkedHashMap preserves insertion (chronological) order.
    private final Map<String, StockMovement> store = new LinkedHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    public JsonMovementRepository(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        try {
            String raw = JsonFileStore.read(file);
            if ("{}".equals(raw.trim())) return;
            JsonArray array = JsonFileStore.GSON.fromJson(raw, JsonArray.class);
            if (array == null) return;
            int max = 0;
            for (JsonElement element : array) {
                StockMovement m = JsonFileStore.GSON.fromJson(element, StockMovement.class);
                store.put(m.id(), m);
                max = Math.max(max, parseSeq(m.id()));
            }
            counter.set(max);
        } catch (Exception e) {
            JsonFileStore.handleCorrupt(file, e);
        }
    }

    private static int parseSeq(String id) {
        if (id != null && id.startsWith(PREFIX)) {
            try {
                return Integer.parseInt(id.substring(PREFIX.length()));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private synchronized void persist() throws IOException {
        JsonArray array = new JsonArray();
        for (StockMovement m : store.values()) {
            array.add(JsonFileStore.GSON.toJsonTree(m));
        }
        JsonFileStore.write(file, JsonFileStore.GSON.toJson(array));
    }

    @Override
    public synchronized void append(StockMovement movement) {
        store.put(movement.id(), movement);
        try {
            persist();
        } catch (IOException e) {
            store.remove(movement.id());
            throw new RuntimeException("Failed to append movement " + movement.id() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized List<StockMovement> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public String nextId() {
        return PREFIX + String.format("%06d", counter.incrementAndGet());
    }

    /**
     * Removes movements by id — only used by rollback logic in CheckoutService.
     * This is NOT part of the MovementRepository interface (movements are logically
     * immutable) but is a targeted safety valve for the gap described in D12.
     */
    @Override
    public synchronized void removeByIds(Set<String> ids) {
        ids.forEach(store::remove);
        try {
            persist();
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist after removing movements: " + e.getMessage(), e);
        }
    }
}


package com.ikedi.pos.repository.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.ikedi.pos.model.Sale;
import com.ikedi.pos.repository.SaleRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Persists COMPLETED and VOIDED sales as a JSON array. OPEN sales are never written
 * to disk (they live only in CheckoutService.openSales). Id counter starts at the
 * highest saved id on startup so ids always increase and never repeat (D14).
 */
public class JsonSaleRepository implements SaleRepository {

    private static final String PREFIX = "SALE-";

    private final Path file;
    private final Map<String, Sale> store = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    public JsonSaleRepository(Path file) {
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
                Sale s = JsonFileStore.GSON.fromJson(element, Sale.class);
                store.put(s.getId(), s);
                max = Math.max(max, parseSeq(s.getId()));
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

    private void persist() throws IOException {
        JsonArray array = new JsonArray();
        for (Sale s : new TreeMap<>(store).values()) {
            array.add(JsonFileStore.GSON.toJsonTree(s));
        }
        JsonFileStore.write(file, JsonFileStore.GSON.toJson(array));
    }

    @Override
    public Optional<Sale> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Sale> findAll() {
        return new ArrayList<>(new TreeMap<>(store).values());
    }

    @Override
    public synchronized void save(Sale sale) {
        Sale previous = store.put(sale.getId(), sale);
        try {
            persist();
        } catch (IOException e) {
            if (previous == null) {
                store.remove(sale.getId());
            } else {
                store.put(sale.getId(), previous);
            }
            throw new RuntimeException("Failed to save sale " + sale.getId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String nextId() {
        return PREFIX + String.format("%06d", counter.incrementAndGet());
    }
}


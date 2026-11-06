package com.ikedi.pos.repository.memory;

import com.ikedi.pos.model.StockMovement;
import com.ikedi.pos.repository.MovementRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryMovementRepository implements MovementRepository {

    // LinkedHashMap preserves insertion (chronological) order and supports removeByIds.
    private final Map<String, StockMovement> store = new LinkedHashMap<>();
    private final AtomicLong counter = new AtomicLong();

    @Override
    public synchronized void append(StockMovement movement) {
        store.put(movement.id(), movement);
    }

    @Override
    public synchronized List<StockMovement> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public String nextId() {
        return String.format("MOV-%06d", counter.incrementAndGet());
    }

    @Override
    public synchronized void removeByIds(Set<String> ids) {
        ids.forEach(store::remove);
    }
}

package com.ikedi.pos.repository.memory;

import com.ikedi.pos.model.StockMovement;
import com.ikedi.pos.repository.MovementRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryMovementRepository implements MovementRepository {

    private final List<StockMovement> store = new CopyOnWriteArrayList<>();
    private final AtomicLong counter = new AtomicLong();

    @Override
    public void append(StockMovement movement) {
        store.add(movement);
    }

    @Override
    public List<StockMovement> findAll() {
        return new ArrayList<>(store);
    }

    @Override
    public String nextId() {
        return String.format("MOV-%06d", counter.incrementAndGet());
    }
}

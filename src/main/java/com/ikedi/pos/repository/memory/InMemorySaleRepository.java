package com.ikedi.pos.repository.memory;

import com.ikedi.pos.model.Sale;
import com.ikedi.pos.repository.SaleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemorySaleRepository implements SaleRepository {

    private final Map<String, Sale> store = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();

    @Override
    public Optional<Sale> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Sale> findAll() {
        return new ArrayList<>(new TreeMap<>(store).values());
    }

    @Override
    public void save(Sale sale) {
        store.put(sale.getId(), sale);
    }

    @Override
    public String nextId() {
        return String.format("SALE-%06d", counter.incrementAndGet());
    }
}

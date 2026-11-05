package com.ikedi.pos.repository.memory;

import com.ikedi.pos.model.Supplier;
import com.ikedi.pos.repository.SupplierRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySupplierRepository implements SupplierRepository {

    private final Map<String, Supplier> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Supplier> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Supplier> findAll() {
        return new ArrayList<>(new TreeMap<>(store).values());
    }

    @Override
    public void save(Supplier supplier) {
        store.put(supplier.getId(), supplier);
    }
}


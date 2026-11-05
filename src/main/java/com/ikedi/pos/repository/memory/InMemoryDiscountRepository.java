package com.ikedi.pos.repository.memory;

import com.ikedi.pos.repository.DiscountRepository;
import com.ikedi.pos.strategy.DiscountStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDiscountRepository implements DiscountRepository {

    private final Map<String, DiscountStrategy> store = new ConcurrentHashMap<>();

    @Override
    public Optional<DiscountStrategy> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<DiscountStrategy> findAll() {
        return new ArrayList<>(new TreeMap<>(store).values());
    }

    @Override
    public void save(DiscountStrategy discount) {
        store.put(discount.id(), discount);
    }
}

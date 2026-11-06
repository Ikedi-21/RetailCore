package com.ikedi.pos.repository.memory;

import com.ikedi.pos.model.Product;
import com.ikedi.pos.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryProductRepository implements ProductRepository {

    private final Map<String, Product> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Product> findBySku(String sku) {
        return Optional.ofNullable(store.get(sku));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(new TreeMap<>(store).values());
    }

    @Override
    public void save(Product product) {
        store.put(product.getSku(), product);
    }

    @Override
    public boolean existsBySku(String sku) {
        return store.containsKey(sku);
    }
}

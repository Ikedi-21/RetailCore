package com.ikedi.pos.repository;

import com.ikedi.pos.model.Product;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findBySku(String sku);

    List<Product> findAll();

    /** Inserts or replaces the product with this SKU. */
    void save(Product product);

    boolean existsBySku(String sku);
}

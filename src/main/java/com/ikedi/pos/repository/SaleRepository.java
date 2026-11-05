package com.ikedi.pos.repository;

import com.ikedi.pos.model.Sale;
import java.util.List;
import java.util.Optional;

public interface SaleRepository {

    Optional<Sale> findById(String id);

    List<Sale> findAll();

    /** Inserts or replaces the sale with this id. */
    void save(Sale sale);

    /** Next unique sale and receipt id, for example SALE-000126. Continues after a restart. */
    String nextId();
}

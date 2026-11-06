package com.ikedi.pos.repository;

import com.ikedi.pos.model.Supplier;
import java.util.List;
import java.util.Optional;

public interface SupplierRepository {

    Optional<Supplier> findById(String id);

    List<Supplier> findAll();

    /** Inserts or replaces the supplier with this id. */
    void save(Supplier supplier);
}


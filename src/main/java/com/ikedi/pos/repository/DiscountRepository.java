package com.ikedi.pos.repository;

import com.ikedi.pos.strategy.DiscountStrategy;
import java.util.List;
import java.util.Optional;

public interface DiscountRepository {

    Optional<DiscountStrategy> findById(String id);

    List<DiscountStrategy> findAll();

    void save(DiscountStrategy discount);
}

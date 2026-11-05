package com.ikedi.pos.repository;

import com.ikedi.pos.model.StockMovement;
import java.util.List;

/** Stock movements are append-only: this interface deliberately has no update or delete. */
public interface MovementRepository {

    void append(StockMovement movement);

    List<StockMovement> findAll();

    String nextId();
}

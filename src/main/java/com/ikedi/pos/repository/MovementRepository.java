package com.ikedi.pos.repository;

import com.ikedi.pos.model.StockMovement;
import java.util.List;
import java.util.Set;

/**
 * Stock movements are logically append-only. The one exception is {@link #removeByIds},
 * which exists solely for rollback when a sale-completion save fails after stock was
 * already deducted (the gap described in D12). No other caller should use it.
 */
public interface MovementRepository {

    void append(StockMovement movement);

    List<StockMovement> findAll();

    String nextId();

    /** Removes movements by id. Used only for rollback; see {@link #append}. */
    void removeByIds(Set<String> ids);
}

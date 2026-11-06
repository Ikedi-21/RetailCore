package com.ikedi.pos.observer;

/** Observer contract: the inventory service announces stock events and does not know who listens. */
public interface StockListener {

    void onLowStock(LowStockEvent event);

    /** Stock rose back above the reorder level. */
    default void onStockRecovered(String sku) {
    }
}

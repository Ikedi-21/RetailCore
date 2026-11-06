package com.ikedi.pos.observer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores low-stock alerts for managers to read later. It never prints, so a checkout is
 * never interrupted. One open alert per SKU (decision D16); it closes when stock recovers.
 */
public class AlertService implements StockListener {

    private final Map<String, LowStockEvent> open = new ConcurrentHashMap<>();

    @Override
    public void onLowStock(LowStockEvent event) {
        open.put(event.sku(), event);
    }

    @Override
    public void onStockRecovered(String sku) {
        open.remove(sku);
    }

    /** Open alerts, sorted by SKU. */
    public List<LowStockEvent> openAlerts() {
        List<LowStockEvent> list = new ArrayList<>(open.values());
        list.sort((a, b) -> a.sku().compareTo(b.sku()));
        return list;
    }
}

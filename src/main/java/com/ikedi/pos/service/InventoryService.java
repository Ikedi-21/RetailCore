package com.ikedi.pos.service;

import com.ikedi.pos.exception.OutOfStockException;
import com.ikedi.pos.exception.ProductNotFoundException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.MovementType;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.model.StockMovement;
import com.ikedi.pos.observer.LowStockEvent;
import com.ikedi.pos.observer.StockListener;
import com.ikedi.pos.repository.MovementRepository;
import com.ikedi.pos.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Owns every change to stock. Each product has its own lock. Operations touching several
 * products take the locks in ascending SKU order, so two cashiers can never wait on each
 * other in a circle (no deadlock). Every change appends one StockMovement. Alerts are
 * published only after the locks are released.
 */
public class InventoryService {

    private final ProductRepository products;
    final MovementRepository movements;
    private final Clock clock;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final List<StockListener> listeners = new CopyOnWriteArrayList<>();

    public InventoryService(ProductRepository products, MovementRepository movements, Clock clock) {
        this.products = products;
        this.movements = movements;
        this.clock = clock;
    }

    public void addListener(StockListener listener) {
        listeners.add(listener);
    }

    public Product getProduct(String sku) {
        return products.findBySku(sku).orElseThrow(() -> new ProductNotFoundException("Product not found: " + sku));
    }

    /** Runs the action while holding the locks of all given SKUs, taken in ascending order. */
    public <T> T withLocks(Collection<String> skus, Supplier<T> action) {
        List<ReentrantLock> held = new ArrayList<>();
        try {
            for (String sku : new TreeSet<>(skus)) {
                ReentrantLock lock = locks.computeIfAbsent(sku, k -> new ReentrantLock());
                lock.lock();
                held.add(lock);
                afterLockAcquired(sku);
            }
            return action.get();
        } finally {
            for (int i = held.size() - 1; i >= 0; i--) {
                held.get(i).unlock();
            }
        }
    }

    /** Extension point called after each lock is taken. Does nothing; tests override it to widen race windows. */
    protected void afterLockAcquired(String sku) {
    }

    /** Receives stock from a supplier: RESTOCK movement. */
    public void receiveStock(String sku, BigDecimal quantity, String reason, String user) {
        List<Runnable> after = new ArrayList<>();
        withLocks(List.of(sku), () -> {
            Product product = getProduct(sku);
            product.checkQuantity(quantity);
            applyDeltaLocked(product, quantity, MovementType.RESTOCK, requireReason(reason), user, null, after);
            return null;
        });
        runAfter(after);
    }

    /** Manual correction (damage, theft, count): ADJUSTMENT movement. A reason is required. */
    public void adjustStock(String sku, BigDecimal delta, String reason, String user) {
        if (delta == null || delta.signum() == 0) {
            throw new ValidationException("An adjustment must be a non-zero quantity");
        }
        String why = requireReason(reason);
        List<Runnable> after = new ArrayList<>();
        withLocks(List.of(sku), () -> {
            Product product = getProduct(sku);
            product.checkQuantity(delta.abs());
            applyDeltaLocked(product, delta, MovementType.ADJUSTMENT, why, user, null, after);
            return null;
        });
        runAfter(after);
    }

    /** Removes all remaining stock of an expired perishable: EXPIRED_WRITE_OFF movement. */
    public void writeOffExpired(String sku, String user) {
        List<Runnable> after = new ArrayList<>();
        withLocks(List.of(sku), () -> {
            Product product = getProduct(sku);
            LocalDate today = LocalDate.now(clock);
            if (!product.isExpired(today)) {
                throw new ValidationException("Product " + sku + " has not expired");
            }
            if (product.getQuantity().signum() == 0) {
                throw new ValidationException("Product " + sku + " has no stock to write off");
            }
            applyDeltaLocked(product, product.getQuantity().negate(), MovementType.EXPIRED_WRITE_OFF,
                    "Expired stock written off", user, null, after);
            return null;
        });
        runAfter(after);
    }

    // ---- used by CheckoutService, always called while holding the product's lock ----

    void validateAvailable(Product product, BigDecimal quantity, LocalDate today) {
        product.validateSale(quantity, today);
        if (product.getQuantity().compareTo(quantity) < 0) {
            throw new OutOfStockException("Not enough stock for " + product.getSku() + ": requested "
                    + quantity.toPlainString() + ", available " + product.getQuantity().toPlainString());
        }
    }

    /**
     * Applies a stock delta, saves the product and appends a movement.
     * Returns the id of the movement that was appended so callers can roll
     * it back if a later operation (e.g., persisting the sale) fails.
     */
    String applyDeltaLocked(Product product, BigDecimal delta, MovementType type, String reason,
                             String user, String saleId, List<Runnable> after) {
        if (delta.signum() < 0) {
            product.decreaseQuantity(delta.negate());
        } else {
            product.increaseQuantity(delta);
        }
        products.save(product);
        LocalDateTime now = LocalDateTime.now(clock);
        String movId = movements.nextId();
        movements.append(new StockMovement(movId, type, product.getSku(), delta, reason, user, now, saleId));

        if (delta.signum() < 0 && product.isLowStock()) {
            LowStockEvent event = new LowStockEvent(product.getSku(), product.getName(),
                    product.getQuantity(), product.getReorderLevel(), now);
            after.add(() -> listeners.forEach(l -> l.onLowStock(event)));
        } else if (delta.signum() > 0 && !product.isLowStock()) {
            String sku = product.getSku();
            after.add(() -> listeners.forEach(l -> l.onStockRecovered(sku)));
        }
        return movId;
    }

    void runAfter(List<Runnable> after) {
        after.forEach(Runnable::run);
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("A reason is required");
        }
        return reason.trim();
    }
}

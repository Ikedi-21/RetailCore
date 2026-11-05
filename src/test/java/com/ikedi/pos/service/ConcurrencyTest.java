package com.ikedi.pos.service;

import static com.ikedi.pos.TestKit.assertNum;
import static com.ikedi.pos.TestKit.bd;
import static com.ikedi.pos.TestKit.clock;
import static com.ikedi.pos.TestKit.std;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.exception.PosException;
import com.ikedi.pos.model.MovementType;
import com.ikedi.pos.model.PaymentMethod;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.model.Sale;
import com.ikedi.pos.model.SaleStatus;
import com.ikedi.pos.model.StockMovement;
import com.ikedi.pos.repository.memory.InMemoryDiscountRepository;
import com.ikedi.pos.repository.memory.InMemoryMovementRepository;
import com.ikedi.pos.repository.memory.InMemoryProductRepository;
import com.ikedi.pos.repository.memory.InMemorySaleRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Simulation: many cashier threads competing for limited stock. */
class ConcurrencyTest {

    /** Saving takes a moment, which widens the gap between "check stock" and "deduct stock". */
    private static final class SlowProductRepository extends InMemoryProductRepository {
        @Override
        public void save(Product product) {
            pause(1);
            super.save(product);
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final InMemoryProductRepository products = new SlowProductRepository();
    private final InMemorySaleRepository sales = new InMemorySaleRepository();
    private final InMemoryMovementRepository movements = new InMemoryMovementRepository();
    /** Pauses after each lock is taken, so opposite lock orders would reliably deadlock. */
    private final InventoryService inventory = new InventoryService(products, movements, clock()) {
        @Override
        protected void afterLockAcquired(String sku) {
            pause(5);
        }
    };
    private final CheckoutService checkout = new CheckoutService(products, sales,
            new InMemoryDiscountRepository(), inventory, new PriceCalculator(), clock());

    private BigDecimal sumOfMovements(String sku, MovementType type) {
        BigDecimal sum = BigDecimal.ZERO;
        for (StockMovement m : movements.findAll()) {
            if (m.sku().equals(sku) && m.type() == type) {
                sum = sum.add(m.quantity());
            }
        }
        return sum;
    }

    @Test
    void sixtyCashiersFightingForTenUnitsSellExactlyTen() throws Exception {
        products.save(std("LAST", "100.00", "0", "10"));
        int cashiers = 60;
        ExecutorService pool = Executors.newFixedThreadPool(cashiers);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger sold = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < cashiers; i++) {
            String cashier = "cashier" + i;
            futures.add(pool.submit(() -> {
                try {
                    go.await();
                    Sale sale = checkout.startSale(cashier);
                    checkout.addItem(sale.getId(), cashier, "LAST", bd("1"));
                    checkout.pay(sale.getId(), cashier, PaymentMethod.CASH, bd("100"));
                    sold.incrementAndGet();
                } catch (PosException e) {
                    rejected.incrementAndGet();           // out of stock: the expected loser outcome
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(10, sold.get());
        assertEquals(50, rejected.get());
        assertNum("0", products.findBySku("LAST").orElseThrow().getQuantity());           // never negative
        assertNum("-10", sumOfMovements("LAST", MovementType.SALE));                        // movements match sales
        long completed = sales.findAll().stream().filter(s -> s.getStatus() == SaleStatus.COMPLETED).count();
        assertEquals(10L, completed);
        // units sold + remaining stock == starting quantity
        assertNum("10", sumOfMovements("LAST", MovementType.SALE).negate()
                .add(products.findBySku("LAST").orElseThrow().getQuantity()));
    }

    @Test
    void multiProductSalesStayAllOrNothingWhenOneProductRunsOut() throws Exception {
        products.save(std("ABUNDANT", "10.00", "0", "1000"));
        products.save(std("SCARCE", "10.00", "0", "5"));
        int cashiers = 40;
        ExecutorService pool = Executors.newFixedThreadPool(cashiers);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger sold = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < cashiers; i++) {
            String cashier = "cashier" + i;
            futures.add(pool.submit(() -> {
                try {
                    go.await();
                    Sale sale = checkout.startSale(cashier);
                    checkout.addItem(sale.getId(), cashier, "ABUNDANT", bd("1"));
                    checkout.addItem(sale.getId(), cashier, "SCARCE", bd("1"));
                    checkout.pay(sale.getId(), cashier, PaymentMethod.CASH, bd("20"));
                    sold.incrementAndGet();
                } catch (PosException e) {
                    // sold out: this sale must leave NO partial deduction behind
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(5, sold.get());
        assertNum("0", products.findBySku("SCARCE").orElseThrow().getQuantity());
        assertNum("995", products.findBySku("ABUNDANT").orElseThrow().getQuantity());   // exactly 5 deducted, not more
        assertNum("-5", sumOfMovements("ABUNDANT", MovementType.SALE));
        assertNum("-5", sumOfMovements("SCARCE", MovementType.SALE));
    }

    @Test
    void opposingProductOrdersNeverDeadlock() throws Exception {
        products.save(std("AAA", "10.00", "0", "1000"));
        products.save(std("ZZZ", "10.00", "0", "1000"));
        int perDirection = 10;
        ExecutorService pool = Executors.newFixedThreadPool(perDirection * 2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger sold = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < perDirection * 2; i++) {
            boolean forward = i % 2 == 0;
            String cashier = "cashier" + i;
            futures.add(pool.submit(() -> {
                try {
                    go.await();
                    Sale sale = checkout.startSale(cashier);
                    String first = forward ? "AAA" : "ZZZ";
                    String second = forward ? "ZZZ" : "AAA";
                    checkout.addItem(sale.getId(), cashier, first, bd("1"));    // lines are added in opposite orders
                    checkout.addItem(sale.getId(), cashier, second, bd("1"));
                    checkout.pay(sale.getId(), cashier, PaymentMethod.CASH, bd("20"));
                    sold.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(20, TimeUnit.SECONDS);     // a deadlock would time out here and fail the test
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(perDirection * 2, sold.get());
        assertNum("980", products.findBySku("AAA").orElseThrow().getQuantity());
        assertNum("980", products.findBySku("ZZZ").orElseThrow().getQuantity());
        assertNum("-20", sumOfMovements("AAA", MovementType.SALE));
        assertNum("-20", sumOfMovements("ZZZ", MovementType.SALE));
    }
}

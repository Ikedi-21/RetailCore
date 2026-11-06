package com.ikedi.pos.demo;

import com.ikedi.pos.exception.OutOfStockException;
import com.ikedi.pos.model.PaymentMethod;
import com.ikedi.pos.model.ProductStatus;
import com.ikedi.pos.model.Sale;
import com.ikedi.pos.model.StandardProduct;
import com.ikedi.pos.repository.memory.InMemoryDiscountRepository;
import com.ikedi.pos.repository.memory.InMemoryMovementRepository;
import com.ikedi.pos.repository.memory.InMemoryProductRepository;
import com.ikedi.pos.repository.memory.InMemorySaleRepository;
import com.ikedi.pos.service.CheckoutService;
import com.ikedi.pos.service.InventoryService;
import com.ikedi.pos.service.PriceCalculator;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency and Deadlock-Freedom Demonstration for RetailCore.
 *
 * <p>Demonstrates two critical multi-user properties:
 * 1. <b>Zero Overselling under High Contention:</b> Multiple cashiers concurrently attempting
 *    to purchase remaining inventory. Exact atomic locking prevents overselling (decision D12).
 * 2. <b>Deadlock Freedom:</b> Cashiers checking out carts with overlapping products in reverse
 *    order. Natural lock sorting (via {@link java.util.TreeSet} in {@code InventoryService.withLocks})
 *    prevents circular-wait deadlocks.
 */
public class ConcurrencyDemo {

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("     RETAILCORE CONCURRENCY & DEADLOCK-FREEDOM DEMO");
        System.out.println("============================================================");
        boolean success = runAllScenarios(true);
        if (success) {
            System.out.println("============================================================");
            System.out.println("   ALL CONCURRENCY AND DEADLOCK-FREEDOM CHECKS PASSED!");
            System.out.println("============================================================");
        } else {
            System.err.println("============================================================");
            System.err.println("   DEMO FAILED: One or more concurrency invariants broken!");
            System.err.println("============================================================");
            System.exit(1);
        }
    }

    public static boolean runAllScenarios(boolean verbose) {
        boolean s1 = runScenario1HighContention(verbose);
        boolean s2 = runScenario2DeadlockFreedom(verbose);
        return s1 && s2;
    }

    /**
     * Scenario 1: 50 cashier threads make 150 concurrent purchase attempts for 100 units of stock.
     * Invariants:
     * - Exactly 100 sales succeed.
     * - Exactly 50 sales fail with OutOfStockException.
     * - Final stock is exactly 0.
     * - Zero overselling.
     */
    public static boolean runScenario1HighContention(boolean verbose) {
        if (verbose) {
            System.out.println("\n--- SCENARIO 1: High Contention on Single SKU (Zero Overselling) ---");
            System.out.println("Initial Stock: 100 units of 'PRD-RICE-50KG' (₦2,500.00 each)");
            System.out.println("Workload: 150 concurrent checkout attempts across 30 thread workers...");
        }

        Clock clock = Clock.systemDefaultZone();
        InMemoryProductRepository productRepo = new InMemoryProductRepository();
        InMemorySaleRepository saleRepo = new InMemorySaleRepository();
        InMemoryMovementRepository movementRepo = new InMemoryMovementRepository();
        InMemoryDiscountRepository discountRepo = new InMemoryDiscountRepository();

        String sku = "PRD-RICE-50KG";
        StandardProduct rice = new StandardProduct(
                sku, "Mama Gold Rice 50kg", "Groceries", "SUPP-01",
                new BigDecimal("2500.00"), new BigDecimal("2000.00"),
                new BigDecimal("100"), new BigDecimal("10"), BigDecimal.ZERO, ProductStatus.ACTIVE
        );
        productRepo.save(rice);

        InventoryService inventory = new InventoryService(productRepo, movementRepo, clock);
        PriceCalculator calculator = new PriceCalculator();
        CheckoutService checkout = new CheckoutService(productRepo, saleRepo, movementRepo, discountRepo, inventory, calculator, clock);

        int totalAttempts = 150;
        int threadPoolSize = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalAttempts);

        AtomicInteger successfulSales = new AtomicInteger(0);
        AtomicInteger outOfStockErrors = new AtomicInteger(0);
        AtomicInteger unexpectedErrors = new AtomicInteger(0);

        for (int i = 1; i <= totalAttempts; i++) {
            final String cashierId = "cashier-" + ((i % 10) + 1);
            executor.submit(() -> {
                try {
                    startGate.await(); // wait for simultaneous launch
                    Sale sale = checkout.startSale(cashierId);
                    checkout.addItem(sale.getId(), cashierId, sku, BigDecimal.ONE);
                    checkout.pay(sale.getId(), cashierId, PaymentMethod.CASH, new BigDecimal("2500.00"));
                    successfulSales.incrementAndGet();
                } catch (OutOfStockException e) {
                    outOfStockErrors.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrors.incrementAndGet();
                    if (verbose) {
                        System.err.println("Unexpected error: " + e.getMessage());
                    }
                } finally {
                    doneGate.countDown();
                }
            });
        }

        long startTime = System.currentTimeMillis();
        startGate.countDown(); // fire all threads simultaneously

        try {
            boolean finished = doneGate.await(10, TimeUnit.SECONDS);
            executor.shutdown();
            if (!finished) {
                if (verbose) System.err.println("[FAIL] Timeout: Workers did not complete within 10 seconds.");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        BigDecimal finalStock = inventory.getProduct(sku).getQuantity();
        long movementsCount = movementRepo.findAll().stream().filter(m -> m.sku().equals(sku)).count();

        boolean passed = (successfulSales.get() == 100)
                && (outOfStockErrors.get() == 50)
                && (unexpectedErrors.get() == 0)
                && (finalStock.compareTo(BigDecimal.ZERO) == 0)
                && (movementsCount == 100);

        if (verbose) {
            System.out.printf("Execution Time       : %d ms%n", elapsed);
            System.out.printf("Successful Sales     : %d (Expected: 100)%n", successfulSales.get());
            System.out.printf("Out-of-Stock Denials : %d (Expected: 50)%n", outOfStockErrors.get());
            System.out.printf("Unexpected Errors    : %d (Expected: 0)%n", unexpectedErrors.get());
            System.out.printf("Final Stock Remaining: %s (Expected: 0)%n", finalStock.toPlainString());
            System.out.printf("Stock Movements Log  : %d (Expected: 100)%n", movementsCount);
            if (passed) {
                System.out.println("[PASS] Scenario 1 verified: Zero overselling and atomic checkout under load.");
            } else {
                System.err.println("[FAIL] Scenario 1 failed to meet expectations.");
            }
        }

        return passed;
    }

    /**
     * Scenario 2: 40 concurrent cashiers purchasing cross-ordered pairs of products.
     * Cashier A buys [SKU-1, SKU-2]. Cashier B buys [SKU-2, SKU-1].
     * Invariants:
     * - No deadlocks occur because locks are acquired in sorted order (D12).
     * - All 40 transactions complete cleanly within timeout.
     * - Final inventory matches initial stock minus sold quantities.
     */
    public static boolean runScenario2DeadlockFreedom(boolean verbose) {
        if (verbose) {
            System.out.println("\n--- SCENARIO 2: Cross-SKU Ordering (Deadlock Prevention Test) ---");
            System.out.println("Products: 'SKU-A' (50 units) and 'SKU-B' (50 units)");
            System.out.println("Group 1: 20 cashiers add [SKU-A, SKU-B]");
            System.out.println("Group 2: 20 cashiers add [SKU-B, SKU-A]");
            System.out.println("Testing lock hierarchy (TreeSet ascending sort in InventoryService)...");
        }

        Clock clock = Clock.systemDefaultZone();
        InMemoryProductRepository productRepo = new InMemoryProductRepository();
        InMemorySaleRepository saleRepo = new InMemorySaleRepository();
        InMemoryMovementRepository movementRepo = new InMemoryMovementRepository();
        InMemoryDiscountRepository discountRepo = new InMemoryDiscountRepository();

        String skuA = "SKU-A";
        String skuB = "SKU-B";

        productRepo.save(new StandardProduct(
                skuA, "Product Alpha", "General", "SUPP-01",
                new BigDecimal("500.00"), new BigDecimal("300.00"),
                new BigDecimal("50"), new BigDecimal("5"), BigDecimal.ZERO, ProductStatus.ACTIVE
        ));

        productRepo.save(new StandardProduct(
                skuB, "Product Beta", "General", "SUPP-01",
                new BigDecimal("800.00"), new BigDecimal("500.00"),
                new BigDecimal("50"), new BigDecimal("5"), BigDecimal.ZERO, ProductStatus.ACTIVE
        ));

        InventoryService inventory = new InventoryService(productRepo, movementRepo, clock);
        PriceCalculator calculator = new PriceCalculator();
        CheckoutService checkout = new CheckoutService(productRepo, saleRepo, movementRepo, discountRepo, inventory, calculator, clock);

        int totalTransactions = 40;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalTransactions);

        AtomicInteger successfulTransactions = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < totalTransactions; i++) {
            final int index = i;
            final String cashierId = "cashier-" + ((i % 5) + 1);
            executor.submit(() -> {
                try {
                    startGate.await();
                    Sale sale = checkout.startSale(cashierId);
                    if (index % 2 == 0) {
                        // Order: A then B
                        checkout.addItem(sale.getId(), cashierId, skuA, BigDecimal.ONE);
                        checkout.addItem(sale.getId(), cashierId, skuB, BigDecimal.ONE);
                    } else {
                        // Order: B then A
                        checkout.addItem(sale.getId(), cashierId, skuB, BigDecimal.ONE);
                        checkout.addItem(sale.getId(), cashierId, skuA, BigDecimal.ONE);
                    }
                    checkout.pay(sale.getId(), cashierId, PaymentMethod.CASH, new BigDecimal("2000.00"));
                    successfulTransactions.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                    if (verbose) {
                        System.err.println("Scenario 2 error: " + e.getMessage());
                    }
                } finally {
                    doneGate.countDown();
                }
            });
        }

        long startTime = System.currentTimeMillis();
        startGate.countDown();

        try {
            boolean finished = doneGate.await(10, TimeUnit.SECONDS);
            executor.shutdown();
            if (!finished) {
                if (verbose) System.err.println("[FAIL] Deadlock detected! Threads failed to complete within 10 seconds.");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        BigDecimal finalStockA = inventory.getProduct(skuA).getQuantity();
        BigDecimal finalStockB = inventory.getProduct(skuB).getQuantity();

        // 40 transactions, each buying 1 of SKU-A and 1 of SKU-B: 50 - 40 = 10 units left
        BigDecimal expectedRemaining = new BigDecimal("10");
        boolean passed = (successfulTransactions.get() == 40)
                && (errors.get() == 0)
                && (finalStockA.compareTo(expectedRemaining) == 0)
                && (finalStockB.compareTo(expectedRemaining) == 0);

        if (verbose) {
            System.out.printf("Execution Time       : %d ms%n", elapsed);
            System.out.printf("Completed Sales      : %d / 40%n", successfulTransactions.get());
            System.out.printf("Errors Encountered   : %d%n", errors.get());
            System.out.printf("Stock Remaining '%s': %s (Expected: 10)%n", skuA, finalStockA.toPlainString());
            System.out.printf("Stock Remaining '%s': %s (Expected: 10)%n", skuB, finalStockB.toPlainString());
            if (passed) {
                System.out.println("[PASS] Scenario 2 verified: Lock ordering eliminated all deadlocks.");
            } else {
                System.err.println("[FAIL] Scenario 2 failed to meet expectations.");
            }
        }

        return passed;
    }
}

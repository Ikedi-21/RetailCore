package com.ikedi.pos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.model.PerishableProduct;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.model.StandardProduct;
import com.ikedi.pos.model.Supplier;
import com.ikedi.pos.model.WeightedProduct;
import com.ikedi.pos.repository.json.JsonDiscountRepository;
import com.ikedi.pos.repository.json.JsonProductRepository;
import com.ikedi.pos.repository.json.JsonSupplierRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeIfAbsentCopiesSeedFilesOnFirstRunOnly() throws IOException {
        DataInitializer init = new DataInitializer(tempDir);
        assertTrue(init.isFirstRun());

        boolean copied = init.initializeIfAbsent();
        assertTrue(copied);
        assertFalse(init.isFirstRun());

        assertTrue(Files.exists(tempDir.resolve("suppliers.json")));
        assertTrue(Files.exists(tempDir.resolve("categories.json")));
        assertTrue(Files.exists(tempDir.resolve("products.json")));
        assertTrue(Files.exists(tempDir.resolve("discounts.json")));

        // Modify a file to prove second run does NOT overwrite user changes
        Files.writeString(tempDir.resolve("products.json"), "[]");
        boolean secondRun = init.initializeIfAbsent();
        assertFalse(secondRun);
        assertEquals("[]", Files.readString(tempDir.resolve("products.json")));
    }

    @Test
    void seedDataCoversFifteenPlusProductsAndAllThreeTypes() throws IOException {
        DataInitializer init = new DataInitializer(tempDir);
        init.initializeIfAbsent();

        JsonProductRepository productRepo = new JsonProductRepository(tempDir.resolve("products.json"));
        List<Product> products = productRepo.findAll();

        assertTrue(products.size() >= 15, "Must have 15+ products, found " + products.size());

        boolean hasStandard = products.stream().anyMatch(p -> p instanceof StandardProduct);
        boolean hasWeighted = products.stream().anyMatch(p -> p instanceof WeightedProduct);
        boolean hasPerishable = products.stream().anyMatch(p -> p instanceof PerishableProduct);

        assertTrue(hasStandard, "Must contain STANDARD products");
        assertTrue(hasWeighted, "Must contain WEIGHTED products");
        assertTrue(hasPerishable, "Must contain PERISHABLE products");

        // Verify at least one low-stock product
        boolean hasLowStock = products.stream().anyMatch(Product::isLowStock);
        assertTrue(hasLowStock, "Must contain at least one low stock product");

        // Verify at least one expiring-soon product (relative to 2026-11-06)
        LocalDate testNow = LocalDate.of(2026, 11, 6);
        boolean hasExpiringSoon = products.stream()
                .filter(p -> p instanceof PerishableProduct)
                .map(p -> (PerishableProduct) p)
                .anyMatch(p -> !p.isExpired(testNow) && !p.getExpiryDate().isAfter(testNow.plusDays(5)));
        assertTrue(hasExpiringSoon, "Must contain at least one product expiring within 5 days");
    }

    @Test
    void seedSuppliersExistAndAllProductsReferenceValidSuppliers() throws IOException {
        DataInitializer init = new DataInitializer(tempDir);
        init.initializeIfAbsent();

        JsonSupplierRepository supplierRepo = new JsonSupplierRepository(tempDir.resolve("suppliers.json"));
        List<Supplier> suppliers = supplierRepo.findAll();
        assertTrue(suppliers.size() >= 3, "Expected at least 3 suppliers, found " + suppliers.size());

        Set<String> supplierIds = suppliers.stream().map(Supplier::getId).collect(Collectors.toSet());

        JsonProductRepository productRepo = new JsonProductRepository(tempDir.resolve("products.json"));
        for (Product product : productRepo.findAll()) {
            assertTrue(supplierIds.contains(product.getSupplierId()),
                    "Product " + product.getSku() + " has unknown supplier " + product.getSupplierId());
        }
    }

    @Test
    void seedDiscountsLoadCleanly() throws IOException {
        DataInitializer init = new DataInitializer(tempDir);
        init.initializeIfAbsent();

        JsonDiscountRepository discountRepo = new JsonDiscountRepository(tempDir.resolve("discounts.json"));
        assertFalse(discountRepo.findAll().isEmpty(), "Expected at least one seed discount");
    }
}

package com.ikedi.pos.service;

import com.ikedi.pos.exception.DuplicateSkuException;
import com.ikedi.pos.exception.ProductNotFoundException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.repository.ProductRepository;
import java.util.List;

/**
 * Creates and looks up products. The Product constructors already validate prices,
 * quantity and the type-specific rules, so this service adds only the rules that need
 * other data: unique SKU, and the supplier must exist.
 */
public class ProductService {

    private final ProductRepository products;
    private final SupplierService suppliers;

    public ProductService(ProductRepository products, SupplierService suppliers) {
        this.products = products;
        this.suppliers = suppliers;
    }

    /** SKUs are compared ignoring case, so "abc-1" and "ABC-1" are the same product. */
    public synchronized void add(Product product) {
        if (product == null) {
            throw new ValidationException("Product is required");
        }
        boolean taken = products.findAll().stream()
                .anyMatch(p -> p.getSku().equalsIgnoreCase(product.getSku()));
        if (taken) {
            throw new DuplicateSkuException("SKU already exists: " + product.getSku());
        }
        if (!suppliers.exists(product.getSupplierId())) {
            throw new ValidationException("Supplier not found: " + product.getSupplierId());
        }
        products.save(product);
    }

    public Product get(String sku) {
        return products.findBySku(sku == null ? "" : sku.trim())
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + sku));
    }

    public List<Product> list() {
        return products.findAll();
    }

    /** Saves a product that was changed through its setters. */
    public void update(Product product) {
        get(product.getSku());
        products.save(product);
    }
}


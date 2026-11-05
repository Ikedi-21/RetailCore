package com.ikedi.pos.service;

import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Supplier;
import com.ikedi.pos.repository.SupplierRepository;
import java.util.List;

public class SupplierService {

    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    /** Adds a supplier. Ids are compared ignoring case, so "sup-1" and "SUP-1" clash. */
    public synchronized Supplier add(String id, String name, String phone, String email) {
        Supplier supplier = new Supplier(id, name, phone, email);
        boolean taken = suppliers.findAll().stream()
                .anyMatch(s -> s.getId().equalsIgnoreCase(supplier.getId()));
        if (taken) {
            throw new ValidationException("Supplier id already exists: " + supplier.getId());
        }
        suppliers.save(supplier);
        return supplier;
    }

    public Supplier get(String id) {
        return suppliers.findById(id == null ? "" : id.trim())
                .orElseThrow(() -> new ValidationException("Supplier not found: " + id));
    }

    public boolean exists(String id) {
        return id != null && suppliers.findById(id.trim()).isPresent();
    }

    public List<Supplier> list() {
        return suppliers.findAll();
    }
}


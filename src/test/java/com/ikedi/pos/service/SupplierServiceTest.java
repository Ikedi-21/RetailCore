package com.ikedi.pos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Supplier;
import com.ikedi.pos.repository.memory.InMemorySupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplierServiceTest {

    private SupplierService service;

    @BeforeEach
    void setUp() {
        service = new SupplierService(new InMemorySupplierRepository());
    }

    @Test
    void addThenGetReturnsTheSupplier() {
        service.add("SUP-1", "Dangote Foods", "0803", "a@b.com");
        Supplier found = service.get("SUP-1");
        assertEquals("Dangote Foods", found.getName());
        assertTrue(service.exists("SUP-1"));
        assertEquals(1, service.list().size());
    }

    @Test
    void duplicateIdIsRejectedIgnoringCase() {
        service.add("SUP-1", "Dangote Foods", "", "");
        assertThrows(ValidationException.class, () -> service.add("sup-1", "Other", "", ""));
    }

    @Test
    void blankIdOrNameIsRejected() {
        assertThrows(ValidationException.class, () -> service.add(" ", "Name", "", ""));
        assertThrows(ValidationException.class, () -> service.add("SUP-2", " ", "", ""));
    }

    @Test
    void unknownSupplierIsReportedClearly() {
        assertFalse(service.exists("SUP-9"));
        assertThrows(ValidationException.class, () -> service.get("SUP-9"));
    }
}


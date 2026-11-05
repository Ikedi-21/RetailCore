package com.ikedi.pos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ikedi.pos.TestKit;
import com.ikedi.pos.exception.DuplicateSkuException;
import com.ikedi.pos.exception.InvalidQuantityException;
import com.ikedi.pos.exception.ProductNotFoundException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.model.ProductStatus;
import com.ikedi.pos.model.StandardProduct;
import com.ikedi.pos.repository.memory.InMemoryProductRepository;
import com.ikedi.pos.repository.memory.InMemorySupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductServiceTest {

    private ProductService service;

    @BeforeEach
    void setUp() {
        SupplierService suppliers = new SupplierService(new InMemorySupplierRepository());
        suppliers.add("SUP-1", "Dangote Foods", "", "");
        service = new ProductService(new InMemoryProductRepository(), suppliers);
    }

    @Test
    void addThenGetReturnsTheProduct() {
        service.add(TestKit.std("RICE-1", "100.00", "0", "10"));
        Product found = service.get("RICE-1");
        assertEquals("RICE-1", found.getSku());
        assertEquals(1, service.list().size());
    }

    @Test
    void duplicateSkuIsRejectedIncludingDifferentCase() {
        service.add(TestKit.std("RICE-1", "100.00", "0", "10"));
        assertThrows(DuplicateSkuException.class, () -> service.add(TestKit.std("RICE-1", "90.00", "0", "5")));
        assertThrows(DuplicateSkuException.class, () -> service.add(TestKit.std("rice-1", "90.00", "0", "5")));
        assertEquals(1, service.list().size());
    }

    @Test
    void unknownSupplierIsRejected() {
        Product orphan = new StandardProduct("X-1", "Item", "General", "SUP-9",
                TestKit.bd("10.00"), TestKit.bd("5.00"), TestKit.bd("1"), TestKit.bd("1"),
                TestKit.bd("0"), ProductStatus.ACTIVE);
        assertThrows(ValidationException.class, () -> service.add(orphan));
        assertEquals(0, service.list().size());
    }

    @Test
    void invalidDataIsRejectedBeforeItReachesTheService() {
        assertThrows(ValidationException.class, () -> new StandardProduct("X-1", "Item", "General", "SUP-1",
                TestKit.bd("-1"), TestKit.bd("5.00"), TestKit.bd("1"), TestKit.bd("1"),
                TestKit.bd("0"), ProductStatus.ACTIVE));
        assertThrows(ValidationException.class, () -> new StandardProduct("X-1", "Item", "General", "SUP-1",
                TestKit.bd("10"), TestKit.bd("5.00"), TestKit.bd("-3"), TestKit.bd("1"),
                TestKit.bd("0"), ProductStatus.ACTIVE));
    }

    @Test
    void standardProductRejectsFractionalQuantity() {
        service.add(TestKit.std("RICE-1", "100.00", "0", "10"));
        Product rice = service.get("RICE-1");
        assertThrows(InvalidQuantityException.class, () -> rice.checkQuantity(TestKit.bd("1.5")));
    }

    @Test
    void unknownSkuThrowsNotFound() {
        assertThrows(ProductNotFoundException.class, () -> service.get("NOPE"));
    }

    @Test
    void updateSavesChangesMadeThroughSetters() {
        service.add(TestKit.std("RICE-1", "100.00", "0", "10"));
        Product rice = service.get("RICE-1");
        rice.setSellingPrice(TestKit.bd("120.00"));
        service.update(rice);
        TestKit.assertNum("120.00", service.get("RICE-1").getSellingPrice());
    }
}


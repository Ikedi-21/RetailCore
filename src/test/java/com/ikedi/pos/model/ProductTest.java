package com.ikedi.pos.model;

import static com.ikedi.pos.TestKit.TODAY;
import static com.ikedi.pos.TestKit.assertNum;
import static com.ikedi.pos.TestKit.bd;
import static com.ikedi.pos.TestKit.perishable;
import static com.ikedi.pos.TestKit.std;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.exception.ExpiredProductException;
import com.ikedi.pos.exception.InvalidQuantityException;
import com.ikedi.pos.exception.OutOfStockException;
import com.ikedi.pos.exception.ValidationException;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void standardAcceptsWholeQuantities() {
        StandardProduct p = std("A1", "100.00", "0", "10");
        p.validateSale(bd("3"), TODAY);
        p.validateSale(bd("3.0"), TODAY);
    }

    @Test
    void standardRejectsFractionsZeroAndNegative() {
        StandardProduct p = std("A1", "100.00", "0", "10");
        assertThrows(InvalidQuantityException.class, () -> p.validateSale(bd("2.5"), TODAY));
        assertThrows(InvalidQuantityException.class, () -> p.validateSale(bd("0"), TODAY));
        assertThrows(InvalidQuantityException.class, () -> p.validateSale(bd("-1"), TODAY));
    }

    @Test
    void weightedAcceptsDecimalsUpToThreePlaces() {
        WeightedProduct p = new WeightedProduct("W1", "Rice", "Grains", "SUP-1", bd("800.00"), bd("600.00"),
                bd("50"), bd("5"), bd("0"), ProductStatus.ACTIVE, WeightUnit.KG);
        p.validateSale(bd("2.5"), TODAY);
        p.validateSale(bd("0.125"), TODAY);
        assertThrows(InvalidQuantityException.class, () -> p.validateSale(bd("1.2345"), TODAY));
        assertEquals(WeightUnit.KG, p.getUnit());
    }

    @Test
    void perishableIsSellableOnItsExpiryDateButNotAfter() {
        PerishableProduct onTheDay = perishable("P1", "50.00", "10", TODAY);
        onTheDay.validateSale(bd("1"), TODAY);
        assertFalse(onTheDay.isExpired(TODAY));

        PerishableProduct yesterday = perishable("P2", "50.00", "10", TODAY.minusDays(1));
        assertTrue(yesterday.isExpired(TODAY));
        assertThrows(ExpiredProductException.class, () -> yesterday.validateSale(bd("1"), TODAY));
        // even though the recorded stock is above zero
        assertNum("10", yesterday.getQuantity());
    }

    @Test
    void inactiveProductCannotBeSold() {
        StandardProduct p = std("A1", "100.00", "0", "10");
        p.setStatus(ProductStatus.INACTIVE);
        assertThrows(ValidationException.class, () -> p.validateSale(bd("1"), TODAY));
    }

    @Test
    void constructorRejectsBadData() {
        assertThrows(ValidationException.class, () -> std(" ", "100.00", "0", "10"));
        assertThrows(ValidationException.class, () -> std("A1", "-1.00", "0", "10"));
        assertThrows(ValidationException.class, () -> std("A1", "100.00", "101", "10"));
        assertThrows(ValidationException.class, () -> std("A1", "100.00", "0", "-5"));
    }

    @Test
    void stockNeverGoesNegative() {
        StandardProduct p = std("A1", "100.00", "0", "3");
        assertThrows(OutOfStockException.class, () -> p.decreaseQuantity(bd("4")));
        assertNum("3", p.getQuantity());
        p.decreaseQuantity(bd("3"));
        assertNum("0", p.getQuantity());
    }

    @Test
    void lowStockMeansAtOrBelowReorderLevel() {
        StandardProduct p = std("A1", "100.00", "0", "3"); // reorder level 2
        assertFalse(p.isLowStock());
        p.decreaseQuantity(bd("1"));
        assertTrue(p.isLowStock());
        assertEquals(ProductType.STANDARD, p.type());
    }
}

package com.ikedi.pos.service;

import static com.ikedi.pos.TestKit.FUTURE;
import static com.ikedi.pos.TestKit.PAST;
import static com.ikedi.pos.TestKit.assertNum;
import static com.ikedi.pos.TestKit.bd;
import static com.ikedi.pos.TestKit.clock;
import static com.ikedi.pos.TestKit.perishable;
import static com.ikedi.pos.TestKit.std;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.exception.OutOfStockException;
import com.ikedi.pos.exception.ProductNotFoundException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.MovementType;
import com.ikedi.pos.model.StockMovement;
import com.ikedi.pos.observer.AlertService;
import com.ikedi.pos.repository.memory.InMemoryMovementRepository;
import com.ikedi.pos.repository.memory.InMemoryProductRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InventoryServiceTest {

    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final InMemoryMovementRepository movements = new InMemoryMovementRepository();
    private final InventoryService inventory = new InventoryService(products, movements, clock());
    private final AlertService alerts = new AlertService();

    InventoryServiceTest() {
        inventory.addListener(alerts);
        products.save(std("A1", "100.00", "0", "10"));   // reorder level is 2
    }

    @Test
    void receivingStockIncreasesQuantityAndWritesARestockMovement() {
        inventory.receiveStock("A1", bd("5"), "Delivery from supplier", "manager");
        assertNum("15", products.findBySku("A1").orElseThrow().getQuantity());
        List<StockMovement> all = movements.findAll();
        assertEquals(1, all.size());
        assertEquals(MovementType.RESTOCK, all.get(0).type());
        assertNum("5", all.get(0).quantity());
        assertEquals("manager", all.get(0).user());
    }

    @Test
    void anAdjustmentNeedsAReason() {
        assertThrows(ValidationException.class, () -> inventory.adjustStock("A1", bd("-1"), " ", "manager"));
        assertThrows(ValidationException.class, () -> inventory.adjustStock("A1", bd("0"), "count", "manager"));
        assertEquals(0, movements.findAll().size());
    }

    @Test
    void anAdjustmentCannotTakeStockBelowZero() {
        assertThrows(OutOfStockException.class, () -> inventory.adjustStock("A1", bd("-11"), "theft", "manager"));
        assertNum("10", products.findBySku("A1").orElseThrow().getQuantity());
        assertEquals(0, movements.findAll().size());   // a rejected change leaves no movement
    }

    @Test
    void lowStockRaisesOneAlertAndRestockingClearsIt() {
        inventory.adjustStock("A1", bd("-8"), "damage", "manager");           // 2 left, at the reorder level
        assertEquals(1, alerts.openAlerts().size());
        assertEquals("A1", alerts.openAlerts().get(0).sku());
        inventory.adjustStock("A1", bd("-1"), "damage", "manager");           // still one alert, not two
        assertEquals(1, alerts.openAlerts().size());
        inventory.receiveStock("A1", bd("20"), "Delivery", "manager");        // well above the level again
        assertTrue(alerts.openAlerts().isEmpty());
    }

    @Test
    void expiredStockCanBeWrittenOff() {
        products.save(perishable("P1", "50.00", "6", PAST));
        products.save(perishable("P2", "50.00", "6", FUTURE));
        inventory.writeOffExpired("P1", "manager");
        assertNum("0", products.findBySku("P1").orElseThrow().getQuantity());
        assertEquals(MovementType.EXPIRED_WRITE_OFF, movements.findAll().get(0).type());
        assertNum("-6", movements.findAll().get(0).quantity());
        assertThrows(ValidationException.class, () -> inventory.writeOffExpired("P2", "manager"));
    }

    @Test
    void unknownProductsAreReported() {
        assertThrows(ProductNotFoundException.class, () -> inventory.receiveStock("NOPE", bd("1"), "x", "manager"));
    }

    @Test
    void movementIdsAreUnique() {
        inventory.receiveStock("A1", bd("1"), "one", "manager");
        inventory.receiveStock("A1", bd("1"), "two", "manager");
        inventory.receiveStock("A1", bd("1"), "three", "manager");
        Set<String> ids = new HashSet<>();
        movements.findAll().forEach(m -> ids.add(m.id()));
        assertEquals(3, ids.size());
    }
}

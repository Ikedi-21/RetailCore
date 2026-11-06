package com.ikedi.pos.service;

import static com.ikedi.pos.TestKit.FUTURE;
import static com.ikedi.pos.TestKit.PAST;
import static com.ikedi.pos.TestKit.TODAY;
import static com.ikedi.pos.TestKit.assertNum;
import static com.ikedi.pos.TestKit.bd;
import static com.ikedi.pos.TestKit.clock;
import static com.ikedi.pos.TestKit.perishable;
import static com.ikedi.pos.TestKit.std;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.exception.ExpiredProductException;
import com.ikedi.pos.exception.InsufficientPaymentException;
import com.ikedi.pos.exception.InvalidQuantityException;
import com.ikedi.pos.exception.OutOfStockException;
import com.ikedi.pos.exception.ProductNotFoundException;
import com.ikedi.pos.exception.SaleStateException;
import com.ikedi.pos.exception.UnauthorizedActionException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.MovementType;
import com.ikedi.pos.model.PaymentMethod;
import com.ikedi.pos.model.ProductStatus;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.Sale;
import com.ikedi.pos.model.SaleStatus;
import com.ikedi.pos.model.StockMovement;
import com.ikedi.pos.observer.AlertService;
import com.ikedi.pos.repository.memory.InMemoryDiscountRepository;
import com.ikedi.pos.repository.memory.InMemoryMovementRepository;
import com.ikedi.pos.repository.memory.InMemoryProductRepository;
import com.ikedi.pos.repository.memory.InMemorySaleRepository;
import com.ikedi.pos.strategy.PercentageDiscount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckoutServiceTest {

    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final InMemorySaleRepository sales = new InMemorySaleRepository();
    private final InMemoryMovementRepository movements = new InMemoryMovementRepository();
    private final InMemoryDiscountRepository discounts = new InMemoryDiscountRepository();
    private final InventoryService inventory = new InventoryService(products, movements, clock());
    private final AlertService alerts = new AlertService();
    private final CheckoutService checkout =
            new CheckoutService(products, sales, movements, discounts, inventory, new PriceCalculator(), clock());

    CheckoutServiceTest() {
        inventory.addListener(alerts);
        products.save(std("WTR001", "500.00", "7.5", "20"));
        products.save(std("A1", "100.00", "0", "5"));
        products.save(std("B1", "100.00", "0", "1"));
    }

    private int saleMovements() {
        return (int) movements.findAll().stream().filter(m -> m.type() == MovementType.SALE).count();
    }

    @Test
    void aCashSaleCalculatesTotalsChangeAndDeductsStock() {
        Sale sale = checkout.startSale("cashier01");
        checkout.addItem(sale.getId(), "cashier01", "WTR001", bd("2"));
        assertEquals(bd("1075.00"), sale.getTotal());               // running total while OPEN

        Sale done = checkout.pay(sale.getId(), "cashier01", PaymentMethod.CASH, bd("1500"));

        assertEquals(SaleStatus.COMPLETED, done.getStatus());
        assertEquals(bd("1000.00"), done.getSubtotal());
        assertEquals(bd("75.00"), done.getTaxTotal());
        assertEquals(bd("1075.00"), done.getTotal());
        assertEquals(bd("1500.00"), done.getAmountPaid());
        assertEquals(bd("425.00"), done.getChange());
        assertNum("18", products.findBySku("WTR001").orElseThrow().getQuantity());
        assertEquals(1, saleMovements());
        assertEquals(done, checkout.findSale(sale.getId()));
        assertEquals(1, checkout.completedSalesBy("cashier01", TODAY).size());
    }

    @Test
    void paymentBelowTheAmountDueIsRejectedAndNothingChanges() {
        Sale sale = checkout.startSale("cashier01");
        checkout.addItem(sale.getId(), "cashier01", "WTR001", bd("2"));
        assertThrows(InsufficientPaymentException.class,
                () -> checkout.pay(sale.getId(), "cashier01", PaymentMethod.CASH, bd("1000")));
        assertEquals(SaleStatus.OPEN, sale.getStatus());
        assertNum("20", products.findBySku("WTR001").orElseThrow().getQuantity());
        assertEquals(0, saleMovements());
        // and it can still be paid properly afterwards
        checkout.pay(sale.getId(), "cashier01", PaymentMethod.CASH, bd("1075"));
        assertEquals(SaleStatus.COMPLETED, sale.getStatus());
    }

    @Test
    void cardPaymentTakesExactlyTheTotal() {
        Sale sale = checkout.startSale("cashier01");
        checkout.addItem(sale.getId(), "cashier01", "A1", bd("1"));
        Sale done = checkout.pay(sale.getId(), "cashier01", PaymentMethod.CARD, null);
        assertEquals(bd("100.00"), done.getAmountPaid());
        assertEquals(bd("0.00"), done.getChange());
    }

    @Test
    void aSaleWithSeveralProductsIsAllOrNothing() {
        Sale first = checkout.startSale("cashier01");
        checkout.addItem(first.getId(), "cashier01", "A1", bd("2"));
        checkout.addItem(first.getId(), "cashier01", "B1", bd("1"));    // B1 has exactly one unit

        Sale rival = checkout.startSale("cashier02");
        checkout.addItem(rival.getId(), "cashier02", "B1", bd("1"));
        checkout.pay(rival.getId(), "cashier02", PaymentMethod.CASH, bd("100"));   // takes the last B1

        assertThrows(OutOfStockException.class,
                () -> checkout.pay(first.getId(), "cashier01", PaymentMethod.CASH, bd("1000")));

        assertNum("5", products.findBySku("A1").orElseThrow().getQuantity());      // A1 was NOT deducted
        assertEquals(SaleStatus.OPEN, first.getStatus());
        assertEquals(1, saleMovements());                                          // only the rival's sale
    }

    @Test
    void unsellableProductsAndBadQuantitiesAreRejectedWhenAdding() {
        products.save(perishable("P1", "50.00", "5", PAST));
        products.save(perishable("P2", "50.00", "5", FUTURE));
        var inactive = std("I1", "10.00", "0", "5");
        inactive.setStatus(ProductStatus.INACTIVE);
        products.save(inactive);
        Sale sale = checkout.startSale("cashier01");

        assertThrows(ExpiredProductException.class, () -> checkout.addItem(sale.getId(), "cashier01", "P1", bd("1")));
        assertThrows(ValidationException.class, () -> checkout.addItem(sale.getId(), "cashier01", "I1", bd("1")));
        assertThrows(InvalidQuantityException.class, () -> checkout.addItem(sale.getId(), "cashier01", "A1", bd("1.5")));
        assertThrows(InvalidQuantityException.class, () -> checkout.addItem(sale.getId(), "cashier01", "A1", bd("0")));
        assertThrows(OutOfStockException.class, () -> checkout.addItem(sale.getId(), "cashier01", "A1", bd("6")));
        assertThrows(ProductNotFoundException.class, () -> checkout.addItem(sale.getId(), "cashier01", "ZZZ", bd("1")));
        checkout.addItem(sale.getId(), "cashier01", "P2", bd("1"));    // a good perishable is fine
        assertEquals(1, sale.lines().size());
    }

    @Test
    void itemsCanBeRevisedAndRemovedWhileOpen() {
        Sale sale = checkout.startSale("cashier01");
        checkout.addItem(sale.getId(), "cashier01", "A1", bd("1"));
        checkout.addItem(sale.getId(), "cashier01", "A1", bd("1"));        // merges into one line
        assertNum("2", sale.lines().get("A1"));
        checkout.updateQuantity(sale.getId(), "cashier01", "A1", bd("4"));
        assertEquals(bd("400.00"), sale.getTotal());
        checkout.addItem(sale.getId(), "cashier01", "B1", bd("1"));
        checkout.removeItem(sale.getId(), "cashier01", "A1");
        assertEquals(bd("100.00"), sale.getTotal());
        assertThrows(ValidationException.class, () -> checkout.removeItem(sale.getId(), "cashier01", "A1"));
    }

    @Test
    void aCompletedSaleCannotBePaidOrChangedAgain() {
        Sale sale = checkout.startSale("cashier01");
        checkout.addItem(sale.getId(), "cashier01", "A1", bd("1"));
        checkout.pay(sale.getId(), "cashier01", PaymentMethod.CASH, bd("100"));
        assertThrows(SaleStateException.class,
                () -> checkout.pay(sale.getId(), "cashier01", PaymentMethod.CASH, bd("100")));
        assertThrows(SaleStateException.class, () -> checkout.addItem(sale.getId(), "cashier01", "A1", bd("1")));
        assertNum("4", products.findBySku("A1").orElseThrow().getQuantity());   // deducted exactly once
    }

    @Test
    void anEmptySaleCannotBePaidAndAnotherCashierCannotTouchIt() {
        Sale sale = checkout.startSale("cashier01");
        assertThrows(SaleStateException.class,
                () -> checkout.pay(sale.getId(), "cashier01", PaymentMethod.CASH, bd("100")));
        assertThrows(UnauthorizedActionException.class,
                () -> checkout.addItem(sale.getId(), "cashier02", "A1", bd("1")));
    }

    @Test
    void anActiveDiscountChangesTheTotal() {
        discounts.save(new PercentageDiscount("PCT10", "10% off A1", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), PercentageDiscount.Target.PRODUCT, "A1", bd("10")));
        Sale sale = checkout.startSale("cashier01");
        checkout.addItem(sale.getId(), "cashier01", "A1", bd("2"));
        Sale done = checkout.pay(sale.getId(), "cashier01", PaymentMethod.CASH, bd("180"));
        assertEquals(bd("200.00"), done.getSubtotal());
        assertEquals(bd("20.00"), done.getDiscountTotal());
        assertEquals(bd("180.00"), done.getTotal());
    }

    @Test
    void voidingReturnsStockKeepsTheSaleAndIsManagerOnly() {
        Sale sale = checkout.startSale("cashier01");
        checkout.addItem(sale.getId(), "cashier01", "A1", bd("3"));
        checkout.pay(sale.getId(), "cashier01", PaymentMethod.CASH, bd("300"));
        assertNum("2", products.findBySku("A1").orElseThrow().getQuantity());

        assertThrows(UnauthorizedActionException.class,
                () -> checkout.voidSale(sale.getId(), "cashier01", Role.CASHIER));

        Sale voided = checkout.voidSale(sale.getId(), "manager", Role.MANAGER);

        assertEquals(SaleStatus.VOIDED, voided.getStatus());
        assertEquals("manager", voided.getVoidedBy());
        assertEquals(1, voided.getItems().size());                    // original sale and items kept
        assertEquals(bd("300.00"), voided.getTotal());
        assertNum("5", products.findBySku("A1").orElseThrow().getQuantity());
        List<StockMovement> returns = movements.findAll().stream()
                .filter(m -> m.type() == MovementType.VOID_RETURN).toList();
        assertEquals(1, returns.size());
        assertNum("3", returns.get(0).quantity());
        assertEquals(0, checkout.completedSalesBy("cashier01", TODAY).size());   // voided sales are not "completed"

        assertThrows(SaleStateException.class, () -> checkout.voidSale(sale.getId(), "manager", Role.MANAGER));
    }

    @Test
    void anOpenOrUnknownSaleCannotBeVoided() {
        Sale open = checkout.startSale("cashier01");
        assertThrows(SaleStateException.class, () -> checkout.voidSale(open.getId(), "manager", Role.MANAGER));
        assertThrows(SaleStateException.class, () -> checkout.voidSale("SALE-999999", "manager", Role.MANAGER));
    }

    @Test
    void voidingRestocksAPerishableThatIsStillInDate() {
        products.save(perishable("P3", "50.00", "5", TODAY));          // expires today, still sellable today
        Sale sale = checkout.startSale("cashier01");
        checkout.addItem(sale.getId(), "cashier01", "P3", bd("2"));
        checkout.pay(sale.getId(), "cashier01", PaymentMethod.CASH, bd("100"));
        checkout.voidSale(sale.getId(), "manager", Role.MANAGER);
        assertNum("5", products.findBySku("P3").orElseThrow().getQuantity());
        assertTrue(sales.findById(sale.getId()).orElseThrow().getVoidNote().isEmpty());
    }

    @Test
    void voidingDoesNotRestockPerishablesThatHaveSinceExpired() {
        products.save(perishable("P3", "50.00", "5", TODAY));
        Sale sale = checkout.startSale("cashier01");
        checkout.addItem(sale.getId(), "cashier01", "P3", bd("2"));
        checkout.pay(sale.getId(), "cashier01", PaymentMethod.CASH, bd("100"));

        // two days later the same stock is expired
        Clock later = Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC);
        InventoryService laterInventory = new InventoryService(products, movements, later);
        CheckoutService laterCheckout =
                new CheckoutService(products, sales, movements, discounts, laterInventory, new PriceCalculator(), later);
        Sale voided = laterCheckout.voidSale(sale.getId(), "manager", Role.MANAGER);

        assertEquals(SaleStatus.VOIDED, voided.getStatus());
        assertNum("3", products.findBySku("P3").orElseThrow().getQuantity());   // nothing returned
        assertTrue(voided.getVoidNote().contains("P3"));
        assertEquals(0L, movements.findAll().stream().filter(m -> m.type() == MovementType.VOID_RETURN).count());
    }

    @Test
    void lowStockAfterASaleRaisesAnAlertWithoutPrintingAnything() {
        Sale sale = checkout.startSale("cashier01");
        checkout.addItem(sale.getId(), "cashier01", "A1", bd("4"));         // 5 -> 1, reorder level 2
        checkout.pay(sale.getId(), "cashier01", PaymentMethod.CASH, bd("400"));
        assertEquals(1, alerts.openAlerts().size());
        assertEquals("A1", alerts.openAlerts().get(0).sku());
    }

    @Test
    void savingFailureRollsBackStockAndRemovesMovements() {
        InMemorySaleRepository failingSales = new InMemorySaleRepository() {
            @Override
            public void save(Sale s) {
                throw new RuntimeException("Disk full simulated");
            }
        };
        CheckoutService failingCheckout = new CheckoutService(products, failingSales, movements, discounts, inventory, new PriceCalculator(), clock());
        Sale sale = failingCheckout.startSale("cashier01");
        failingCheckout.addItem(sale.getId(), "cashier01", "WTR001", bd("2"));
        BigDecimal beforeStock = products.findBySku("WTR001").orElseThrow().getQuantity();
        int beforeMovements = movements.findAll().size();

        assertThrows(RuntimeException.class, () ->
                failingCheckout.pay(sale.getId(), "cashier01", PaymentMethod.CASH, bd("1500")));

        assertEquals(beforeStock, products.findBySku("WTR001").orElseThrow().getQuantity());
        assertEquals(beforeMovements, movements.findAll().size());
    }
}

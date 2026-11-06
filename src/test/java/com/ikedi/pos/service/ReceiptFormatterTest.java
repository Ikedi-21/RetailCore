package com.ikedi.pos.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.TestKit;
import com.ikedi.pos.model.PaymentMethod;
import com.ikedi.pos.model.PricedCart;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.Sale;
import com.ikedi.pos.model.SaleItem;
import com.ikedi.pos.model.SaleStatus;
import com.ikedi.pos.repository.memory.InMemoryDiscountRepository;
import com.ikedi.pos.repository.memory.InMemoryProductRepository;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReceiptFormatterTest {

    private ReceiptFormatter formatterWithSymbol;
    private ReceiptFormatter formatterWithNgn;
    private Sale sale;

    @BeforeEach
    void setUp() {
        formatterWithSymbol = new ReceiptFormatter(true);
        formatterWithNgn = new ReceiptFormatter(false);

        sale = new Sale("SALE-000001", "cashier01", LocalDateTime.of(2026, 11, 3, 10, 0));
        Product rice = TestKit.std("RICE-1", "1000.00", "7.5", "50");

        PriceCalculator calc = new PriceCalculator();
        PricedCart cart = calc.price(List.of(new PriceCalculator.PricedLine(rice, new BigDecimal("2"))),
                List.of(), TestKit.TODAY);

        sale.setLine("RICE-1", new BigDecimal("2"));
        sale.applyPricing(cart);
        sale.complete(PaymentMethod.CASH, new BigDecimal("2500.00"), new BigDecimal("350.00"),
                LocalDateTime.of(2026, 11, 3, 10, 5));
    }

    @Test
    void receiptContainsStoreAndTransactionDetails() {
        String receipt = formatterWithSymbol.format(sale);

        assertTrue(receipt.contains("RetailCore Supermarket"));
        assertTrue(receipt.contains("SALE-000001"));
        assertTrue(receipt.contains("cashier01"));
        assertTrue(receipt.contains("RICE-1"));
        assertTrue(receipt.contains("TOTAL DUE"));
        assertTrue(receipt.contains("Payment (CASH)"));
        assertTrue(receipt.contains("Change Given"));
    }

    @Test
    void receiptSupportsNairaSymbolAndNgnFallback() {
        String withSymbol = formatterWithSymbol.format(sale);
        String withNgn = formatterWithNgn.format(sale);

        assertTrue(withSymbol.contains("\u20A6"), "Expected Naira sign in formatted receipt");
        assertFalse(withSymbol.contains("NGN"));

        assertTrue(withNgn.contains("NGN"), "Expected NGN fallback in formatted receipt");
        assertFalse(withNgn.contains("\u20A6"));
    }

    @Test
    void voidedSaleDisplaysProminentVoidHeader() {
        sale.markVoided("manager01", LocalDateTime.of(2026, 11, 3, 11, 0), "Customer return");
        String receipt = formatterWithSymbol.format(sale);

        assertTrue(receipt.contains("*** SALE VOIDED ***"));
        assertTrue(receipt.contains("Voided by: manager01"));
        assertTrue(receipt.contains("Note: Customer return"));
    }
}

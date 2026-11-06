package com.ikedi.pos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.TestKit;
import com.ikedi.pos.model.PaymentMethod;
import com.ikedi.pos.model.PerishableProduct;
import com.ikedi.pos.model.PricedCart;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.model.ProductStatus;
import com.ikedi.pos.model.Sale;
import com.ikedi.pos.model.StandardProduct;
import com.ikedi.pos.repository.memory.InMemoryProductRepository;
import com.ikedi.pos.repository.memory.InMemorySaleRepository;
import com.ikedi.pos.strategy.PercentageDiscount;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private InMemorySaleRepository saleRepo;
    private InMemoryProductRepository productRepo;
    private ReportService reportService;
    private PriceCalculator calculator;

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 11, 3);

    @BeforeEach
    void setUp() {
        saleRepo = new InMemorySaleRepository();
        productRepo = new InMemoryProductRepository();
        reportService = new ReportService(saleRepo, productRepo, TestKit.clock(), false);
        calculator = new PriceCalculator();
    }

    private Sale createCompletedSale(String id, PaymentMethod method, LocalDateTime when,
                                     Product product, BigDecimal qty) {
        Sale sale = new Sale(id, "cashier1", when);
        sale.setLine(product.getSku(), qty);
        PricedCart cart = calculator.price(
                List.of(new PriceCalculator.PricedLine(product, qty)),
                List.of(),
                when.toLocalDate());
        sale.applyPricing(cart);
        sale.complete(method, cart.total(), Money.ZERO, when);
        saleRepo.save(sale);
        return sale;
    }

    @Test
    void dailySummaryCalculatesTotalsAndPaymentBreakdown() {
        Product p1 = TestKit.std("P1", "100.00", "0", "100");
        Product p2 = TestKit.std("P2", "200.00", "0", "100");
        productRepo.save(p1);
        productRepo.save(p2);

        LocalDateTime time1 = LocalDateTime.of(2026, 11, 3, 10, 0);
        LocalDateTime time2 = LocalDateTime.of(2026, 11, 3, 12, 0);
        LocalDateTime anotherDay = LocalDateTime.of(2026, 11, 4, 9, 0);

        createCompletedSale("S1", PaymentMethod.CASH, time1, p1, new BigDecimal("2")); // 200
        createCompletedSale("S2", PaymentMethod.CARD, time2, p2, new BigDecimal("1")); // 200
        createCompletedSale("S3", PaymentMethod.TRANSFER, anotherDay, p1, new BigDecimal("5")); // next day

        // A voided sale on REPORT_DATE must be excluded from daily summary
        Sale voided = createCompletedSale("S4", PaymentMethod.CASH, time1, p1, new BigDecimal("10"));
        voided.markVoided("manager", time1.plusMinutes(5), "Mistake");
        saleRepo.save(voided);

        ReportService.DailySalesSummary summary = reportService.getDailySalesSummary(REPORT_DATE);

        assertEquals(2, summary.transactionCount());
        assertEquals(new BigDecimal("3"), summary.totalQuantitySold());
        assertEquals(0, new BigDecimal("400.00").compareTo(summary.totalRevenue()));
        assertEquals(1, summary.paymentBreakdown().get(PaymentMethod.CASH).count());
        assertEquals(1, summary.paymentBreakdown().get(PaymentMethod.CARD).count());
        assertEquals(0, summary.paymentBreakdown().get(PaymentMethod.TRANSFER).count());

        String text = reportService.formatDailySalesSummary(REPORT_DATE);
        assertTrue(text.contains("Completed Transactions : 2"));
    }

    @Test
    void topProductsByQuantityAndRevenueOrderCorrectly() {
        Product cheap = TestKit.std("CHEAP", "10.00", "0", "100");
        Product pricey = TestKit.std("PRICEY", "1000.00", "0", "100");
        productRepo.save(cheap);
        productRepo.save(pricey);

        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 10, 0);
        // Cheap: 20 sold -> Qty 20, Revenue 200
        createCompletedSale("S1", PaymentMethod.CASH, now, cheap, new BigDecimal("20"));
        // Pricey: 2 sold -> Qty 2, Revenue 2000
        createCompletedSale("S2", PaymentMethod.CARD, now, pricey, new BigDecimal("2"));

        List<ReportService.ProductQuantityRanking> byQty = reportService.getTopProductsByQuantity(5);
        assertEquals(2, byQty.size());
        assertEquals("CHEAP", byQty.get(0).sku());
        assertEquals("PRICEY", byQty.get(1).sku());

        List<ReportService.ProductRevenueRanking> byRev = reportService.getTopProductsByRevenue(5);
        assertEquals(2, byRev.size());
        assertEquals("PRICEY", byRev.get(0).sku());
        assertEquals("CHEAP", byRev.get(1).sku());

        assertTrue(reportService.formatTopProductsByQuantity(5).contains("CHEAP"));
        assertTrue(reportService.formatTopProductsByRevenue(5).contains("PRICEY"));
    }

    @Test
    void lowStockReportFiltersProductsAtOrBelowReorderLevel() {
        Product low = new StandardProduct("LOW", "Low Item", "General", "SUP-1",
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("2"),
                new BigDecimal("5"), new BigDecimal("0"), ProductStatus.ACTIVE);
        Product ok = new StandardProduct("OK", "Normal Item", "General", "SUP-1",
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("20"),
                new BigDecimal("5"), new BigDecimal("0"), ProductStatus.ACTIVE);

        productRepo.save(low);
        productRepo.save(ok);

        List<ReportService.LowStockItem> lowItems = reportService.getLowStockProducts();
        assertEquals(1, lowItems.size());
        assertEquals("LOW", lowItems.get(0).sku());
        assertEquals(new BigDecimal("3"), lowItems.get(0).deficit()); // 5 - 2 = 3

        assertTrue(reportService.formatLowStockReport().contains("LOW"));
        assertFalse(reportService.formatLowStockReport().contains("OK"));
    }

    @Test
    void expiringSoonReportFiltersPerishablesWithinThreshold() {
        // Clock is fixed at 2026-11-03
        PerishableProduct expiresSoon = TestKit.perishable("EXP-SOON", "100.00", "10", LocalDate.of(2026, 11, 5)); // in 2 days
        PerishableProduct expiresFar = TestKit.perishable("EXP-FAR", "100.00", "10", LocalDate.of(2026, 12, 1));  // far
        PerishableProduct alreadyExpired = TestKit.perishable("EXP-PAST", "100.00", "10", LocalDate.of(2026, 11, 1)); // expired

        productRepo.save(expiresSoon);
        productRepo.save(expiresFar);
        productRepo.save(alreadyExpired);

        List<ReportService.ExpiringItem> list = reportService.getExpiringProducts(5);
        assertEquals(2, list.size()); // alreadyExpired and expiresSoon

        assertTrue(reportService.formatExpiringReport(5).contains("EXP-SOON"));
        assertTrue(reportService.formatExpiringReport(5).contains("EXP-PAST"));
        assertFalse(reportService.formatExpiringReport(5).contains("EXP-FAR"));
    }

    @Test
    void profitReportCalculatesPerD11ExcludingVoided() {
        // Product: Selling 150, Cost 100
        Product item = new StandardProduct("ITEM-1", "Test Item", "General", "SUP-1",
                new BigDecimal("150.00"), new BigDecimal("100.00"), new BigDecimal("50"),
                new BigDecimal("5"), new BigDecimal("0"), ProductStatus.ACTIVE);
        productRepo.save(item);

        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 10, 0);

        // Sale 1: 2 units. Revenue = 300, Cost = 200, Profit = 100
        createCompletedSale("S1", PaymentMethod.CASH, now, item, new BigDecimal("2"));

        // Sale 2 (Voided): 5 units. Must NOT be included in profit
        Sale voided = createCompletedSale("S2", PaymentMethod.CASH, now, item, new BigDecimal("5"));
        voided.markVoided("manager", now.plusMinutes(10), "Cancelled");
        saleRepo.save(voided);

        ReportService.ProfitReport report = reportService.getProfitReport(null, null);

        assertEquals(1, report.completedSalesCount());
        assertEquals(0, new BigDecimal("300.00").compareTo(report.totalRevenue()));
        assertEquals(0, new BigDecimal("200.00").compareTo(report.totalCost()));
        assertEquals(0, new BigDecimal("100.00").compareTo(report.grossProfit()));
        assertEquals(0, new BigDecimal("33.33").compareTo(report.profitMarginPercent())); // 100 / 300 = 33.33%

        String formatted = reportService.formatProfitReport(null, null);
        assertTrue(formatted.contains("Gross Profit"));
        assertTrue(formatted.contains("33.33%"));
    }
}


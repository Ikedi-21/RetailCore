package com.ikedi.pos.service;

import com.ikedi.pos.model.PaymentMethod;
import com.ikedi.pos.model.PerishableProduct;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.model.Sale;
import com.ikedi.pos.model.SaleItem;
import com.ikedi.pos.model.SaleStatus;
import com.ikedi.pos.repository.ProductRepository;
import com.ikedi.pos.repository.SaleRepository;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates management reports.
 *
 * <p>Six required reports (Step 4):
 * <ol>
 *   <li>Daily sales summary (transactions, totals, payment method breakdown)
 *   <li>Top 5 products by quantity sold
 *   <li>Top 5 products by revenue
 *   <li>Low-stock alert list (products at or below reorder level)
 *   <li>Expiring-soon list (perishables near or past expiration date)
 *   <li>Profit report (D11: revenue after discounts, before tax, minus qty * cost snapshot; voided sales excluded)
 * </ol>
 *
 * All dates use the injected {@link Clock} (decision D13).
 */
public class ReportService {

    private final SaleRepository sales;
    private final ProductRepository products;
    private final Clock clock;
    private final boolean useNairaSymbol;

    public ReportService(SaleRepository sales, ProductRepository products, Clock clock) {
        this(sales, products, clock, true);
    }

    public ReportService(SaleRepository sales, ProductRepository products, Clock clock, boolean useNairaSymbol) {
        this.sales = sales;
        this.products = products;
        this.clock = clock;
        this.useNairaSymbol = useNairaSymbol;
    }

    // =========================================================================
    // 1. Daily Sales Summary
    // =========================================================================

    public record PaymentBreakdown(PaymentMethod method, int count, BigDecimal total) {}

    public record DailySalesSummary(
            LocalDate date,
            int transactionCount,
            BigDecimal totalQuantitySold,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal taxTotal,
            BigDecimal totalRevenue,
            Map<PaymentMethod, PaymentBreakdown> paymentBreakdown
    ) {}

    public DailySalesSummary getDailySalesSummary(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(clock);
        List<Sale> completedOnDate = sales.findAll().stream()
                .filter(s -> s.getStatus() == SaleStatus.COMPLETED)
                .filter(s -> s.getCompletedAt() != null && s.getCompletedAt().toLocalDate().equals(targetDate))
                .toList();

        int txCount = completedOnDate.size();
        BigDecimal qtySum = BigDecimal.ZERO;
        BigDecimal subtotalSum = Money.ZERO;
        BigDecimal discountSum = Money.ZERO;
        BigDecimal taxSum = Money.ZERO;
        BigDecimal totalSum = Money.ZERO;

        Map<PaymentMethod, Integer> methodCounts = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, BigDecimal> methodTotals = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod m : PaymentMethod.values()) {
            methodCounts.put(m, 0);
            methodTotals.put(m, Money.ZERO);
        }

        for (Sale s : completedOnDate) {
            subtotalSum = subtotalSum.add(s.getSubtotal());
            discountSum = discountSum.add(s.getDiscountTotal());
            taxSum = taxSum.add(s.getTaxTotal());
            totalSum = totalSum.add(s.getTotal());

            for (SaleItem item : s.getItems()) {
                qtySum = qtySum.add(item.quantity());
            }

            PaymentMethod m = s.getPaymentMethod();
            if (m != null) {
                methodCounts.put(m, methodCounts.get(m) + 1);
                methodTotals.put(m, methodTotals.get(m).add(s.getTotal()));
            }
        }

        Map<PaymentMethod, PaymentBreakdown> breakdown = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod m : PaymentMethod.values()) {
            breakdown.put(m, new PaymentBreakdown(m, methodCounts.get(m), methodTotals.get(m)));
        }

        return new DailySalesSummary(targetDate, txCount, qtySum, subtotalSum, discountSum, taxSum, totalSum, breakdown);
    }

    public String formatDailySalesSummary(LocalDate date) {
        DailySalesSummary s = getDailySalesSummary(date);
        StringBuilder b = new StringBuilder();
        b.append("====================================================\n");
        b.append("             DAILY SALES SUMMARY: ").append(s.date()).append("\n");
        b.append("====================================================\n");
        b.append(String.format("Completed Transactions : %d\n", s.transactionCount()));
        b.append(String.format("Total Items Sold       : %s\n", s.totalQuantitySold().toPlainString()));
        b.append(String.format("Subtotal (Gross)       : %s\n", fmt(s.subtotal())));
        b.append(String.format("Total Discounts Given  : -%s\n", fmt(s.discountTotal())));
        b.append(String.format("VAT / Tax Collected    : %s\n", fmt(s.taxTotal())));
        b.append(String.format("Total Sales Revenue    : %s\n", fmt(s.totalRevenue())));
        b.append("----------------------------------------------------\n");
        b.append("PAYMENT BREAKDOWN:\n");
        for (PaymentBreakdown pb : s.paymentBreakdown().values()) {
            b.append(String.format("  %-10s : %3d tx  |  %s\n", pb.method(), pb.count(), fmt(pb.total())));
        }
        b.append("====================================================\n");
        return b.toString();
    }

    // =========================================================================
    // 2. Top 5 Products by Quantity
    // =========================================================================

    public record ProductQuantityRanking(String sku, String name, BigDecimal quantitySold, BigDecimal revenue) {}

    public List<ProductQuantityRanking> getTopProductsByQuantity(int limit) {
        Map<String, BigDecimal> qtyMap = new LinkedHashMap<>();
        Map<String, BigDecimal> revMap = new LinkedHashMap<>();
        Map<String, String> nameMap = new LinkedHashMap<>();

        for (Sale s : sales.findAll()) {
            if (s.getStatus() != SaleStatus.COMPLETED) continue;
            for (SaleItem item : s.getItems()) {
                qtyMap.put(item.sku(), qtyMap.getOrDefault(item.sku(), BigDecimal.ZERO).add(item.quantity()));
                revMap.put(item.sku(), revMap.getOrDefault(item.sku(), Money.ZERO).add(item.lineTotal()));
                nameMap.putIfAbsent(item.sku(), item.name());
            }
        }

        return qtyMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(e -> new ProductQuantityRanking(e.getKey(), nameMap.get(e.getKey()), e.getValue(), revMap.get(e.getKey())))
                .collect(Collectors.toList());
    }

    public String formatTopProductsByQuantity(int limit) {
        List<ProductQuantityRanking> list = getTopProductsByQuantity(limit);
        StringBuilder b = new StringBuilder();
        b.append("====================================================\n");
        b.append("            TOP ").append(limit).append(" PRODUCTS BY QUANTITY SOLD\n");
        b.append("====================================================\n");
        b.append(String.format("%-4s %-12s %-20s %8s %12s\n", "Rank", "SKU", "Name", "Qty Sold", "Revenue"));
        b.append("----------------------------------------------------\n");
        int rank = 1;
        for (ProductQuantityRanking r : list) {
            String name = r.name().length() > 20 ? r.name().substring(0, 18) + ".." : r.name();
            b.append(String.format("%-4d %-12s %-20s %8s %12s\n",
                    rank++, r.sku(), name, r.quantitySold().toPlainString(), fmt(r.revenue())));
        }
        if (list.isEmpty()) {
            b.append("No sales recorded yet.\n");
        }
        b.append("====================================================\n");
        return b.toString();
    }

    // =========================================================================
    // 3. Top 5 Products by Revenue
    // =========================================================================

    public record ProductRevenueRanking(String sku, String name, BigDecimal revenue, BigDecimal quantitySold) {}

    public List<ProductRevenueRanking> getTopProductsByRevenue(int limit) {
        Map<String, BigDecimal> revMap = new LinkedHashMap<>();
        Map<String, BigDecimal> qtyMap = new LinkedHashMap<>();
        Map<String, String> nameMap = new LinkedHashMap<>();

        for (Sale s : sales.findAll()) {
            if (s.getStatus() != SaleStatus.COMPLETED) continue;
            for (SaleItem item : s.getItems()) {
                revMap.put(item.sku(), revMap.getOrDefault(item.sku(), Money.ZERO).add(item.lineTotal()));
                qtyMap.put(item.sku(), qtyMap.getOrDefault(item.sku(), BigDecimal.ZERO).add(item.quantity()));
                nameMap.putIfAbsent(item.sku(), item.name());
            }
        }

        return revMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(e -> new ProductRevenueRanking(e.getKey(), nameMap.get(e.getKey()), e.getValue(), qtyMap.get(e.getKey())))
                .collect(Collectors.toList());
    }

    public String formatTopProductsByRevenue(int limit) {
        List<ProductRevenueRanking> list = getTopProductsByRevenue(limit);
        StringBuilder b = new StringBuilder();
        b.append("====================================================\n");
        b.append("            TOP ").append(limit).append(" PRODUCTS BY REVENUE\n");
        b.append("====================================================\n");
        b.append(String.format("%-4s %-12s %-20s %12s %8s\n", "Rank", "SKU", "Name", "Revenue", "Qty Sold"));
        b.append("----------------------------------------------------\n");
        int rank = 1;
        for (ProductRevenueRanking r : list) {
            String name = r.name().length() > 20 ? r.name().substring(0, 18) + ".." : r.name();
            b.append(String.format("%-4d %-12s %-20s %12s %8s\n",
                    rank++, r.sku(), name, fmt(r.revenue()), r.quantitySold().toPlainString()));
        }
        if (list.isEmpty()) {
            b.append("No sales recorded yet.\n");
        }
        b.append("====================================================\n");
        return b.toString();
    }

    // =========================================================================
    // 4. Low-Stock Report
    // =========================================================================

    public record LowStockItem(String sku, String name, String category, BigDecimal quantity, BigDecimal reorderLevel, BigDecimal deficit) {}

    public List<LowStockItem> getLowStockProducts() {
        return products.findAll().stream()
                .filter(Product::isLowStock)
                .map(p -> new LowStockItem(p.getSku(), p.getName(), p.getCategory(), p.getQuantity(),
                        p.getReorderLevel(), p.getReorderLevel().subtract(p.getQuantity()).max(BigDecimal.ZERO)))
                .sorted(Comparator.comparing(LowStockItem::deficit).reversed()
                        .thenComparing(LowStockItem::sku))
                .collect(Collectors.toList());
    }

    public String formatLowStockReport() {
        List<LowStockItem> list = getLowStockProducts();
        StringBuilder b = new StringBuilder();
        b.append("====================================================\n");
        b.append("                 LOW-STOCK REPORT\n");
        b.append("====================================================\n");
        b.append(String.format("%-12s %-18s %8s %8s %8s\n", "SKU", "Name", "Stock", "Reorder", "Deficit"));
        b.append("----------------------------------------------------\n");
        for (LowStockItem i : list) {
            String name = i.name().length() > 18 ? i.name().substring(0, 16) + ".." : i.name();
            b.append(String.format("%-12s %-18s %8s %8s %8s\n",
                    i.sku(), name, i.quantity().toPlainString(), i.reorderLevel().toPlainString(), i.deficit().toPlainString()));
        }
        if (list.isEmpty()) {
            b.append("All products are above their reorder level.\n");
        }
        b.append("====================================================\n");
        return b.toString();
    }

    // =========================================================================
    // 5. Expiring-Soon Report
    // =========================================================================

    public record ExpiringItem(String sku, String name, LocalDate expiryDate, long daysRemaining, boolean expired) {}

    public List<ExpiringItem> getExpiringProducts(int daysAhead) {
        LocalDate today = LocalDate.now(clock);
        LocalDate threshold = today.plusDays(daysAhead);

        return products.findAll().stream()
                .filter(p -> p instanceof PerishableProduct)
                .map(p -> (PerishableProduct) p)
                .filter(p -> !p.getExpiryDate().isAfter(threshold))
                .map(p -> {
                    long days = ChronoUnit.DAYS.between(today, p.getExpiryDate());
                    return new ExpiringItem(p.getSku(), p.getName(), p.getExpiryDate(), days, p.isExpired(today));
                })
                .sorted(Comparator.comparing(ExpiringItem::expiryDate)
                        .thenComparing(ExpiringItem::sku))
                .collect(Collectors.toList());
    }

    public String formatExpiringReport(int daysAhead) {
        List<ExpiringItem> list = getExpiringProducts(daysAhead);
        LocalDate today = LocalDate.now(clock);
        StringBuilder b = new StringBuilder();
        b.append("====================================================\n");
        b.append(String.format("         EXPIRING-SOON REPORT (Next %d Days)\n", daysAhead));
        b.append(String.format("         Current Date: %s\n", today));
        b.append("====================================================\n");
        b.append(String.format("%-12s %-18s %-12s %s\n", "SKU", "Name", "Expiry Date", "Status"));
        b.append("----------------------------------------------------\n");
        for (ExpiringItem item : list) {
            String name = item.name().length() > 18 ? item.name().substring(0, 16) + ".." : item.name();
            String status;
            if (item.expired()) {
                status = "EXPIRED (" + Math.abs(item.daysRemaining()) + "d ago)";
            } else if (item.daysRemaining() == 0) {
                status = "EXPIRES TODAY";
            } else {
                status = item.daysRemaining() + " days left";
            }
            b.append(String.format("%-12s %-18s %-12s %s\n", item.sku(), name, item.expiryDate(), status));
        }
        if (list.isEmpty()) {
            b.append("No perishables expiring within the selected window.\n");
        }
        b.append("====================================================\n");
        return b.toString();
    }

    // =========================================================================
    // 6. Profit Report (Decision D11)
    // =========================================================================

    public record ProductProfit(String sku, String name, BigDecimal quantitySold, BigDecimal revenue, BigDecimal cost, BigDecimal profit) {}

    public record ProfitReport(
            LocalDate fromDate,
            LocalDate toDate,
            int completedSalesCount,
            BigDecimal totalRevenue,
            BigDecimal totalCost,
            BigDecimal grossProfit,
            BigDecimal profitMarginPercent,
            List<ProductProfit> productBreakdown
    ) {}

    /**
     * Profit calculation according to decision D11:
     * profit = revenue after discounts, before tax, minus qty * cost snapshot; voided sales excluded.
     */
    public ProfitReport getProfitReport(LocalDate from, LocalDate to) {
        List<Sale> filtered = sales.findAll().stream()
                .filter(s -> s.getStatus() == SaleStatus.COMPLETED)
                .filter(s -> {
                    if (s.getCompletedAt() == null) return false;
                    LocalDate d = s.getCompletedAt().toLocalDate();
                    if (from != null && d.isBefore(from)) return false;
                    return to == null || !d.isAfter(to);
                })
                .toList();

        BigDecimal totalRevenue = Money.ZERO;
        BigDecimal totalCost = Money.ZERO;

        Map<String, BigDecimal> productQty = new LinkedHashMap<>();
        Map<String, BigDecimal> productRev = new LinkedHashMap<>();
        Map<String, BigDecimal> productCost = new LinkedHashMap<>();
        Map<String, String> productName = new LinkedHashMap<>();

        for (Sale s : filtered) {
            for (SaleItem item : s.getItems()) {
                // Revenue after line and cart discounts, before tax
                BigDecimal lineRevenue = item.taxableAmount();
                // Cost snapshot
                BigDecimal lineCost = Money.round(item.quantity().multiply(item.costPrice()));

                totalRevenue = totalRevenue.add(lineRevenue);
                totalCost = totalCost.add(lineCost);

                String sku = item.sku();
                productQty.put(sku, productQty.getOrDefault(sku, BigDecimal.ZERO).add(item.quantity()));
                productRev.put(sku, productRev.getOrDefault(sku, Money.ZERO).add(lineRevenue));
                productCost.put(sku, productCost.getOrDefault(sku, Money.ZERO).add(lineCost));
                productName.putIfAbsent(sku, item.name());
            }
        }

        BigDecimal grossProfit = totalRevenue.subtract(totalCost);
        BigDecimal marginPercent = BigDecimal.ZERO;
        if (totalRevenue.signum() > 0) {
            marginPercent = grossProfit.multiply(BigDecimal.valueOf(100))
                    .divide(totalRevenue, 2, RoundingMode.HALF_UP);
        }

        List<ProductProfit> breakdown = new ArrayList<>();
        for (String sku : productRev.keySet()) {
            BigDecimal rev = productRev.get(sku);
            BigDecimal cost = productCost.get(sku);
            BigDecimal prof = rev.subtract(cost);
            breakdown.add(new ProductProfit(sku, productName.get(sku), productQty.get(sku), rev, cost, prof));
        }
        breakdown.sort(Comparator.comparing(ProductProfit::profit).reversed().thenComparing(ProductProfit::sku));

        return new ProfitReport(from, to, filtered.size(), totalRevenue, totalCost, grossProfit, marginPercent, breakdown);
    }

    public String formatProfitReport(LocalDate from, LocalDate to) {
        ProfitReport r = getProfitReport(from, to);
        StringBuilder b = new StringBuilder();
        b.append("====================================================\n");
        b.append("                   PROFIT REPORT\n");
        String period = (from != null ? from.toString() : "Earliest") + " to " + (to != null ? to.toString() : "Latest");
        b.append("Period: ").append(period).append("\n");
        b.append("====================================================\n");
        b.append(String.format("Completed Sales        : %d\n", r.completedSalesCount()));
        b.append(String.format("Net Revenue (ex. tax)  : %s\n", fmt(r.totalRevenue())));
        b.append(String.format("Cost of Goods Sold     : %s\n", fmt(r.totalCost())));
        b.append(String.format("Gross Profit           : %s\n", fmt(r.grossProfit())));
        b.append(String.format("Profit Margin          : %s%%\n", r.profitMarginPercent().toPlainString()));
        b.append("----------------------------------------------------\n");
        b.append("BREAKDOWN BY PRODUCT (Ordered by Profit):\n");
        b.append(String.format("%-12s %-16s %10s %10s %10s\n", "SKU", "Name", "Revenue", "Cost", "Profit"));
        b.append("----------------------------------------------------\n");
        for (ProductProfit p : r.productBreakdown()) {
            String name = p.name().length() > 16 ? p.name().substring(0, 14) + ".." : p.name();
            b.append(String.format("%-12s %-16s %10s %10s %10s\n",
                    p.sku(), name, fmt(p.revenue()), fmt(p.cost()), fmt(p.profit())));
        }
        if (r.productBreakdown().isEmpty()) {
            b.append("No completed sales in this period.\n");
        }
        b.append("====================================================\n");
        return b.toString();
    }

    private String fmt(BigDecimal amount) {
        String symbol = useNairaSymbol ? "\u20A6" : "NGN ";
        return String.format(Locale.US, "%s%,.2f", symbol, Money.round(amount));
    }
}

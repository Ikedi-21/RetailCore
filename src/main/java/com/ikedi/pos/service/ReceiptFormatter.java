package com.ikedi.pos.service;

import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.PaymentMethod;
import com.ikedi.pos.model.Sale;
import com.ikedi.pos.model.SaleItem;
import com.ikedi.pos.model.SaleStatus;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formats printable ASCII receipts for sales.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Decision D15: naira sign (₦) with NGN fallback if currency symbol is not desired/supported.
 *   <li>Shows sale ID, date, cashier, itemized lines with discounts, tax, total, payment and change.
 *   <li>Prominently marks VOIDED sales with the manager and reason (decision D10).
 * </ul>
 */
public class ReceiptFormatter {

    public static final String STORE_NAME = "RetailCore Supermarket";
    public static final String STORE_ADDRESS = "Plot 14, Commercial Avenue, Enugu";
    public static final String STORE_PHONE = "Tel: +234 803 123 4567";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int LINE_WIDTH = 42;

    private final boolean useNairaSymbol;

    public ReceiptFormatter() {
        this(true);
    }

    public ReceiptFormatter(boolean useNairaSymbol) {
        this.useNairaSymbol = useNairaSymbol;
    }

    public String format(Sale sale) {
        if (sale == null) {
            throw new ValidationException("Sale is required for receipt");
        }

        StringBuilder b = new StringBuilder();
        String div = "-".repeat(LINE_WIDTH);
        String dblDiv = "=".repeat(LINE_WIDTH);

        // Header
        b.append(center(STORE_NAME)).append("\n");
        b.append(center(STORE_ADDRESS)).append("\n");
        b.append(center(STORE_PHONE)).append("\n");
        b.append(dblDiv).append("\n");

        if (sale.getStatus() == SaleStatus.VOIDED) {
            b.append(center("*** SALE VOIDED ***")).append("\n");
            if (sale.getVoidedBy() != null) {
                b.append(center("Voided by: " + sale.getVoidedBy())).append("\n");
            }
            if (sale.getVoidNote() != null && !sale.getVoidNote().isBlank()) {
                b.append(center("Note: " + sale.getVoidNote())).append("\n");
            }
            b.append(div).append("\n");
        }

        // Sale meta
        b.append(String.format("Receipt No : %s\n", sale.getId()));
        b.append(String.format("Cashier    : %s\n", sale.getCashier()));
        if (sale.getCompletedAt() != null) {
            b.append(String.format("Date       : %s\n", sale.getCompletedAt().format(DATE_FMT)));
        } else {
            b.append(String.format("Created    : %s\n", sale.getCreatedAt().format(DATE_FMT)));
        }
        b.append(div).append("\n");

        // Items header
        b.append(String.format("%-18s %5s %7s %8s\n", "Item", "Qty", "Price", "Total"));
        b.append(div).append("\n");

        for (SaleItem item : sale.getItems()) {
            String name = item.name();
            if (name.length() > 18) {
                name = name.substring(0, 16) + "..";
            }
            b.append(String.format("%-18s %5s %7s %8s\n",
                    name,
                    item.quantity().toPlainString(),
                    item.unitPrice().toPlainString(),
                    item.lineTotal().toPlainString()));

            if (item.totalDiscount().signum() > 0) {
                String label = item.discountLabel().isEmpty() ? "Discount" : item.discountLabel();
                b.append(String.format("  [%s: -%s]\n", label, fmtMoney(item.totalDiscount())));
            }
        }
        b.append(div).append("\n");

        // Totals
        b.append(padRight("Subtotal:", fmtMoney(sale.getSubtotal()))).append("\n");
        if (sale.getDiscountTotal().signum() > 0) {
            b.append(padRight("Total Discounts:", "-" + fmtMoney(sale.getDiscountTotal()))).append("\n");
        }
        b.append(padRight("VAT / Tax:", fmtMoney(sale.getTaxTotal()))).append("\n");
        b.append(dblDiv).append("\n");
        b.append(padRight("TOTAL DUE:", fmtMoney(sale.getTotal()))).append("\n");
        b.append(dblDiv).append("\n");

        // Payment info (if paid)
        if (sale.getPaymentMethod() != null) {
            b.append(padRight("Payment (" + sale.getPaymentMethod() + "):", fmtMoney(sale.getAmountPaid()))).append("\n");
            if (sale.getPaymentMethod() == PaymentMethod.CASH) {
                b.append(padRight("Change Given:", fmtMoney(sale.getChange()))).append("\n");
            }
            b.append(div).append("\n");
        }

        // Footer
        b.append(center("Thank you for your patronage!")).append("\n");
        b.append(center("Goods purchased in good condition")).append("\n");
        b.append(center("are not returnable after 48 hours.")).append("\n");

        return b.toString();
    }

    private String fmtMoney(BigDecimal amount) {
        String symbol = useNairaSymbol ? "\u20A6" : "NGN ";
        return String.format(Locale.US, "%s%,.2f", symbol, Money.round(amount));
    }

    private static String center(String text) {
        if (text.length() >= LINE_WIDTH) return text;
        int left = (LINE_WIDTH - text.length()) / 2;
        return " ".repeat(left) + text;
    }

    private static String padRight(String left, String right) {
        int space = LINE_WIDTH - left.length() - right.length();
        if (space < 1) space = 1;
        return left + " ".repeat(space) + right;
    }
}


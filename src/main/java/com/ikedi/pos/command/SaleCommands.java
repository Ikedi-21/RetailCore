package com.ikedi.pos.command;

import com.ikedi.pos.exception.PosException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.PaymentMethod;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.Sale;
import com.ikedi.pos.model.SaleItem;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.util.List;

/**
 * Point of Sale checkout and cashier operation commands.
 */
public final class SaleCommands {

    private SaleCommands() {}

    public static class SaleStartCommand implements Command {
        @Override public String name() { return "sale-start"; }
        @Override public String description() { return "Start a new point-of-sale checkout session"; }
        @Override public String usage() { return "sale-start"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            String cashier = ctx.getCurrentUser().getUsername();
            Sale sale = ctx.getCheckoutService().startSale(cashier);
            ctx.setActiveSaleId(sale.getId());
            ctx.getOut().println("Started new sale: " + sale.getId() + " (Cashier: " + cashier + ")");
        }
    }

    public static class SaleAddCommand implements Command {
        @Override public String name() { return "sale-add"; }
        @Override public String description() { return "Add an item to the current active sale"; }
        @Override public String usage() { return "sale-add <sku> <quantity>"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.size() < 2) {
                throw new ValidationException("Usage: " + usage());
            }
            String saleId = requireActiveSale(ctx);
            String cashier = ctx.getCurrentUser().getUsername();
            String sku = args.get(0);
            BigDecimal qty = new BigDecimal(args.get(1));

            Sale sale = ctx.getCheckoutService().addItem(saleId, cashier, sku, qty);
            ctx.getOut().println("Added " + qty.toPlainString() + " of " + sku + " to sale " + saleId);
            ctx.getOut().println("Running Total: " + Money.format(sale.getTotal()));
        }
    }

    public static class SaleUpdateCommand implements Command {
        @Override public String name() { return "sale-update"; }
        @Override public String description() { return "Update item quantity in active sale"; }
        @Override public String usage() { return "sale-update <sku> <quantity>"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.size() < 2) {
                throw new ValidationException("Usage: " + usage());
            }
            String saleId = requireActiveSale(ctx);
            String cashier = ctx.getCurrentUser().getUsername();
            String sku = args.get(0);
            BigDecimal qty = new BigDecimal(args.get(1));

            Sale sale = ctx.getCheckoutService().updateQuantity(saleId, cashier, sku, qty);
            ctx.getOut().println("Updated " + sku + " quantity to " + qty.toPlainString() + " in sale " + saleId);
            ctx.getOut().println("Running Total: " + Money.format(sale.getTotal()));
        }
    }

    public static class SaleRemoveCommand implements Command {
        @Override public String name() { return "sale-remove"; }
        @Override public String description() { return "Remove an item line from the active sale"; }
        @Override public String usage() { return "sale-remove <sku>"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.isEmpty()) {
                throw new ValidationException("Usage: " + usage());
            }
            String saleId = requireActiveSale(ctx);
            String cashier = ctx.getCurrentUser().getUsername();
            String sku = args.get(0);

            Sale sale = ctx.getCheckoutService().removeItem(saleId, cashier, sku);
            ctx.getOut().println("Removed " + sku + " from sale " + saleId);
            ctx.getOut().println("Running Total: " + Money.format(sale.getTotal()));
        }
    }

    public static class SaleShowCommand implements Command {
        @Override public String name() { return "sale-show"; }
        @Override public String description() { return "Display current items and totals in active sale"; }
        @Override public String usage() { return "sale-show"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            String saleId = requireActiveSale(ctx);
            Sale sale = ctx.getCheckoutService().findSale(saleId);
            ctx.getOut().println("====================================================");
            ctx.getOut().println("Active Sale: " + sale.getId() + " | Cashier: " + sale.getCashier());
            ctx.getOut().println("----------------------------------------------------");
            ctx.getOut().println(String.format("%-14s %6s %9s %10s", "SKU", "Qty", "Price", "Total"));
            for (SaleItem item : sale.getItems()) {
                ctx.getOut().println(String.format("%-14s %6s %9s %10s",
                        item.sku(), item.quantity().toPlainString(),
                        Money.format(item.unitPrice()), Money.format(item.lineTotal())));
                if (item.totalDiscount().signum() > 0) {
                    ctx.getOut().println("  [Disc: -" + Money.format(item.totalDiscount()) + " " + item.discountLabel() + "]");
                }
            }
            ctx.getOut().println("----------------------------------------------------");
            ctx.getOut().println("Subtotal   : " + Money.format(sale.getSubtotal()));
            ctx.getOut().println("Discounts  : -" + Money.format(sale.getDiscountTotal()));
            ctx.getOut().println("VAT / Tax  : " + Money.format(sale.getTaxTotal()));
            ctx.getOut().println("TOTAL DUE  : " + Money.format(sale.getTotal()));
            ctx.getOut().println("====================================================");
        }
    }

    public static class SalePayCommand implements Command {
        @Override public String name() { return "sale-pay"; }
        @Override public String description() { return "Process payment and complete active sale"; }
        @Override public String usage() { return "sale-pay <CASH|CARD|TRANSFER> [amountTendered]"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.isEmpty()) {
                throw new ValidationException("Usage: " + usage());
            }
            String saleId = requireActiveSale(ctx);
            String cashier = ctx.getCurrentUser().getUsername();

            PaymentMethod method;
            try {
                method = PaymentMethod.valueOf(args.get(0).toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Invalid payment method: " + args.get(0) + ". Must be CASH, CARD, or TRANSFER");
            }

            BigDecimal tendered = null;
            if (args.size() > 1) {
                tendered = Money.parse(args.get(1));
            }

            Sale completed = ctx.getCheckoutService().pay(saleId, cashier, method, tendered);
            ctx.setActiveSaleId(null); // Clear active sale upon completion

            ctx.getOut().println("\n" + ctx.getReceiptFormatter().format(completed));
            ctx.getOut().println("Sale " + completed.getId() + " completed successfully.");
        }
    }

    public static class SaleReceiptCommand implements Command {
        @Override public String name() { return "receipt"; }
        @Override public String description() { return "Print receipt for a sale ID"; }
        @Override public String usage() { return "receipt <sale-id>"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.isEmpty()) {
                throw new ValidationException("Usage: " + usage());
            }
            Sale sale = ctx.getCheckoutService().findSale(args.get(0));
            ctx.getOut().println("\n" + ctx.getReceiptFormatter().format(sale));
        }
    }

    public static class SaleVoidCommand implements Command {
        @Override public String name() { return "sale-void"; }
        @Override public String description() { return "Void a completed sale and return eligible stock (Manager only)"; }
        @Override public String usage() { return "sale-void <sale-id>"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.isEmpty()) {
                throw new ValidationException("Usage: " + usage());
            }
            String saleId = args.get(0);
            String manager = ctx.getCurrentUser().getUsername();
            Sale voided = ctx.getCheckoutService().voidSale(saleId, manager, Role.MANAGER);
            ctx.getOut().println("Sale " + voided.getId() + " has been marked VOIDED.");
            if (voided.getVoidNote() != null && !voided.getVoidNote().isBlank()) {
                ctx.getOut().println("Restock Notes: " + voided.getVoidNote());
            }
        }
    }

    private static String requireActiveSale(CommandContext ctx) {
        String active = ctx.getActiveSaleId();
        if (active == null) {
            throw new ValidationException("No active sale. Start one with: sale-start");
        }
        return active;
    }
}

package com.ikedi.pos.command;

import com.ikedi.pos.exception.PosException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.PerishableProduct;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.model.ProductStatus;
import com.ikedi.pos.model.ProductType;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.StandardProduct;
import com.ikedi.pos.model.WeightUnit;
import com.ikedi.pos.model.WeightedProduct;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Product viewing and management commands.
 */
public final class ProductCommands {

    private ProductCommands() {}

    public static class ProductListCommand implements Command {
        @Override public String name() { return "products"; }
        @Override public String description() { return "List inventory products"; }
        @Override public String usage() { return "products"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            List<Product> list = ctx.getProductService().list();
            ctx.getOut().println("==========================================================================");
            ctx.getOut().println(String.format("%-12s %-24s %-16s %10s %8s %8s",
                    "SKU", "Name", "Category", "Price", "Stock", "Type"));
            ctx.getOut().println("--------------------------------------------------------------------------");
            for (Product p : list) {
                String name = p.getName().length() > 24 ? p.getName().substring(0, 22) + ".." : p.getName();
                ctx.getOut().println(String.format("%-12s %-24s %-16s %10s %8s %8s",
                        p.getSku(), name, p.getCategory(), Money.format(p.getSellingPrice()),
                        p.getQuantity().toPlainString(), p.type()));
            }
            ctx.getOut().println("==========================================================================");
        }
    }

    public static class ProductViewCommand implements Command {
        @Override public String name() { return "product-view"; }
        @Override public String description() { return "View detailed product information"; }
        @Override public String usage() { return "product-view <sku>"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.isEmpty()) {
                throw new ValidationException("Usage: " + usage());
            }
            Product p = ctx.getProductService().get(args.get(0));
            ctx.getOut().println("====================================================");
            ctx.getOut().println("SKU            : " + p.getSku());
            ctx.getOut().println("Name           : " + p.getName());
            ctx.getOut().println("Type           : " + p.type());
            ctx.getOut().println("Category       : " + p.getCategory());
            ctx.getOut().println("Supplier ID    : " + p.getSupplierId());
            ctx.getOut().println("Selling Price  : " + Money.format(p.getSellingPrice()));
            ctx.getOut().println("Cost Price     : " + Money.format(p.getCostPrice()));
            ctx.getOut().println("Stock Quantity : " + p.getQuantity().toPlainString());
            ctx.getOut().println("Reorder Level  : " + p.getReorderLevel().toPlainString());
            ctx.getOut().println("Tax Rate       : " + p.getTaxRatePercent().toPlainString() + "%");
            ctx.getOut().println("Status         : " + p.getStatus());
            if (p instanceof WeightedProduct wp) {
                ctx.getOut().println("Weight Unit    : " + wp.getUnit());
            } else if (p instanceof PerishableProduct pp) {
                ctx.getOut().println("Expiry Date    : " + pp.getExpiryDate());
            }
            ctx.getOut().println("Low Stock      : " + (p.isLowStock() ? "YES (Alert)" : "NO"));
            ctx.getOut().println("====================================================");
        }
    }

    public static class ProductAddCommand implements Command {
        @Override public String name() { return "product-add"; }
        @Override public String description() { return "Add a new product (Manager only)"; }
        @Override public String usage() {
            return "product-add <sku> <name> <category> <supplierId> <sellingPrice> <costPrice> <qty> <reorderLevel> <taxRate> <STANDARD|WEIGHTED|PERISHABLE> [unit|expiryDate]";
        }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.size() < 10) {
                throw new ValidationException("Usage: " + usage());
            }

            String sku = args.get(0);
            String name = args.get(1);
            String category = args.get(2);
            String supplierId = args.get(3);
            BigDecimal sellingPrice = Money.parse(args.get(4));
            BigDecimal costPrice = Money.parse(args.get(5));
            BigDecimal qty = new BigDecimal(args.get(6));
            BigDecimal reorderLevel = new BigDecimal(args.get(7));
            BigDecimal taxRate = new BigDecimal(args.get(8));

            ProductType type;
            try {
                type = ProductType.valueOf(args.get(9).toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Invalid product type: " + args.get(9) + ". Must be STANDARD, WEIGHTED, or PERISHABLE");
            }

            Product product = switch (type) {
                case STANDARD -> new StandardProduct(sku, name, category, supplierId, sellingPrice, costPrice,
                        qty, reorderLevel, taxRate, ProductStatus.ACTIVE);
                case WEIGHTED -> {
                    if (args.size() < 11) {
                        throw new ValidationException("Weighted products require a weight unit: KG or G");
                    }
                    WeightUnit unit = WeightUnit.valueOf(args.get(10).toUpperCase());
                    yield new WeightedProduct(sku, name, category, supplierId, sellingPrice, costPrice,
                            qty, reorderLevel, taxRate, ProductStatus.ACTIVE, unit);
                }
                case PERISHABLE -> {
                    if (args.size() < 11) {
                        throw new ValidationException("Perishable products require an expiry date: YYYY-MM-DD");
                    }
                    LocalDate expiry = LocalDate.parse(args.get(10));
                    yield new PerishableProduct(sku, name, category, supplierId, sellingPrice, costPrice,
                            qty, reorderLevel, taxRate, ProductStatus.ACTIVE, expiry);
                }
            };

            ctx.getProductService().add(product);
            ctx.getOut().println("Product '" + product.getSku() + "' added successfully.");
        }
    }
}

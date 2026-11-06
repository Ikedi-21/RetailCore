package com.ikedi.pos.command;

import com.ikedi.pos.exception.PosException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Role;
import java.math.BigDecimal;
import java.util.List;

/**
 * Inventory adjustment, receipt and write-off commands (Manager only).
 */
public final class StockCommands {

    private StockCommands() {}

    public static class StockReceiveCommand implements Command {
        @Override public String name() { return "stock-receive"; }
        @Override public String description() { return "Receive new stock from a supplier (Manager only)"; }
        @Override public String usage() { return "stock-receive <sku> <quantity> <reason>"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.size() < 3) {
                throw new ValidationException("Usage: " + usage());
            }
            String sku = args.get(0);
            BigDecimal qty = new BigDecimal(args.get(1));
            String reason = args.get(2);
            String actor = ctx.getCurrentUser().getUsername();

            ctx.getInventoryService().receiveStock(sku, qty, reason, actor);
            ctx.getOut().println("Received " + qty.toPlainString() + " units of " + sku + " successfully.");
        }
    }

    public static class StockAdjustCommand implements Command {
        @Override public String name() { return "stock-adjust"; }
        @Override public String description() { return "Manually adjust stock level (+/-) (Manager only)"; }
        @Override public String usage() { return "stock-adjust <sku> <delta> <reason>"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.size() < 3) {
                throw new ValidationException("Usage: " + usage());
            }
            String sku = args.get(0);
            BigDecimal delta = new BigDecimal(args.get(1));
            String reason = args.get(2);
            String actor = ctx.getCurrentUser().getUsername();

            ctx.getInventoryService().adjustStock(sku, delta, reason, actor);
            ctx.getOut().println("Stock adjusted for " + sku + " by " + delta.toPlainString() + " successfully.");
        }
    }

    public static class StockWriteOffCommand implements Command {
        @Override public String name() { return "stock-write-off"; }
        @Override public String description() { return "Write off expired perishable stock (Manager only)"; }
        @Override public String usage() { return "stock-write-off <sku>"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.isEmpty()) {
                throw new ValidationException("Usage: " + usage());
            }
            String sku = args.get(0);
            String actor = ctx.getCurrentUser().getUsername();

            ctx.getInventoryService().writeOffExpired(sku, actor);
            ctx.getOut().println("Expired stock written off for " + sku + " successfully.");
        }
    }
}

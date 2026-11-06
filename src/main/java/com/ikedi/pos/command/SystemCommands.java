package com.ikedi.pos.command;

import com.ikedi.pos.exception.PosException;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.observer.LowStockEvent;
import java.util.List;

/**
 * System utility commands (help, alerts, exit).
 */
public final class SystemCommands {

    private SystemCommands() {}

    public static class HelpCommand implements Command {
        private final CommandRegistry registry;

        public HelpCommand(CommandRegistry registry) {
            this.registry = registry;
        }

        @Override public String name() { return "help"; }
        @Override public String description() { return "Show available commands for your role"; }
        @Override public String usage() { return "help"; }
        @Override public Role requiredRole() { return null; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            List<Command> available = registry.getAvailableFor(ctx.getCurrentUser());
            ctx.getOut().println("==========================================================================");
            String roleStr = ctx.getCurrentUser() == null ? "Unauthenticated" : ctx.getCurrentUser().getRole().name();
            ctx.getOut().println("AVAILABLE COMMANDS (Role: " + roleStr + "):");
            ctx.getOut().println("--------------------------------------------------------------------------");
            for (Command cmd : available) {
                ctx.getOut().println(String.format("  %-25s - %s", cmd.usage(), cmd.description()));
            }
            ctx.getOut().println("==========================================================================");
        }
    }

    public static class AlertsCommand implements Command {
        @Override public String name() { return "alerts"; }
        @Override public String description() { return "View active low-stock alerts"; }
        @Override public String usage() { return "alerts"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            List<LowStockEvent> alerts = ctx.getAlertService().openAlerts();
            ctx.getOut().println("====================================================");
            ctx.getOut().println("ACTIVE LOW-STOCK ALERTS (" + alerts.size() + "):");
            ctx.getOut().println("----------------------------------------------------");
            if (alerts.isEmpty()) {
                ctx.getOut().println("No active low-stock alerts.");
            } else {
                for (LowStockEvent e : alerts) {
                    ctx.getOut().println(String.format("  [%s] %s (%s) - Stock: %s <= Reorder: %s",
                            e.raisedAt().toLocalDate(), e.productName(), e.sku(),
                            e.quantity().toPlainString(), e.reorderLevel().toPlainString()));
                }
            }
            ctx.getOut().println("====================================================");
        }
    }

    public static class ExitCommand implements Command {
        @Override public String name() { return "exit"; }
        @Override public String description() { return "Exit the POS application"; }
        @Override public String usage() { return "exit"; }
        @Override public Role requiredRole() { return null; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            ctx.getOut().println("Exiting RetailCore. Goodbye!");
            ctx.stop();
        }
    }
}

package com.ikedi.pos.command;

import com.ikedi.pos.exception.PosException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.Supplier;
import java.util.List;

/**
 * Supplier lookup and management commands.
 */
public final class SupplierCommands {

    private SupplierCommands() {}

    public static class SupplierListCommand implements Command {
        @Override public String name() { return "suppliers"; }
        @Override public String description() { return "List all registered suppliers"; }
        @Override public String usage() { return "suppliers"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            List<Supplier> list = ctx.getSupplierService().list();
            ctx.getOut().println("==========================================================================");
            ctx.getOut().println(String.format("%-10s %-24s %-15s %-20s", "ID", "Name", "Phone", "Email"));
            ctx.getOut().println("--------------------------------------------------------------------------");
            for (Supplier s : list) {
                ctx.getOut().println(String.format("%-10s %-24s %-15s %-20s",
                        s.getId(), s.getName(), s.getPhone(), s.getEmail()));
            }
            ctx.getOut().println("==========================================================================");
        }
    }

    public static class SupplierAddCommand implements Command {
        @Override public String name() { return "supplier-add"; }
        @Override public String description() { return "Add a new supplier (Manager only)"; }
        @Override public String usage() { return "supplier-add <id> <name> [phone] [email]"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.size() < 2) {
                throw new ValidationException("Usage: " + usage());
            }
            String id = args.get(0);
            String name = args.get(1);
            String phone = args.size() > 2 ? args.get(2) : "";
            String email = args.size() > 3 ? args.get(3) : "";

            Supplier supplier = ctx.getSupplierService().add(id, name, phone, email);
            ctx.getOut().println("Supplier '" + supplier.getId() + "' (" + supplier.getName() + ") added successfully.");
        }
    }
}

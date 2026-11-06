package com.ikedi.pos.cli;

import com.ikedi.pos.command.Command;
import com.ikedi.pos.command.CommandContext;
import com.ikedi.pos.command.CommandRegistry;
import com.ikedi.pos.exception.PosException;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.User;
import java.util.List;

/**
 * Interactive command-line loop.
 *
 * <p>Key constraints (Step 5):
 * <ul>
 *   <li>Catches {@link PosException} only, displaying clean and friendly errors.
 *   <li>Enforces forced password change on first login (decision D17) before any
 *       other commands are allowed.
 *   <li>Role-aware command execution: enforces required roles (cashier vs manager).
 * </ul>
 */
public class PosCli {

    private final CommandContext context;
    private final CommandRegistry registry;

    public PosCli(CommandContext context, CommandRegistry registry) {
        this.context = context;
        this.registry = registry;
    }

    public void run() {
        printBanner();

        while (context.isRunning()) {
            User user = context.getCurrentUser();
            String prompt = user != null ? (user.getUsername() + "@retailcore> ") : "retailcore> ";
            context.getOut().print(prompt);
            context.getOut().flush();

            if (!context.getIn().hasNextLine()) {
                break; // EOF reached
            }

            String line = context.getIn().nextLine();
            if (line == null || line.isBlank()) {
                continue;
            }

            executeLine(line.trim());
        }
    }

    public void executeLine(String line) {
        List<String> tokens;
        try {
            tokens = CommandParser.parse(line);
        } catch (PosException e) {
            context.getOut().println("Error: " + e.getMessage());
            return;
        }

        if (tokens.isEmpty()) {
            return;
        }

        String commandName = tokens.get(0).toLowerCase();
        List<String> args = tokens.subList(1, tokens.size());

        Command cmd = registry.find(commandName).orElse(null);
        if (cmd == null) {
            context.getOut().println("Unknown command: '" + commandName + "'. Type 'help' to see available commands.");
            return;
        }

        // Role authorization check
        User user = context.getCurrentUser();
        Role req = cmd.requiredRole();
        if (req != null) {
            if (user == null) {
                context.getOut().println("Error: Please log in first using: login <username> <password>");
                return;
            }
            if (req == Role.MANAGER && user.getRole() != Role.MANAGER) {
                context.getOut().println("Error: Unauthorized action. Only a manager can execute '" + commandName + "'.");
                return;
            }
        }

        // Forced password change enforcement (decision D17)
        if (user != null && user.isMustChangePassword()) {
            boolean allowedWhilePendingPasswordChange =
                    "change-password".equalsIgnoreCase(commandName)
                    || "logout".equalsIgnoreCase(commandName)
                    || "exit".equalsIgnoreCase(commandName)
                    || "help".equalsIgnoreCase(commandName);

            if (!allowedWhilePendingPasswordChange) {
                context.getOut().println("Error: You must change your default password before performing other commands.");
                context.getOut().println("       Use: change-password <oldPassword> <newPassword>");
                return;
            }
        }

        // Catch PosException ONLY as specified in requirement
        try {
            cmd.execute(context, args);
        } catch (PosException e) {
            context.getOut().println("Error: " + e.getMessage());
        }
    }

    private void printBanner() {
        context.getOut().println("==========================================================================");
        context.getOut().println("             RetailCore - Console POS & Inventory System");
        context.getOut().println("==========================================================================");
        context.getOut().println("Type 'help' to list available commands. Type 'exit' to quit.\n");
    }
}

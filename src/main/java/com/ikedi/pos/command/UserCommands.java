package com.ikedi.pos.command;

import com.ikedi.pos.exception.PosException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.User;
import java.util.List;

/**
 * User account administration commands (Manager-only).
 */
public final class UserCommands {

    private UserCommands() {}

    public static class UserListCommand implements Command {
        @Override public String name() { return "users"; }
        @Override public String description() { return "List all system users (Manager only)"; }
        @Override public String usage() { return "users"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            List<User> list = ctx.getAuthService().listUsers(ctx.getCurrentUser());
            ctx.getOut().println("====================================================");
            ctx.getOut().println(String.format("%-15s %-10s %-8s %-12s", "Username", "Role", "Locked", "MustChangePwd"));
            ctx.getOut().println("----------------------------------------------------");
            for (User u : list) {
                ctx.getOut().println(String.format("%-15s %-10s %-8s %-12s",
                        u.getUsername(), u.getRole(), u.isLocked() ? "YES" : "NO", u.isMustChangePassword() ? "YES" : "NO"));
            }
            ctx.getOut().println("====================================================");
        }
    }

    public static class UserCreateCommand implements Command {
        @Override public String name() { return "user-create"; }
        @Override public String description() { return "Create a new user account (Manager only)"; }
        @Override public String usage() { return "user-create <username> <password> <CASHIER|MANAGER>"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.size() < 3) {
                throw new ValidationException("Usage: " + usage());
            }
            String username = args.get(0);
            String password = args.get(1);
            Role role;
            try {
                role = Role.valueOf(args.get(2).toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Invalid role: " + args.get(2) + ". Must be CASHIER or MANAGER");
            }

            User created = ctx.getAuthService().createUser(ctx.getCurrentUser(), username, password, role);
            ctx.getOut().println("User created successfully: " + created.getUsername() + " (" + created.getRole() + ")");
        }
    }

    public static class UserUnlockCommand implements Command {
        @Override public String name() { return "user-unlock"; }
        @Override public String description() { return "Unlock a locked user account (Manager only)"; }
        @Override public String usage() { return "user-unlock <username>"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.isEmpty()) {
                throw new ValidationException("Usage: " + usage());
            }
            String username = args.get(0);
            ctx.getAuthService().unlock(ctx.getCurrentUser(), username);
            ctx.getOut().println("User '" + username + "' has been unlocked successfully.");
        }
    }
}

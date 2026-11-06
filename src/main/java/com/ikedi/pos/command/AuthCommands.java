package com.ikedi.pos.command;

import com.ikedi.pos.exception.PosException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.User;
import java.util.List;

/**
 * Authentication and session commands.
 */
public final class AuthCommands {

    private AuthCommands() {}

    public static class LoginCommand implements Command {
        @Override public String name() { return "login"; }
        @Override public String description() { return "Log in to the system"; }
        @Override public String usage() { return "login <username> <password>"; }
        @Override public Role requiredRole() { return null; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.size() < 2) {
                throw new ValidationException("Usage: " + usage());
            }
            String username = args.get(0);
            String password = args.get(1);

            User user = ctx.getAuthService().login(username, password);
            ctx.setCurrentUser(user);
            ctx.getOut().println("Welcome, " + user.getUsername() + " (" + user.getRole() + ")!");

            if (user.isMustChangePassword()) {
                ctx.getOut().println("\n*** NOTICE: You are using a temporary or default password.");
                ctx.getOut().println("*** You MUST change your password before doing anything else.");
                ctx.getOut().println("*** Use: change-password <oldPassword> <newPassword>\n");
            }
        }
    }

    public static class LogoutCommand implements Command {
        @Override public String name() { return "logout"; }
        @Override public String description() { return "Log out current session"; }
        @Override public String usage() { return "logout"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) {
            String name = ctx.getCurrentUser() != null ? ctx.getCurrentUser().getUsername() : "User";
            ctx.setCurrentUser(null);
            ctx.setActiveSaleId(null);
            ctx.getOut().println(name + " logged out successfully.");
        }
    }

    public static class ChangePasswordCommand implements Command {
        @Override public String name() { return "change-password"; }
        @Override public String description() { return "Change your login password"; }
        @Override public String usage() { return "change-password <oldPassword> <newPassword>"; }
        @Override public Role requiredRole() { return Role.CASHIER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            if (args.size() < 2) {
                throw new ValidationException("Usage: " + usage());
            }
            User user = ctx.getCurrentUser();
            if (user == null) {
                throw new ValidationException("You must be logged in to change your password");
            }
            String oldPassword = args.get(0);
            String newPassword = args.get(1);

            ctx.getAuthService().changePassword(user.getUsername(), oldPassword, newPassword);
            // Re-login to update user state (mustChangePassword cleared)
            User refreshed = ctx.getAuthService().login(user.getUsername(), newPassword);
            ctx.setCurrentUser(refreshed);
            ctx.getOut().println("Password changed successfully.");
        }
    }
}

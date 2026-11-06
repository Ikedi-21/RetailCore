package com.ikedi.pos.command;

import com.ikedi.pos.exception.PosException;
import com.ikedi.pos.model.Role;
import java.util.List;

/**
 * Command interface (Command pattern) for CLI operations.
 */
public interface Command {

    /** Primary command invocation name (e.g. "login", "sale", "report"). */
    String name();

    /** Human-readable description of what this command does. */
    String description();

    /** Usage syntax for the help listing. */
    String usage();

    /**
     * Minimum role required to execute and view this command.
     * Returns null if unauthenticated users may execute it (e.g. login, help, exit).
     */
    Role requiredRole();

    /**
     * Executes the command with arguments.
     *
     * @param context the current runtime session context
     * @param args arguments excluding the command name
     * @throws PosException domain validation/authorization error to display cleanly
     */
    void execute(CommandContext context, List<String> args) throws PosException;
}

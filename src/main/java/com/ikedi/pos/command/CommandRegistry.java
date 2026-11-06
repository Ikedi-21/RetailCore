package com.ikedi.pos.command;

import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.User;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of available CLI commands. Provides role-aware filtering so
 * a CASHIER never sees or accesses MANAGER commands.
 */
public class CommandRegistry {

    private final Map<String, Command> commands = new LinkedHashMap<>();

    public void register(Command command) {
        commands.put(command.name().toLowerCase(), command);
    }

    public Optional<Command> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(commands.get(name.trim().toLowerCase()));
    }

    public List<Command> getAll() {
        return new ArrayList<>(commands.values());
    }

    /**
     * Role-aware listing:
     * - Unauthenticated: only commands with no role requirement (login, help, exit)
     * - CASHIER: unauthenticated commands + CASHIER commands (MANAGER commands hidden)
     * - MANAGER: all commands
     */
    public List<Command> getAvailableFor(User user) {
        List<Command> result = new ArrayList<>();
        for (Command cmd : commands.values()) {
            Role req = cmd.requiredRole();
            if (req == null) {
                result.add(cmd);
            } else if (user != null) {
                if (user.getRole() == Role.MANAGER) {
                    result.add(cmd);
                } else if (user.getRole() == Role.CASHIER && req == Role.CASHIER) {
                    result.add(cmd);
                }
            }
        }
        return result;
    }
}

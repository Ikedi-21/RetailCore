package com.ikedi.pos.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.cli.Main;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandRegistryTest {

    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
        Main.registerAllCommands(registry);
    }

    @Test
    void unauthenticatedUserOnlySeesPublicCommands() {
        List<Command> available = registry.getAvailableFor(null);
        List<String> names = available.stream().map(Command::name).toList();

        assertTrue(names.contains("login"));
        assertTrue(names.contains("help"));
        assertTrue(names.contains("exit"));

        assertFalse(names.contains("sale-start"));
        assertFalse(names.contains("report-profit"));
        assertFalse(names.contains("user-create"));
    }

    @Test
    void cashierDoesNotSeeManagerCommands() {
        User cashier = new User("ada", Role.CASHIER, "hash", "salt", false);
        List<Command> available = registry.getAvailableFor(cashier);
        List<String> names = available.stream().map(Command::name).toList();

        // Cashier can see cashier commands
        assertTrue(names.contains("sale-start"));
        assertTrue(names.contains("sale-pay"));
        assertTrue(names.contains("products"));
        assertTrue(names.contains("change-password"));

        // Cashier MUST NOT see manager commands
        assertFalse(names.contains("report-profit"));
        assertFalse(names.contains("report-daily"));
        assertFalse(names.contains("user-create"));
        assertFalse(names.contains("user-unlock"));
        assertFalse(names.contains("sale-void"));
        assertFalse(names.contains("product-add"));
        assertFalse(names.contains("stock-receive"));
    }

    @Test
    void managerSeesAllCommands() {
        User manager = new User("boss", Role.MANAGER, "hash", "salt", false);
        List<Command> available = registry.getAvailableFor(manager);
        List<String> names = available.stream().map(Command::name).toList();

        assertTrue(names.contains("login"));
        assertTrue(names.contains("sale-start"));
        assertTrue(names.contains("user-create"));
        assertTrue(names.contains("report-profit"));
        assertTrue(names.contains("sale-void"));
        assertTrue(names.contains("stock-adjust"));
    }
}

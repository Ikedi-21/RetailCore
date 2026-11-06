package com.ikedi.pos.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.TestKit;
import com.ikedi.pos.command.CommandContext;
import com.ikedi.pos.command.CommandRegistry;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.User;
import com.ikedi.pos.observer.AlertService;
import com.ikedi.pos.repository.memory.InMemoryDiscountRepository;
import com.ikedi.pos.repository.memory.InMemoryMovementRepository;
import com.ikedi.pos.repository.memory.InMemoryProductRepository;
import com.ikedi.pos.repository.memory.InMemorySaleRepository;
import com.ikedi.pos.repository.memory.InMemorySupplierRepository;
import com.ikedi.pos.repository.memory.InMemoryUserRepository;
import com.ikedi.pos.service.AuthService;
import com.ikedi.pos.service.CheckoutService;
import com.ikedi.pos.service.InventoryService;
import com.ikedi.pos.service.PriceCalculator;
import com.ikedi.pos.service.ProductService;
import com.ikedi.pos.service.ReceiptFormatter;
import com.ikedi.pos.service.ReportService;
import com.ikedi.pos.service.SupplierService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Scanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PosCliTest {

    private CommandRegistry registry;
    private CommandContext context;
    private PosCli cli;
    private ByteArrayOutputStream output;
    private AuthService authService;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        InMemoryUserRepository userRepo = new InMemoryUserRepository();
        InMemorySupplierRepository supplierRepo = new InMemorySupplierRepository();
        InMemoryProductRepository productRepo = new InMemoryProductRepository();
        InMemoryDiscountRepository discountRepo = new InMemoryDiscountRepository();
        InMemorySaleRepository saleRepo = new InMemorySaleRepository();
        InMemoryMovementRepository movementRepo = new InMemoryMovementRepository();

        Clock clock = TestKit.clock();
        AlertService alertService = new AlertService();

        authService = new AuthService(userRepo);
        authService.seedDefaultManager();

        SupplierService supplierService = new SupplierService(supplierRepo);
        supplierService.add("SUP-1", "Dangote", "0803", "a@b.com");

        productService = new ProductService(productRepo, supplierService);
        productService.add(TestKit.std("RICE-1", "500.00", "0", "100"));

        InventoryService inventoryService = new InventoryService(productRepo, movementRepo, clock);
        inventoryService.addListener(alertService);

        PriceCalculator calc = new PriceCalculator();
        CheckoutService checkoutService = new CheckoutService(productRepo, saleRepo, movementRepo,
                discountRepo, inventoryService, calc, clock);
        ReceiptFormatter receiptFormatter = new ReceiptFormatter(false);
        ReportService reportService = new ReportService(saleRepo, productRepo, clock, false);

        registry = new CommandRegistry();
        Main.registerAllCommands(registry);

        output = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(output, true, StandardCharsets.UTF_8);

        context = new CommandContext(authService, productService, supplierService, inventoryService,
                checkoutService, reportService, receiptFormatter, alertService, clock, printStream, new Scanner(""));

        cli = new PosCli(context, registry);
    }

    private String getOutput() {
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void unauthenticatedUserCannotRunProtectedCommands() {
        cli.executeLine("sale-start");
        assertTrue(getOutput().contains("Please log in first"));
    }

    @Test
    void seededManagerMustChangePasswordBeforeRunningCommands() {
        cli.executeLine("login manager ChangeMe123");
        assertTrue(getOutput().contains("Welcome, manager"));
        assertTrue(getOutput().contains("NOTICE: You are using a temporary or default password"));

        // Attempting to run a normal command is blocked
        cli.executeLine("products");
        assertTrue(getOutput().contains("You must change your default password before performing other commands"));

        // Changing password unblocks
        cli.executeLine("change-password ChangeMe123 BrandNewSecret123");
        assertTrue(getOutput().contains("Password changed successfully"));

        output.reset();
        cli.executeLine("products");
        assertTrue(getOutput().contains("RICE-1"));
        assertFalse(getOutput().contains("You must change your default password"));
    }

    @Test
    void cashierCannotExecuteManagerCommands() {
        User manager = authService.login("manager", "ChangeMe123");
        authService.changePassword("manager", "ChangeMe123", "NewSecret123");
        manager = authService.login("manager", "NewSecret123");

        authService.createUser(manager, "ada", "cashierPass1", Role.CASHIER);

        output.reset();
        cli.executeLine("login ada cashierPass1");
        cli.executeLine("change-password cashierPass1 AdaNewPassword99");

        output.reset();
        cli.executeLine("report-profit");
        assertTrue(getOutput().contains("Unauthorized action. Only a manager can execute 'report-profit'"));
    }

    @Test
    void fullSaleFlowViaCliCommands() {
        User manager = authService.login("manager", "ChangeMe123");
        authService.changePassword("manager", "ChangeMe123", "NewSecret123");
        manager = authService.login("manager", "NewSecret123");
        authService.createUser(manager, "cashier1", "cashierSecret1", Role.CASHIER);

        output.reset();
        cli.executeLine("login cashier1 cashierSecret1");
        cli.executeLine("change-password cashierSecret1 FreshPassword1");

        cli.executeLine("sale-start");
        assertTrue(getOutput().contains("Started new sale: SALE-000001"));

        cli.executeLine("sale-add RICE-1 2");
        assertTrue(getOutput().contains("Running Total: "));
        assertTrue(getOutput().contains("1,000.00"));

        output.reset();
        cli.executeLine("sale-pay CASH 1500");
        String receipt = getOutput();
        assertTrue(receipt.contains("Receipt No : SALE-000001"));
        assertTrue(receipt.contains("TOTAL DUE"));
        assertTrue(receipt.contains("Payment (CASH)"));
        assertTrue(receipt.contains("Change Given"));
        assertTrue(receipt.contains("completed successfully"));
    }

    @Test
    void friendlyErrorsCatchPosExceptionCleanly() {
        User manager = authService.login("manager", "ChangeMe123");
        authService.changePassword("manager", "ChangeMe123", "NewSecret123");

        cli.executeLine("login manager NewSecret123");
        output.reset();
        // Trying to add invalid quantity
        cli.executeLine("sale-start");
        cli.executeLine("sale-add RICE-1 -5");
        assertTrue(getOutput().contains("Error: Quantity must be greater than zero"));
    }
}

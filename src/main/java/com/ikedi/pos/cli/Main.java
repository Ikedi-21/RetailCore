package com.ikedi.pos.cli;

import com.ikedi.pos.command.AuthCommands;
import com.ikedi.pos.command.CommandContext;
import com.ikedi.pos.command.CommandRegistry;
import com.ikedi.pos.command.ProductCommands;
import com.ikedi.pos.command.ReportCommands;
import com.ikedi.pos.command.SaleCommands;
import com.ikedi.pos.command.StockCommands;
import com.ikedi.pos.command.SupplierCommands;
import com.ikedi.pos.command.SystemCommands;
import com.ikedi.pos.command.UserCommands;
import com.ikedi.pos.observer.AlertService;
import com.ikedi.pos.repository.DiscountRepository;
import com.ikedi.pos.repository.MovementRepository;
import com.ikedi.pos.repository.ProductRepository;
import com.ikedi.pos.repository.SaleRepository;
import com.ikedi.pos.repository.SupplierRepository;
import com.ikedi.pos.repository.UserRepository;
import com.ikedi.pos.repository.json.JsonDiscountRepository;
import com.ikedi.pos.repository.json.JsonMovementRepository;
import com.ikedi.pos.repository.json.JsonProductRepository;
import com.ikedi.pos.repository.json.JsonSaleRepository;
import com.ikedi.pos.repository.json.JsonSupplierRepository;
import com.ikedi.pos.repository.json.JsonUserRepository;
import com.ikedi.pos.service.AuthService;
import com.ikedi.pos.service.CheckoutService;
import com.ikedi.pos.service.DataInitializer;
import com.ikedi.pos.service.InventoryService;
import com.ikedi.pos.service.PriceCalculator;
import com.ikedi.pos.service.ProductService;
import com.ikedi.pos.service.ReceiptFormatter;
import com.ikedi.pos.service.ReportService;
import com.ikedi.pos.service.SupplierService;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.Scanner;

/**
 * Main application entry point for the console POS system.
 */
public class Main {

    public static final String DEFAULT_DATA_DIR = "data";

    public static void main(String[] args) {
        Path dataDir = Paths.get(DEFAULT_DATA_DIR);

        // First run seed data initialization (Step 3)
        try {
            new DataInitializer(dataDir).initializeIfAbsent();
        } catch (IOException e) {
            System.err.println("WARNING: Failed to copy seed data to " + dataDir + ": " + e.getMessage());
        }

        // Initialize JSON repositories
        UserRepository userRepo = new JsonUserRepository(dataDir.resolve("users.json"));
        SupplierRepository supplierRepo = new JsonSupplierRepository(dataDir.resolve("suppliers.json"));
        ProductRepository productRepo = new JsonProductRepository(dataDir.resolve("products.json"));
        DiscountRepository discountRepo = new JsonDiscountRepository(dataDir.resolve("discounts.json"));
        SaleRepository saleRepo = new JsonSaleRepository(dataDir.resolve("sales.json"));
        MovementRepository movementRepo = new JsonMovementRepository(dataDir.resolve("movements.json"));

        Clock clock = Clock.systemDefaultZone();

        // Initialize Observer pattern
        AlertService alertService = new AlertService();

        // Initialize Services
        AuthService authService = new AuthService(userRepo);
        authService.seedDefaultManager();

        // Check for --reset-manager recovery flag (decision D17)
        if (args.length > 0 && "--reset-manager".equalsIgnoreCase(args[0])) {
            authService.resetDefaultManager();
            System.out.println("Default manager account ('" + AuthService.DEFAULT_MANAGER
                    + "') has been recovered and unlocked with default password ('"
                    + AuthService.DEFAULT_MANAGER_PASSWORD + "').");
            System.out.println("Password change will be required upon next login.");
            return;
        }

        SupplierService supplierService = new SupplierService(supplierRepo);
        ProductService productService = new ProductService(productRepo, supplierService);

        InventoryService inventoryService = new InventoryService(productRepo, movementRepo, clock);
        inventoryService.addListener(alertService); // Register observer for low-stock alerts

        PriceCalculator priceCalculator = new PriceCalculator();
        CheckoutService checkoutService = new CheckoutService(productRepo, saleRepo, movementRepo,
                discountRepo, inventoryService, priceCalculator, clock);

        ReceiptFormatter receiptFormatter = new ReceiptFormatter(true);
        ReportService reportService = new ReportService(saleRepo, productRepo, clock);

        // Register Command Pattern handlers
        CommandRegistry registry = new CommandRegistry();
        registerAllCommands(registry);

        CommandContext context = new CommandContext(
                authService, productService, supplierService, inventoryService,
                checkoutService, reportService, receiptFormatter, alertService,
                clock, System.out, new Scanner(System.in)
        );

        PosCli cli = new PosCli(context, registry);
        cli.run();
    }

    public static void registerAllCommands(CommandRegistry registry) {
        // System & Auth
        registry.register(new SystemCommands.HelpCommand(registry));
        registry.register(new SystemCommands.ExitCommand());
        registry.register(new SystemCommands.AlertsCommand());
        registry.register(new AuthCommands.LoginCommand());
        registry.register(new AuthCommands.LogoutCommand());
        registry.register(new AuthCommands.ChangePasswordCommand());

        // Users (Manager only)
        registry.register(new UserCommands.UserListCommand());
        registry.register(new UserCommands.UserCreateCommand());
        registry.register(new UserCommands.UserUnlockCommand());

        // Products & Suppliers
        registry.register(new ProductCommands.ProductListCommand());
        registry.register(new ProductCommands.ProductViewCommand());
        registry.register(new ProductCommands.ProductAddCommand());
        registry.register(new SupplierCommands.SupplierListCommand());
        registry.register(new SupplierCommands.SupplierAddCommand());

        // Inventory Stock (Manager only)
        registry.register(new StockCommands.StockReceiveCommand());
        registry.register(new StockCommands.StockAdjustCommand());
        registry.register(new StockCommands.StockWriteOffCommand());

        // Point of Sale Checkout
        registry.register(new SaleCommands.SaleStartCommand());
        registry.register(new SaleCommands.SaleAddCommand());
        registry.register(new SaleCommands.SaleUpdateCommand());
        registry.register(new SaleCommands.SaleRemoveCommand());
        registry.register(new SaleCommands.SaleShowCommand());
        registry.register(new SaleCommands.SalePayCommand());
        registry.register(new SaleCommands.SaleReceiptCommand());
        registry.register(new SaleCommands.SaleVoidCommand());

        // Reports (Manager only)
        registry.register(new ReportCommands.ReportDailyCommand());
        registry.register(new ReportCommands.ReportTopQtyCommand());
        registry.register(new ReportCommands.ReportTopRevCommand());
        registry.register(new ReportCommands.ReportLowStockCommand());
        registry.register(new ReportCommands.ReportExpiringCommand());
        registry.register(new ReportCommands.ReportProfitCommand());
    }
}

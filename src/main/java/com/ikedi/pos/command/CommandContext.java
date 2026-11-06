package com.ikedi.pos.command;

import com.ikedi.pos.model.User;
import com.ikedi.pos.observer.AlertService;
import com.ikedi.pos.service.AuthService;
import com.ikedi.pos.service.CheckoutService;
import com.ikedi.pos.service.InventoryService;
import com.ikedi.pos.service.ProductService;
import com.ikedi.pos.service.ReceiptFormatter;
import com.ikedi.pos.service.ReportService;
import com.ikedi.pos.service.SupplierService;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Scanner;

/**
 * Shared runtime context for CLI commands. Holds session state and service instances.
 */
public class CommandContext {

    private final AuthService authService;
    private final ProductService productService;
    private final SupplierService supplierService;
    private final InventoryService inventoryService;
    private final CheckoutService checkoutService;
    private final ReportService reportService;
    private final ReceiptFormatter receiptFormatter;
    private final AlertService alertService;
    private final Clock clock;
    private final PrintStream out;
    private final Scanner in;

    private User currentUser;
    private String activeSaleId;
    private boolean running = true;

    public CommandContext(AuthService authService, ProductService productService,
                          SupplierService supplierService, InventoryService inventoryService,
                          CheckoutService checkoutService, ReportService reportService,
                          ReceiptFormatter receiptFormatter, AlertService alertService,
                          Clock clock, PrintStream out, Scanner in) {
        this.authService = authService;
        this.productService = productService;
        this.supplierService = supplierService;
        this.inventoryService = inventoryService;
        this.checkoutService = checkoutService;
        this.reportService = reportService;
        this.receiptFormatter = receiptFormatter;
        this.alertService = alertService;
        this.clock = clock;
        this.out = out;
        this.in = in;
    }

    public AuthService getAuthService() { return authService; }
    public ProductService getProductService() { return productService; }
    public SupplierService getSupplierService() { return supplierService; }
    public InventoryService getInventoryService() { return inventoryService; }
    public CheckoutService getCheckoutService() { return checkoutService; }
    public ReportService getReportService() { return reportService; }
    public ReceiptFormatter getReceiptFormatter() { return receiptFormatter; }
    public AlertService getAlertService() { return alertService; }
    public Clock getClock() { return clock; }
    public PrintStream getOut() { return out; }
    public Scanner getIn() { return in; }

    public User getCurrentUser() { return currentUser; }
    public void setCurrentUser(User currentUser) { this.currentUser = currentUser; }

    public String getActiveSaleId() { return activeSaleId; }
    public void setActiveSaleId(String activeSaleId) { this.activeSaleId = activeSaleId; }

    public boolean isRunning() { return running; }
    public void stop() { this.running = false; }
}

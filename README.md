# RetailCore: Console Inventory & Point-of-Sale System

**RetailCore** is an enterprise-grade, typed console Inventory and Point-of-Sale (POS) application developed by **Ikedi** (Enugu, Nigeria) for an Aptech Semester 2 college project.

The system is implemented in pure Java 17+ and Maven, using Gson for atomic JSON file persistence with **zero external database and zero GUI dependencies**.

---

## 1. Architecture & Design Patterns

The codebase adheres strictly to clean architecture principles across layered packages:

```text
src/main/java/com/ikedi/pos/
├── cli/              # Main entry point, interactive PosCli loop, CommandParser
├── command/          # Command pattern: Auth, Product, Stock, Sale, Report, User commands
├── exception/        # Custom domain exception hierarchy (PosException base)
├── factory/          # Polymorphic JSON deserialization (ProductFactory, DiscountFactory)
├── model/            # Domain entities, records, and enums (Product, Sale, Movement, User)
├── observer/         # LowStockEvent, StockListener, AlertService
├── repository/       # Data store interfaces (ProductRepository, SaleRepository, etc.)
│   ├── json/         # JsonFileStore with atomic moves and JSON repository implementations
│   └── memory/       # High-speed in-memory repository implementations
├── service/          # Core business services (AuthService, CheckoutService, InventoryService,
│                     # PriceCalculator, ReportService, ReceiptFormatter, DataInitializer)
├── strategy/         # Promotion strategies (Percentage, BuyXGetYFree, Bulk, CartDiscount)
├── demo/             # Runnable ConcurrencyDemo stress test
└── util/             # Money arithmetic (scale 2, HALF_UP rounding, string-only BigDecimal)
```

### Key Design Patterns:
- **Strategy Pattern (Discounts):** Extensible promotion calculation (`PercentageDiscount`, `BuyXGetYFreeDiscount`, `BulkDiscount`, `CartDiscount`). Non-stacking rule resolution applies the single best discount per line with lowest rule ID tiebreaker (D9).
- **Factory Pattern (Serialization):** Polymorphic reconstruction of `Product` (`StandardProduct`, `WeightedProduct`, `PerishableProduct`) and `DiscountStrategy` from JSON records using discriminator `"type"` attributes.
- **Repository Pattern (Persistence):** Abstract data stores with thread-safe JSON file persistence via `JsonFileStore` using atomic temporary file moves (`ATOMIC_MOVE` falling back to `REPLACE_EXISTING`).
- **Observer Pattern (Alerts):** Decoupled `LowStockEvent` notifications emitted by `InventoryService` and tracked by `AlertService` when inventory falls below reorder levels (D16).
- **Command Pattern (CLI):** Typed CLI shell (`PosCli`, `CommandRegistry`, `CommandParser`) supporting quoted arguments, role-aware authorization (`MANAGER` vs `CASHIER`), and friendly exception boundaries.

For detailed UML class diagrams, sequence diagrams, and design rationales, see [`docs/architecture.md`](docs/architecture.md).

---

## 2. Concurrency & Thread-Safety Model (D12)

RetailCore is engineered for multi-user, multi-cashier thread safety:
1. **Per-Product ReentrantLock:** Fine-grained locking per SKU ensures high transaction throughput.
2. **Deadlock Elimination:** Carts containing multiple items sort SKU locks alphabetically (`TreeSet<String>`) before acquisition in `InventoryService.withLocks`, eliminating circular wait.
3. **Zero Overselling (Two-Phase Validation):** Preliminary stock validation provides instant non-blocking user feedback on `addItem`; authoritative stock verification runs under lock at checkout completion.
4. **Atomic Rollback:** If persistence fails after stock deduction, product quantities are restored and newly appended `StockMovement` records are purged.

---

## 3. Requirements

- **Java Development Kit (JDK):** Version 17 or newer (tested on Java 17 and Java 26)
- **Apache Maven:** Version 3.9 or newer
- **Operating System:** Windows 10/11 (PowerShell), Linux, or macOS

---

## 4. Build & Verification Instructions

### 4.1 Run Complete Test Suite
```powershell
mvn -B clean test
```
*Current test suite: **119 unit and integration tests**, 0 failures, 0 errors.*

### 4.2 Verify Full Build & Package JAR
```powershell
mvn -B clean verify
```
*Executes full compilation, surefire test runner, and packages `target/console-pos-0.1.0-SNAPSHOT.jar`.*

### 4.3 Run Concurrency & Deadlock Stress Test
Simulates 50 cashiers making 150 concurrent checkout attempts (zero overselling) and cross-ordered checkouts (deadlock freedom):
```powershell
mvn exec:java "-Dexec.args=--demo-concurrency"
# or
mvn exec:java "-Dexec.mainClass=com.ikedi.pos.demo.ConcurrencyDemo"
```

---

## 5. Running the Application

### 5.1 Launch Interactive POS CLI
```powershell
mvn exec:java
```

On first boot, `DataInitializer` automatically copies 17 realistic Nigerian seed products across Standard, Weighted, and Perishable types, 4 suppliers, 5 categories, and promotions to `data/`.

### 5.2 Default Manager Login & Security (D17)
- **Default Username:** `manager`
- **Default Password:** `ChangeMe123`
- **Security Policy:** All passwords are salted with `SecureRandom` and hashed with SHA-256. The default manager must change their password on first login before running any other commands:
  ```text
  login manager ChangeMe123
  change-password ChangeMe123 <new-password>
  ```
- **Account Lockout:** 3 consecutive failed login attempts lock the account. Only a Manager can unlock accounts.

### 5.3 Default Manager Recovery Flag (D17)
If the default manager account is locked or forgotten:
```powershell
mvn exec:java "-Dexec.args=--reset-manager"
```

---

## 6. Command Reference

Commands accept unquoted tokens as well as single and double quoted strings (e.g. `stock-receive SKU-01 10 "Monthly restock"`).

### Cashier Commands (Available to CASHIER and MANAGER)
| Command | Syntax | Description |
| :--- | :--- | :--- |
| `help` | `help` | List commands available to current role |
| `login` | `login <username> <password>` | Authenticate user session |
| `logout` | `logout` | End current session |
| `change-password` | `change-password <old> <new>` | Update account password |
| `products` | `products` | List all active products and stock |
| `product-view` | `product-view <sku>` | View details of a specific product |
| `sale-start` | `sale-start` | Start a new open sale |
| `sale-add` | `sale-add <sku> <qty>` | Add product quantity to current sale |
| `sale-update` | `sale-update <sku> <qty>` | Update quantity of item in current sale |
| `sale-remove` | `sale-remove <sku>` | Remove item from current sale |
| `sale-show` | `sale-show` | Display cart lines, running tax and total |
| `sale-pay` | `sale-pay <CASH\|CARD\|TRANSFER> [tendered]` | Complete sale atomically & print receipt |
| `receipt` | `receipt <saleId>` | Reprint receipt for a completed sale |
| `alerts` | `alerts` | View open low-stock alerts |
| `exit` | `exit` | Terminate the application |

### Manager-Only Commands
| Command | Syntax | Description |
| :--- | :--- | :--- |
| `users` | `users` | List all system accounts |
| `user-create` | `user-create <user> <ROLE> <pass>` | Create a new cashier or manager |
| `user-unlock` | `user-unlock <username>` | Unlock a locked user account |
| `product-add` | `product-add <sku> <name> <cat> <supp> <sell> <cost> <qty> <reorder> <tax%> <STANDARD\|WEIGHTED\|PERISHABLE> [expiryDate]` | Add a new product |
| `suppliers` | `suppliers` | List registered suppliers |
| `supplier-add` | `supplier-add <id> <name> <phone> <email>` | Register a new supplier |
| `stock-receive` | `stock-receive <sku> <qty> <reason>` | Record restock shipment |
| `stock-adjust` | `stock-adjust <sku> <delta> <reason>` | Record manual stock count adjustment |
| `stock-write-off` | `stock-write-off <sku>` | Write off expired perishable inventory |
| `sale-void` | `sale-void <saleId> <reason>` | Void a sale & restock non-perishables (D10) |
| `report-daily` | `report-daily` | Daily sales and payment summary |
| `report-top-qty` | `report-top-qty` | Top 5 products by quantity sold |
| `report-top-rev` | `report-top-rev` | Top 5 products by revenue generated |
| `report-low-stock`| `report-low-stock` | Products at or below reorder level |
| `report-expiring` | `report-expiring [days]` | Perishables expiring within N days |
| `report-profit` | `report-profit` | Financial profit report (D11) |

---

## 7. Binding Decisions Compliance Matrix (D1 – D18)

| Code | Rule Summary | Implementation |
| :---: | :--- | :--- |
| **D1** | BigDecimal from strings only | `Money.java`, `BigDecimalAdapter.java` |
| **D2** | Scale 2, HALF_UP, rounded per line | `Money.round()`, `PriceCalculator.java` |
| **D3** | BigDecimal quantities (Standard whole) | `StandardProduct.validateQuantity()` |
| **D4** | Compare with compareTo, never equals | Consistent across all financial/stock checks |
| **D5** | Pricing order: line $\to$ subtotal $\to$ cart $\to$ tax | `PriceCalculator.price()` |
| **D6** | Cart discount split proportionally by line subtotal | `PriceCalculator.applyCartDiscount()` |
| **D7** | Cart minimum threshold checked after line discounts | `CartDiscount.appliesTo()` |
| **D8** | Buy X Get Y Free: $\lfloor \text{qty} / (X+Y) \rfloor \times Y$ | `BuyXGetYFreeDiscount.calculateDiscount()` |
| **D9** | Best single discount per line, rule ID tiebreaker | `PriceCalculator.bestLineDiscount()` |
| **D10** | Manager void, VOID_RETURN movement, no perishable restock | `CheckoutService.voidSale()` |
| **D11** | Profit = revenue after discounts before tax minus cost snapshot | `ReportService.profitReport()` |
| **D12** | Two-phase stock validation, locked checkout, rollback on error | `CheckoutService.pay()`, `InventoryService.withLocks()` |
| **D13** | Injected java.time.Clock everywhere | Clock injected across all services |
| **D14** | IDs `SALE-000001` & `MOV-000001` continue after restart | `JsonSaleRepository`, `JsonMovementRepository` |
| **D15** | Nigerian Naira (`₦`) sign with `NGN` fallback | `ReceiptFormatter.java`, `Money.java` |
| **D16** | One active alert per SKU, cleared on restock | `AlertService.java`, `InventoryService.java` |
| **D17** | SHA-256 + salt, lockout at 3, manager reset flag | `PasswordHasher.java`, `AuthService.java` |
| **D18** | SKUs and Supplier IDs case-insensitive | `ProductService.java`, `SupplierService.java` |

---

## 8. Documentation Index

- [`docs/specification.md`](docs/specification.md): Comprehensive functional & technical specification.
- [`docs/architecture.md`](docs/architecture.md): Architectural design, pattern catalog, and Mermaid UML diagrams.
- [`docs/timeline.md`](docs/timeline.md): 10-day engineering timeline and daily milestone log.
- [`docs/demo-script.md`](docs/demo-script.md): 5-minute oral presentation and evaluation walkthrough.
- [`docs/HANDOFF.md`](docs/HANDOFF.md): Technical handoff specification and project constraints.

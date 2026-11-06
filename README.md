# RetailCore: Console Inventory & Point-of-Sale System

**RetailCore** is a production-grade, typed console Inventory and Point-of-Sale (POS) application built for an Aptech Semester 2 college project by **Ikedi** (Enugu, Nigeria).

The system is implemented in pure Java 17+ and Maven, using Gson for atomic JSON file persistence with **zero external database and zero GUI dependencies**.

---

## Architecture & Design Patterns

The codebase adheres to clean multi-layered architecture: `model`, `service`, `repository`, `repository.json`, `strategy`, `factory`, `observer`, `command`, `cli`, `exception`, and `util`.

- **Strategy Pattern (Discounts):** Plug-and-play discount calculation (`PercentageDiscount`, `BuyXGetYFreeDiscount`, `BulkDiscount`, `CartDiscount`). Best single discount per line with lowest rule ID tiebreaker (D9).
- **Factory Pattern (Serialization):** Polymorphic reconstruction of `Product` (`StandardProduct`, `WeightedProduct`, `PerishableProduct`) and `DiscountStrategy` from JSON records using discriminator `"type"` fields.
- **Repository Pattern (Persistence):** Abstract data stores with thread-safe JSON file persistence via `JsonFileStore` using atomic temporary file moves (`ATOMIC_MOVE` falling back to `REPLACE_EXISTING`).
- **Observer Pattern (Alerts):** Decoupled `LowStockEvent` notifications emitted by `InventoryService` and tracked by `AlertService` when inventory falls below reorder levels (D16).
- **Command Pattern (CLI):** Typed CLI shell (`PosCli`, `CommandRegistry`, `CommandParser`) supporting quoted arguments, role-aware authorization (`MANAGER` vs `CASHIER`), and friendly exception boundaries.

For architectural details and UML diagrams, see [`docs/architecture.md`](docs/architecture.md).

---

## Requirements

- Java 17 or newer
- Maven 3.9 or newer

---

## Build & Run Instructions

### 1. Run All Tests
```powershell
mvn -B clean test
```
*Suite status: 119 tests, 0 failures, 0 errors.*

### 2. Launch Interactive POS CLI
```powershell
mvn exec:java
```

### 3. Default Manager Login & Security (D17)
- **Default Username:** `manager`
- **Default Password:** `ChangeMe123`
- **Security Policy:** All passwords are salted with `SecureRandom` and hashed with SHA-256. The default manager must change their password on first login before running any other commands:
  ```text
  login manager ChangeMe123
  change-password ChangeMe123 <new-password>
  ```
- **Account Lockout:** 3 consecutive failed login attempts lock the account. Only a Manager can unlock accounts.

### 4. Default Manager Recovery Flag (D17)
If the manager account is locked or forgotten, recover it via:
```powershell
mvn exec:java "-Dexec.args=--reset-manager"
```

### 5. Run Concurrency & Deadlock Demo
Demonstrates 50 cashiers making 150 concurrent checkout attempts (zero overselling) and cross-ordered checkouts (deadlock freedom via natural SKU lock hierarchy):
```powershell
mvn exec:java "-Dexec.mainClass=com.ikedi.pos.demo.ConcurrencyDemo"
# or
mvn exec:java "-Dexec.args=--demo-concurrency"
```

---

## Command Reference

Commands support unquoted arguments as well as single and double quoted strings (e.g. `stock-receive SKU-01 10 "Monthly restock"`).

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

## Documentation & Presentation

- [`docs/architecture.md`](docs/architecture.md): Architecture, pattern breakdown, and Mermaid UML diagrams.
- [`docs/demo-script.md`](docs/demo-script.md): 5-minute step-by-step oral presentation and demo script for the evaluator.
- [`docs/HANDOFF.md`](docs/HANDOFF.md): Technical specification, decision record (D1–D18), and project history.

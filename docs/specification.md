# RetailCore: Functional & Technical Specification

**System:** RetailCore — Console Inventory & Point-of-Sale System  
**Target Environment:** Java 17+, Maven 3.9+, Windows 11 (PowerShell) & Linux (CI)  
**Developer:** Ikedi (Aptech Semester 2 College Project)  
**Persistence:** JSON flat files via Gson 2.11.0 (No database, no GUI)  

---

## 1. System Scope & Objectives
RetailCore is a typed, multi-role point-of-sale and inventory management application designed to handle store operations with mathematical precision, strict role-based access control (RBAC), thread-safe atomic checkouts, and resilient JSON file persistence.

### Core Constraints:
1. Pure Java 17 Standard Library + Google Gson and JUnit 5 only.
2. Zero external database engine and zero GUI frameworks.
3. Thread-safe execution with zero overselling under multi-cashier load.
4. Full mathematical accuracy for financial accounting using `BigDecimal`.

---

## 2. Actors & Role-Based Access Control (RBAC)

| Capability / Command Group | CASHIER | MANAGER |
| :--- | :---: | :---: |
| Authenticate / Logout / Change Own Password | Yes | Yes |
| Browse Products & Check Real-time Stock | Yes | Yes |
| Initiate & Manage Active Sales Carts | Yes | Yes |
| Execute Payment (Cash, Card, Transfer) & Print Receipts | Yes | Yes |
| View Open Low-Stock Alerts | Yes | Yes |
| Create, View, or Unlock System Accounts | No | **Yes** |
| Add New Products & Manage Suppliers | No | **Yes** |
| Record Stock Deliveries (`RESTOCK`) | No | **Yes** |
| Apply Manual Stock Count Adjustments (`ADJUSTMENT`) | No | **Yes** |
| Write Off Expired Perishable Goods (`EXPIRED_WRITE_OFF`) | No | **Yes** |
| Void Completed Sales & Restock Goods (`VOID_RETURN`) | No | **Yes** |
| Generate Financial & Management Reports (Daily, Profit, Top Qty/Rev) | No | **Yes** |

---

## 3. Domain Model & Data Dictionary

### 3.1 Products (`com.ikedi.pos.model.Product`)
- **Common Attributes:** `sku` (unique, case-insensitive), `name`, `category`, `supplierId`, `sellingPrice`, `costPrice`, `quantity`, `reorderLevel`, `taxRatePercent`, `status` (`ACTIVE`/`INACTIVE`).
- **Subclasses:**
  - `StandardProduct`: Sold in whole integer units. Fractional values are strictly rejected (`InvalidQuantityException`).
  - `WeightedProduct`: Sold in decimal quantities (e.g. `3.50 kg`). Supports units `KG`, `G`, `LB`, `OZ`.
  - `PerishableProduct`: Requires an `expiryDate` (`LocalDate`). Sellable on its expiry date; expired and unsellable starting at `expiryDate + 1 day`.

### 3.2 Sales & Items (`com.ikedi.pos.model.Sale`, `SaleItem`)
- **Status:** `OPEN` (in-memory only), `COMPLETED` (persisted), `VOIDED` (persisted).
- **SaleItem (Historical Snapshot):** Captures `sku`, `productName`, `quantity`, `unitPrice`, `costPrice`, `lineDiscount`, `taxableAmount`, and `taxAmount` at the exact millisecond of checkout.

### 3.3 Inventory Audit Movements (`com.ikedi.pos.model.StockMovement`)
- Append-only log with fields: `id` (`MOV-000001`), `type`, `sku`, `quantityDelta` (signed), `reason`, `user`, `timestamp`, `saleId` (optional).
- **Movement Types:** `RESTOCK` (+), `SALE` (-), `ADJUSTMENT` (+/-), `EXPIRED_WRITE_OFF` (-), `VOID_RETURN` (+).

---

## 4. Binding Decisions Specification (D1 – D18)

- **D1 (BigDecimal Strings):** All `BigDecimal` instances must be instantiated from string literals (`new BigDecimal("2500.00")` or `Money.parse`), never from floating-point doubles.
- **D2 (Scale & Rounding):** Currency amounts maintain scale 2 with `RoundingMode.HALF_UP` rounded per line.
- **D3 (Quantities):** All quantities are `BigDecimal`. `StandardProduct` must have scale 0 (whole numbers).
- **D4 (Comparisons):** Numerical comparisons use `compareTo`, never `equals` (to treat `2.0` and `2.00` as identical).
- **D5 (Pricing Order):** Line discounts $\to$ Discounted line subtotals $\to$ Cart discount $\to$ VAT calculated per line.
- **D6 (Cart Discount Distribution):** Fixed cart discounts are distributed proportionally by discounted line subtotals:
  $$\text{lineShare} = \text{round}\left(\text{cartDiscount} \times \frac{\text{discountedLineSubtotal}}{\text{discountedCartSubtotal}}\right)$$
  The final line absorbs any rounding remainder.
- **D7 (Cart Minimum Threshold):** Tested after all line discounts have been deducted.
- **D8 (Buy X Get Y Free):** 
  $$\text{freeQty} = \left\lfloor \frac{\text{purchasedQty}}{X + Y} \right\rfloor \times Y$$
- **D9 (Non-Stacking Discounts):** The single best discount applies per line. Ties resolve in favor of the lowest rule ID.
- **D10 (Sale Voiding):** Manager-only. Reverses sales by logging `VOID_RETURN`. Eligible non-perishables are restored to stock. Expired perishables are discarded and noted on the void audit.
- **D11 (Profit Calculation):**
  $$\text{Profit} = \sum_{i \in \text{Items}} \left( \text{taxableAmount}_i - \text{round}(\text{quantity}_i \times \text{costPrice}_i) \right)$$
  Excludes taxes and voided transactions.
- **D12 (Two-Phase Validation & Locking):** Stock availability is validated on `addItem` as non-blocking user feedback. At checkout, locks are acquired for all cart SKUs in alphabetical order (`TreeSet`), stock is re-validated, stock is deducted, and the sale is saved. If persistence throws, quantities are restored and movement IDs are purged.
- **D13 (Injected Clock):** All temporal logic accepts `java.time.Clock` for deterministic testing across dates and leap years.
- **D14 (Persistent Identifiers):** IDs follow `SALE-000001` and `MOV-000001` format, continuing from the highest stored record upon system restart.
- **D15 (Currency Format):** Default symbol is `₦` (Unicode `\u20A6`) with fallback to `NGN `.
- **D16 (Low Stock Observer):** Exactly one open alert per SKU when stock $\le$ reorder level. Cleared automatically upon restocking.
- **D17 (Authentication & Security):** Passwords hashed via SHA-256 with per-user `SecureRandom` 16-byte hex salts. Accounts lock after 3 consecutive failures. Default manager `manager` / `ChangeMe123` must change password on first login. Emergency recovery via `--reset-manager` CLI startup flag.
- **D18 (Case-Insensitive Uniqueness):** SKUs and Supplier IDs are enforced case-insensitively at the service layer (`ProductService`, `SupplierService`).

---

## 5. Persistence Architecture
- Files stored under `data/`: `users.json`, `suppliers.json`, `products.json`, `discounts.json`, `sales.json`, `movements.json`.
- Writes use `JsonFileStore`: serializes to `<file>.tmp`, syncs, and performs an atomic swap via `Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)`.
- Corrupt file safety: malformed JSON terminates cleanly with a diagnostic message and error code, without exposing stack traces.


# RetailCore: 5-Minute Instructor Presentation Script

**Project:** RetailCore — Console Inventory & Point-of-Sale System  
**Developer:** Ikedi  
**Target Audience:** Aptech Project Evaluator / Instructor  
**Duration:** 5 Minutes  

---

## Preparation Before the Demo

Open PowerShell in the project directory:
```powershell
cd c:\Projects\Aptech-Eprojects\Semester-2-project\console-pos
mvn -B clean verify
```
Confirm the build succeeds with **118+ tests passing**.

---

## Minute 1: Startup, Seed Data Bootstrap & Security (D17)

**Action:** Launch the application:
```powershell
mvn exec:java
```

**Talking Points:**
> "Good morning, Sir. This is **RetailCore**, a production-grade console Inventory and Point-of-Sale system built in Java 17 using Maven, pure Gson JSON persistence, and zero external database dependencies.
>
> On first launch, the system automatically checks for storage and bootstraps **17 realistic seed products** across standard, weighted, and perishable types, alongside Nigerian suppliers and categories."

**Demo Step 1.1 — Default Manager Login:**
```text
login manager ChangeMe123
```
*Observe prompt:* The system blocks access and demands an immediate password change.

**Talking Points:**
> "Notice the security implementation (Decision D17): Passwords are never stored in plaintext; they are hashed using SHA-256 with cryptographically random per-user salts. The seeded default manager account is flagged with `mustChangePassword=true`, forcing an immediate credential update before any administrative action can be taken."

**Demo Step 1.2 — Update Password:**
```text
change-password ChangeMe123 Manager@2026
```
Now type:
```text
help
```
*Observe:* The full manager command suite is displayed.

---

## Minute 2: RBAC & Cashier Delegation

**Demo Step 2.1 — Create a Cashier User:**
```text
user-create chinedu CASHIER Chinedu@123
```

**Demo Step 2.2 — Log Out & Log In as Cashier:**
```text
logout
login chinedu Chinedu@123
help
```

**Talking Points:**
> "Here we demonstrate the **Command Pattern** paired with Role-Based Access Control (RBAC). 
>
> When logged in as Cashier `chinedu`, the command registry filters available actions. High-privilege commands like user creation, inventory write-offs, financial profit reports, and sale voiding are completely invisible and blocked for cashiers."

---

## Minute 3: Point of Sale, Discounts & Receipt Printing

**Demo Step 3.1 — Initiate Sale & Add Products:**
```text
sale-start
sale-add ST-RICE-01 2
sale-add WT-BEAN-01 3.5
sale-add PE-BRD-01 1
```

**Demo Step 3.2 — Inspect Cart & Discount Engine (Strategy Pattern):**
```text
sale-show
```
*Observe:* Lines are formatted with line subtotals, VAT, running total, and active discounts.

**Talking Points:**
> "Our pricing engine implements the **Strategy Pattern** for discounts (Decision D5 to D9). Line discounts apply first, followed by proportional cart discounts, and tax is computed per line using `BigDecimal` with half-up rounding.
>
> Notice that for weighted products like beans, fractional quantities (`3.50 kg`) are supported, while standard packaged products require whole units."

**Demo Step 3.3 — Complete Checkout (CASH Payment):**
```text
sale-pay CASH 25000
```
*Observe:* Change is computed, stock is deducted, and receipt is printed with the Nigerian Naira symbol (`₦`).

**Demo Step 3.4 — View Printable Receipt:**
```text
receipt SALE-000001
```

---

## Minute 4: Observer Alerts, Stock Restocking & Reports

**Demo Step 4.1 — Low Stock Observer Pattern (Decision D16):**
```text
alerts
```
*Observe:* Any product whose stock dropped below its reorder level is visible in the active alerts list.

**Talking Points:**
> "Notice the **Observer Pattern**. When stock crosses the reorder threshold, an event is published and captured by the `AlertService`. Once the product is restocked, the alert clears automatically."

**Demo Step 4.2 — Manager Login & Financial Reports:**
```text
logout
login manager Manager@2026
stock-receive ST-BSK-02 50 "Replenishing biscuit stock"
report-daily
report-profit
```

**Talking Points:**
> "Let's review the profit report (Decision D11). It computes exact margins: revenue after discounts minus the quantity multiplied by historical cost snapshots. Voided sales are cleanly excluded."

---

## Minute 5: Concurrency Demo & Architectural Wrap-Up

**Demo Step 5.1 — Exit CLI:**
```text
exit
```

**Demo Step 5.2 — Run Concurrency & Deadlock Demo:**
```powershell
mvn exec:java "-Dexec.mainClass=com.ikedi.pos.demo.ConcurrencyDemo"
```

**Talking Points:**
> "Finally, let's run the multi-threaded concurrency engine. 
>
> In Scenario 1, 50 cashier threads make 150 simultaneous checkout requests on 100 units of stock. The atomic locking in `InventoryService` ensures **zero overselling**: exactly 100 succeed, 50 are cleanly rejected, and stock reaches exactly 0.
>
> In Scenario 2, 40 cashiers buy overlapping pairs of products in reverse order. Because our `withLocks` implementation sorts SKUs alphabetically into a `TreeSet` before acquiring locks, **circular-wait deadlocks are mathematically impossible**.
>
> All 118 unit and integration tests are green, fulfilling all 18 binding project decisions. Thank you, Sir!"


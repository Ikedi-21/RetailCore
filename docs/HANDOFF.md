# Handoff: Console Inventory and POS ("RetailCore")

## Who and how to work with him
- Ikedinachi "Ikedi" Ogbonne, Enugu, Nigeria. Developer and co-founder of GroTec. Windows 11, Git Bash (MINGW64) and PowerShell, JDK 26.0.2.1, Maven 3.9.16 (C:\Tools), git 2.53.
- Repo: https://github.com/Ikedi-21/RetailCore.git, local folder `C:\Projects\Aptech-Eprojects\Semester-2-project\console-pos`. Package root `com.ikedi.pos`.
- Wants direct, honest feedback and the reasoning behind decisions. Started by wanting to type the code himself; on 3 Nov, short on time, he asked for finished code and will study it afterwards. He wants each file with its path so he can paste and commit per group.
- Started 30 Oct on a 10-day plan, now about 4 days behind. Skip tooling basics; he is a developer.
- Past traps: an editor tab kept saving `Money.java` into `src/test/...` after a move (stray duplicate); one commit message claimed work that was not in the commit; GitHub login cached as the wrong account (403), fixed by setting the remote URL to `https://Ikedi-21@github.com/...` and clearing Windows Credential Manager.

## The project (from his PDF brief)
Java 17+, Maven, Gson only, no DB, no GUI. Roles MANAGER and CASHIER. Typed CLI commands via CommandParser (quoted args). Layers: model, service, repository, repository.json, strategy, factory, observer, command, cli, exception. Patterns: Strategy (discounts), Factory (rebuild Product/Discount from JSON `type`), Repository, Observer (low stock), Command. Clock injected. BigDecimal for money and quantities. Atomic JSON writes (tmp then ATOMIC_MOVE). Per-product locks in ascending SKU order. Deliverables: code, 15+ seed products, README, UML, regular commits, 5-minute demo, concurrency simulation.

## Fixed decisions
D1 BigDecimal from strings. D2 scale 2, HALF_UP, rounded per line. D3 all quantities BigDecimal (Standard must be whole). D4 compareTo, never equals. D5 order: line discounts, discounted subtotal, cart discount, tax per line. D6 fixed cart discount split proportionally by discounted line subtotal, last line takes the remainder. D7 cart minimum tested after line discounts. D8 Buy X Get Y: free = floor(qty/(X+Y)) x Y. D9 no stacking, best single discount per line, ties go to the lowest rule id. D10 void is manager-only, returns stock with VOID_RETURN, expired perishables not restocked (noted on the sale). D11 profit = revenue after discounts, before tax, minus qty x cost snapshot; voided sales excluded. D12 no reservation while OPEN; stock checked on add and again at completion under locks. D13 Clock injected. D14 ids SALE-000001 / MOV-000001, continue after restart. D15 naira sign, fallback NGN. D16 one open low-stock alert per SKU, cleared on recovery. Perishable is sellable on its expiry date, expired the day after.

## Already in his GitHub repo (pushed)
.gitignore (fixed), pom.xml (gson 2.11.0, junit-jupiter 5.10.2, compiler plugin 3.15.0 with -Xlint:all, surefire 3.5.4, release 17, UTF-8), `Money` (round, percentOf only), 11 exception classes (message-only constructors), `MoneyTest`, .editorconfig, CI workflow, README.
Still open on his side: confirm no stray `src/test/.../Money.java`; export the two Claude Docs (specification, 10-day timeline) into `docs/`; GitHub Actions green check; CRLF warnings (add .gitattributes).

## Stage 1 (built and verified here, in `console-pos-stage1.zip`, NOT yet in his repo)
Copy the zip's `src/` over his repo, delete the stray test `Money.java`, run `mvn -B clean test`, then commit in groups.
- util: `Money` (round, percentOf, parse, format).
- model: enums ProductStatus, WeightUnit, ProductType, MovementType, SaleStatus, PaymentMethod, Role; abstract `Product` (validateSale, checkQuantity, increase/decreaseQuantity, isLowStock, isExpired) with StandardProduct, WeightedProduct, PerishableProduct; records StockMovement (signed quantity), SaleItem (price, cost, tax and discount snapshot), PricedCart; class Sale (OPEN/COMPLETED/VOIDED).
- strategy: DiscountStrategy (scope LINE or CART, isActive, discountForLine, discountForCart), BaseDiscount, PercentageDiscount, BuyXGetYFreeDiscount, BulkDiscount, CartDiscount (percent or fixed).
- repository: ProductRepository, SaleRepository (nextId), MovementRepository (append-only, nextId), DiscountRepository, plus repository.memory implementations.
- observer: LowStockEvent, StockListener, AlertService.
- service: PriceCalculator, InventoryService (withLocks, receiveStock, adjustStock, writeOffExpired, protected afterLockAcquired hook for tests), CheckoutService (startSale, addItem, updateQuantity, removeItem, pay, voidSale(saleId, actorUser, Role), findSale, completedSalesBy). Open sales live in memory only; sales.json should hold COMPLETED and VOIDED.
- Verification: compiles clean with -Xlint:all; 56 tests pass on 3 consecutive runs. Run against a JUnit stand-in because the sandbox had no Maven, so real `mvn clean test` has not been run on it. Mutation checks all caught: rounding DOWN fails 4 tests, no locking fails the multi-product test (993 vs 995), unsorted locks time out the deadlock test, deducting during validation fails 11.

## Remaining work, in order
1. Supplier, Category, User models; ProductService (DuplicateSku), SupplierService, AuthService (SHA-256 plus per-user SecureRandom salt, lockout after 3 failures, manager unlock, seeded default manager with forced password change).
2. Gson persistence: adapters for BigDecimal-as-string, LocalDate, LocalDateTime; JSON repositories for users, products, suppliers, sales, movements, discounts (load all at startup, save after each change, tmp then ATOMIC_MOVE, corrupt file means clear error and clean exit, ids continue from the max saved); ProductFactory and DiscountFactory using a `type` field. Known gap: if a save fails after stock was deducted, movements are already appended (no rollback yet).
3. Seed data (15+ products over all three types, suppliers, categories), copied from resources to data/ on first run.
4. ReportService (daily summary, top 5 by quantity and revenue, low-stock, expiring-soon, profit) and a receipt formatter.
5. Command pattern, CommandParser (quoted args), role-aware help, CLI loop with friendly errors, Main class plus exec plugin in the POM.
6. Concurrency simulation as a runnable demo, README (commands, default manager login), UML diagram, 5-minute demo script, CI green.
Open: instructor approval for JUnit 5 is unconfirmed; recovery path if the only manager is locked is undecided.

package com.ikedi.pos.service;

import com.ikedi.pos.exception.InsufficientPaymentException;
import com.ikedi.pos.exception.ProductNotFoundException;
import com.ikedi.pos.exception.SaleStateException;
import com.ikedi.pos.exception.UnauthorizedActionException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.MovementType;
import com.ikedi.pos.model.PaymentMethod;
import com.ikedi.pos.model.PricedCart;
import com.ikedi.pos.model.Product;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.Sale;
import com.ikedi.pos.model.SaleItem;
import com.ikedi.pos.model.SaleStatus;
import com.ikedi.pos.repository.DiscountRepository;
import com.ikedi.pos.repository.ProductRepository;
import com.ikedi.pos.repository.SaleRepository;
import com.ikedi.pos.service.PriceCalculator.PricedLine;
import com.ikedi.pos.util.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs sales from start to receipt. Completing a sale is atomic: while holding every needed
 * product lock it validates all lines first, and only then deducts stock, so a sale either
 * fully commits or changes nothing (decision D12: stock is checked when adding as feedback
 * and again, authoritatively, at completion).
 */
public class CheckoutService {

    private final ProductRepository products;
    private final SaleRepository sales;
    private final DiscountRepository discounts;
    private final InventoryService inventory;
    private final PriceCalculator calculator;
    private final Clock clock;
    private final Map<String, Sale> openSales = new ConcurrentHashMap<>();

    public CheckoutService(ProductRepository products, SaleRepository sales, DiscountRepository discounts,
                           InventoryService inventory, PriceCalculator calculator, Clock clock) {
        this.products = products;
        this.sales = sales;
        this.discounts = discounts;
        this.inventory = inventory;
        this.calculator = calculator;
        this.clock = clock;
    }

    // ---- building the sale ----

    public Sale startSale(String cashier) {
        Sale sale = new Sale(sales.nextId(), cashier, LocalDateTime.now(clock));
        openSales.put(sale.getId(), sale);
        return sale;
    }

    /** Adds quantity of a product to the sale (merging with an existing line). */
    public Sale addItem(String saleId, String cashier, String sku, BigDecimal quantity) {
        Sale sale = requireOpenSale(saleId, cashier);
        BigDecimal existing = sale.lines().getOrDefault(sku, BigDecimal.ZERO);
        if (quantity == null) {
            throw new ValidationException("A quantity is required");
        }
        return setQuantity(sale, sku, existing.add(quantity));
    }

    /** Replaces the quantity of an existing line. */
    public Sale updateQuantity(String saleId, String cashier, String sku, BigDecimal quantity) {
        Sale sale = requireOpenSale(saleId, cashier);
        if (!sale.lines().containsKey(sku)) {
            throw new ValidationException("Item " + sku + " is not in sale " + saleId);
        }
        return setQuantity(sale, sku, quantity);
    }

    public Sale removeItem(String saleId, String cashier, String sku) {
        Sale sale = requireOpenSale(saleId, cashier);
        sale.removeLine(sku);
        reprice(sale);
        return sale;
    }

    private Sale setQuantity(Sale sale, String sku, BigDecimal quantity) {
        Product product = requireProduct(sku);
        inventory.validateAvailable(product, quantity, LocalDate.now(clock));
        sale.setLine(sku, quantity);
        reprice(sale);
        return sale;
    }

    /** Recomputes the running subtotal, discount, tax and total of an open sale. */
    private void reprice(Sale sale) {
        List<PricedLine> lines = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : sale.lines().entrySet()) {
            lines.add(new PricedLine(requireProduct(e.getKey()), e.getValue()));
        }
        sale.applyPricing(calculator.price(lines, discounts.findAll(), LocalDate.now(clock)));
    }

    // ---- payment and atomic completion ----

    /**
     * Completes the sale. For CASH, {@code tendered} is required and change is calculated. For
     * CARD and TRANSFER the amount paid is the total ({@code tendered} may be null).
     */
    public Sale pay(String saleId, String cashier, PaymentMethod method, BigDecimal tendered) {
        Sale sale = requireOpenSale(saleId, cashier);
        if (method == null) {
            throw new ValidationException("A payment method is required");
        }
        if (sale.lines().isEmpty()) {
            throw new SaleStateException("Sale " + saleId + " has no items");
        }
        List<Runnable> after = new ArrayList<>();
        Sale done = inventory.withLocks(sale.lines().keySet(), () -> {
            LocalDate today = LocalDate.now(clock);
            List<PricedLine> lines = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> e : sale.lines().entrySet()) {
                Product product = requireProduct(e.getKey());
                inventory.validateAvailable(product, e.getValue(), today);   // validate ALL before changing ANY
                lines.add(new PricedLine(product, e.getValue()));
            }
            PricedCart cart = calculator.price(lines, discounts.findAll(), today);

            BigDecimal paid;
            BigDecimal change = Money.ZERO;
            if (method == PaymentMethod.CASH) {
                if (tendered == null) {
                    throw new ValidationException("An amount tendered is required for cash payments");
                }
                paid = Money.round(tendered);
                if (paid.compareTo(cart.total()) < 0) {
                    throw new InsufficientPaymentException("Payment of " + Money.format(paid)
                            + " is below the amount due: " + Money.format(cart.total()));
                }
                change = paid.subtract(cart.total());
            } else {
                paid = cart.total();
                if (tendered != null && Money.round(tendered).compareTo(paid) != 0) {
                    throw new InsufficientPaymentException(method + " payments must equal the amount due: "
                            + Money.format(paid));
                }
            }

            sale.applyPricing(cart);
            for (PricedLine line : lines) {
                inventory.applyDeltaLocked(line.product(), line.quantity().negate(), MovementType.SALE,
                        "Sale " + saleId, cashier, saleId, after);
            }
            sale.complete(method, paid, change, LocalDateTime.now(clock));
            sales.save(sale);
            return sale;
        });
        openSales.remove(saleId);
        inventory.runAfter(after);
        return done;
    }

    // ---- voiding ----

    /**
     * Manager-only. Returns eligible quantities to stock with VOID_RETURN movements and marks
     * the sale VOIDED; the sale and its receipt are never deleted. Expired perishables are not
     * restocked (decision D10) and the sale records a note about it.
     */
    public Sale voidSale(String saleId, String actorUser, Role actorRole) {
        if (actorRole != Role.MANAGER) {
            throw new UnauthorizedActionException("Only a manager can void a sale");
        }
        Sale sale = sales.findById(saleId)
                .orElseThrow(() -> new SaleStateException("No completed sale with id " + saleId));
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new SaleStateException("Only a completed sale can be voided; sale " + saleId
                    + " is " + sale.getStatus());
        }
        List<String> skus = new ArrayList<>();
        for (SaleItem item : sale.getItems()) {
            skus.add(item.sku());
        }
        List<Runnable> after = new ArrayList<>();
        Sale voided = inventory.withLocks(skus, () -> {
            synchronized (sale) {
                if (sale.getStatus() != SaleStatus.COMPLETED) {
                    throw new SaleStateException("Sale " + saleId + " is already " + sale.getStatus());
                }
                LocalDate today = LocalDate.now(clock);
                List<String> notes = new ArrayList<>();
                for (SaleItem item : sale.getItems()) {
                    Product product = requireProduct(item.sku());
                    if (product.isExpired(today)) {
                        notes.add(item.sku() + " x" + item.quantity().toPlainString() + " not restocked (expired)");
                    } else {
                        inventory.applyDeltaLocked(product, item.quantity(), MovementType.VOID_RETURN,
                                "Void of " + saleId, actorUser, saleId, after);
                    }
                }
                sale.markVoided(actorUser, LocalDateTime.now(clock), String.join("; ", notes));
                sales.save(sale);
                return sale;
            }
        });
        inventory.runAfter(after);
        return voided;
    }

    // ---- lookups ----

    /** Any sale by id: open (this session), completed or voided. */
    public Sale findSale(String saleId) {
        Sale open = openSales.get(saleId);
        if (open != null) {
            return open;
        }
        return sales.findById(saleId).orElseThrow(() -> new SaleStateException("No sale with id " + saleId));
    }

    /** The cashier's own completed sales for a given day (the current shift). */
    public List<Sale> completedSalesBy(String cashier, LocalDate day) {
        List<Sale> result = new ArrayList<>();
        for (Sale s : sales.findAll()) {
            if (s.getStatus() == SaleStatus.COMPLETED && s.getCashier().equals(cashier)
                    && s.getCompletedAt().toLocalDate().equals(day)) {
                result.add(s);
            }
        }
        return result;
    }

    // ---- helpers ----

    private Sale requireOpenSale(String saleId, String cashier) {
        Sale sale = openSales.get(saleId);
        if (sale == null) {
            throw new SaleStateException("No open sale with id " + saleId);
        }
        if (!sale.getCashier().equals(cashier)) {
            throw new UnauthorizedActionException("Sale " + saleId + " belongs to another cashier");
        }
        return sale;
    }

    private Product requireProduct(String sku) {
        return products.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + sku));
    }
}

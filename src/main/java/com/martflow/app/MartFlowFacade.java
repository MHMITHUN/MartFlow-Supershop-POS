package com.martflow.app;

import com.martflow.catalog.Category;
import com.martflow.catalog.CategoryRegistry;
import com.martflow.catalog.ComboProduct;
import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.Product;
import com.martflow.catalog.ProductFactory;
import com.martflow.catalog.ProductInput;
import com.martflow.catalog.iter.ProductIterator;
import com.martflow.common.NotFoundException;
import com.martflow.inventory.AlertService;
import com.martflow.inventory.InventoryService;
import com.martflow.inventory.StockObserver;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Top-level Facade (Facade pattern) that the REST controllers talk to: inventory browsing
 * (the iterator views), catalog administration, stock movements and the alert center behind
 * one demo-friendly API. Billing, purchasing, returns and reports each have their own
 * facade/service built around this catalog core.
 *
 * <p>Plain POJO built by {@code AppConfig} — no Spring annotations here. Catalog edits of
 * existing items are role-gated here (one layer above the repository proxy).
 */
public class MartFlowFacade {

    private final InventoryCatalog catalog;
    private final InventoryService inventoryService;
    private final AlertService alerts;
    private final Map<String, ProductFactory> factories;
    private final List<StockObserver> watchers;

    public MartFlowFacade(InventoryCatalog catalog, InventoryService inventoryService,
                          AlertService alerts, Map<String, ProductFactory> factories,
                          List<StockObserver> watchers) {
        this.catalog = catalog;
        this.inventoryService = inventoryService;
        this.alerts = alerts;
        this.factories = factories;
        this.watchers = List.copyOf(watchers);
    }

    /** For the billing/purchasing wiring — the same single catalog and stock chokepoint. */
    public InventoryCatalog catalog() {
        return catalog;
    }

    public InventoryService inventory() {
        return inventoryService;
    }

    // ---------------- browsing ----------------

    /**
     * Lists inventory items with server-side filters. The {@code view} parameter selects one of
     * the catalog's custom Iterator views: {@code in_stock}, {@code low_stock} or
     * {@code expiring} (within {@code days}, default 14). Text query matches name/SKU/barcode
     * case-insensitively.
     */
    public List<Product> listProducts(String query, String categoryId, String view, Integer days,
                                      BigDecimal maxPrice) {
        List<Product> source = switch (view == null ? "" : view.toLowerCase(Locale.ROOT)) {
            case "in_stock" -> collect(catalog.browseInStock());
            case "low_stock" -> collect(catalog.browseLowStock());
            case "expiring" -> collect(catalog.browseExpiring(days == null ? 14 : days));
            default -> catalog.getAll();
        };
        List<Product> result = new ArrayList<>();
        for (Product product : source) {
            if (maxPrice != null && product.getPrice().compareTo(maxPrice) > 0) {
                continue;
            }
            if (categoryId != null && !categoryId.isBlank()
                    && !categoryId.equals(product.getCategoryId())) {
                continue;
            }
            if (query != null && !query.isBlank() && !matches(product, query.trim())) {
                continue;
            }
            result.add(product);
        }
        return result;
    }

    private boolean matches(Product product, String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        return contains(product.getName(), needle)
                || contains(product.getSku(), needle)
                || contains(product.getBarcode(), needle);
    }

    private boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private List<Product> collect(ProductIterator iterator) {
        List<Product> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    public Product getProduct(String id) {
        return catalog.findById(id)
                .orElseThrow(() -> new NotFoundException("Unknown product: " + id));
    }

    /** Barcode lookup — the till's primary key. */
    public Product findByBarcode(String barcode) {
        return catalog.findByBarcode(barcode)
                .orElseThrow(() -> new NotFoundException("Unknown barcode: " + barcode));
    }

    public List<Category> categories() {
        return CategoryRegistry.all();
    }

    // ---------------- catalog administration ----------------

    /** Creates an item via the matching {@link ProductFactory} (Factory Method in action). */
    public Product createProduct(String type, ProductInput input) {
        ProductFactory factory = factories.get(type == null ? "" : type.toUpperCase(Locale.ROOT));
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown product type: " + type + " (use UNIT or WEIGHED; combos use /combo)");
        }
        Product product = factory.create(newId("p"), input);
        return addAndWatch(product);
    }

    /** Creates a combo/hamper from existing component items (Composite in action). */
    public Product createCombo(String sku, String barcode, String name, String description,
                               String categoryId, List<String> componentIds, BigDecimal fixedPrice) {
        if (componentIds == null || componentIds.isEmpty()) {
            throw new IllegalArgumentException("A combo needs at least one component");
        }
        List<Product> components = new ArrayList<>();
        for (String componentId : componentIds) {
            components.add(getProduct(componentId)); // 404s on unknown component
        }
        ComboProduct combo = new ComboProduct(newId("c"), sku, barcode, name, description,
                categoryId, components, fixedPrice);
        return addAndWatch(combo);
    }

    /**
     * Edits catalog data of an existing item (name, description, cost, price, reorder level).
     * Manager-only: the till price is not a cashier decision. A price change fires a
     * PRICE_CHANGE event; every change persists.
     */
    public Product updateProduct(String id, String name, String description,
                                 BigDecimal costPrice, BigDecimal price, Integer reorderLevel) {
        RoleGate.requireAtLeast(Role.MANAGER);
        Product product = getProduct(id);
        if (name != null && !name.isBlank()) {
            product.setName(name);
        }
        if (description != null) {
            product.setDescription(description);
        }
        if (costPrice != null) {
            product.setCostPrice(costPrice);
        }
        if (reorderLevel != null) {
            product.setReorderLevel(reorderLevel);
        }
        if (price != null) {
            inventoryService.applyPrice(id, price); // fires PRICE_CHANGE + persists
        }
        catalog.replaceProduct(product);
        return product;
    }

    public void deleteProduct(String id) {
        getProduct(id); // 404 on unknown
        catalog.removeProduct(id); // proxy enforces ADMIN here
    }

    /** Adds stock (manual restock) through the inventory chokepoint. */
    public Product restock(String id, BigDecimal quantity, String batchNo, LocalDate expiry) {
        inventoryService.restock(id, quantity, batchNo, expiry);
        return getProduct(id);
    }

    /** Records shrinkage (damage/loss/theft/count correction) through the chokepoint. */
    public Product adjustStock(String id, String reason, BigDecimal quantity, String note) {
        com.martflow.inventory.StockAdjustment.Reason parsed;
        try {
            parsed = com.martflow.inventory.StockAdjustment.Reason.valueOf(
                    reason == null ? "" : reason.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown adjustment reason: " + reason
                    + " (use DAMAGE, LOSS, THEFT or COUNT)");
        }
        inventoryService.adjust(id, parsed, quantity, note);
        return getProduct(id);
    }

    // ---------------- alerts ----------------

    public List<AlertService.Alert> alerts(boolean unreadOnly) {
        return unreadOnly ? alerts.unread() : alerts.all();
    }

    public void markAlertRead(String alertId) {
        if (!alerts.markRead(alertId)) {
            throw new NotFoundException("Unknown alert: " + alertId);
        }
    }

    // ---------------- helpers ----------------

    /**
     * Adds to the catalog (the repository proxy enforces MANAGER for creates) and wires the
     * inventory watchers onto the new item, so it raises alerts like every other product.
     */
    private Product addAndWatch(Product product) {
        catalog.addProduct(product);
        for (StockObserver watcher : watchers) {
            product.subscribe(watcher);
        }
        return product;
    }

    private static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }
}

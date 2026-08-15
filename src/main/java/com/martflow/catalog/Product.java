package com.martflow.catalog;

import com.martflow.common.MoneyUtil;
import com.martflow.inventory.StockEvent;
import com.martflow.inventory.StockObserver;
import com.martflow.inventory.StockSubject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One sellable inventory item of the supershop. Abstract so creation varies by type (Factory
 * Method) while this base carries the shared state: identity, category/VAT, costing, stock,
 * reorder level, batches and observer wiring.
 *
 * <p><b>Pattern: Subject</b> in the Observer pattern — the alert service and the reorder
 * suggester subscribe to every product and react to restocks, low-stock crossings and price
 * changes.
 *
 * <p>Stock invariant: {@code stock} is mutated only via {@link #consume}, {@link #restock} and
 * {@link #restore} — the callers (InventoryService) persist afterwards. Weighed items
 * (KG/LITRE) keep fractional stock at 3dp; piece/pack items keep whole numbers.
 */
public abstract class Product implements StockSubject {

    private final String id;
    private String sku;
    private String barcode;
    private String name;
    private String description;
    private final String categoryId;
    private String supplierId;
    private final ProductUnit unit;
    private BigDecimal costPrice;
    private BigDecimal stock;
    private int reorderLevel;
    private final List<Batch> batches = new ArrayList<>();

    private final List<StockObserver> observers = new ArrayList<>();

    protected Product(String id, String sku, String barcode, String name, String description,
                      String categoryId, String supplierId, ProductUnit unit,
                      BigDecimal costPrice, BigDecimal stock, int reorderLevel) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Product id is required");
        }
        this.id = id;
        this.sku = sku;
        this.barcode = barcode;
        this.name = name;
        this.description = description;
        this.categoryId = CategoryRegistry.get(categoryId).id(); // validates the reference
        this.supplierId = supplierId;
        this.unit = unit == null ? ProductUnit.PIECE : unit;
        this.costPrice = MoneyUtil.round(costPrice == null ? BigDecimal.ZERO : costPrice);
        this.stock = normalize(stock == null ? BigDecimal.ZERO : stock);
        this.reorderLevel = reorderLevel;
    }

    /** Discriminator used by persistence and the API: "UNIT", "WEIGHED" or "COMBO". */
    public abstract String getType();

    /** The price a customer pays for one unit of measure (MRP for unit items, per-kg for weighed). */
    public abstract BigDecimal getPrice();

    /** Subclass hook behind {@link #setPrice}. */
    protected abstract void applyPrice(BigDecimal newPrice);

    /** Whole-number stock for PIECE/PACK, 3dp for KG/LITRE. */
    protected final BigDecimal normalize(BigDecimal qty) {
        if (unit.isWeighed()) {
            return MoneyUtil.roundQty(qty);
        }
        BigDecimal stripped = qty.stripTrailingZeros();
        if (stripped.scale() > 0) {
            throw new IllegalArgumentException(
                    name + " sells by " + unit + " — quantity must be whole, got " + qty);
        }
        return qty.setScale(0);
    }

    // ---- Observer (Subject) ----

    @Override
    public void subscribe(StockObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    @Override
    public void unsubscribe(StockObserver observer) {
        observers.remove(observer);
    }

    protected void notifyObservers(StockEvent event) {
        for (StockObserver observer : new ArrayList<>(observers)) {
            observer.update(event);
        }
    }

    /**
     * Raises a service-originated event (wastage, expiry) to this product's subscribers. Used by
     * the inventory/expiry services; combos fan the event out to their components.
     */
    public synchronized void raise(StockEvent event) {
        notifyObservers(event);
    }

    // ---- Stock movements (the only sanctioned stock mutations) ----

    /**
     * Adds stock (from a goods receipt or manual restock) and records the batch. Fires a RESTOCK
     * event for the alert center.
     */
    public synchronized void restock(BigDecimal quantity, Batch batch) {
        BigDecimal qty = normalize(quantity);
        if (qty.signum() <= 0) {
            throw new IllegalArgumentException("Restock quantity must be positive for " + name);
        }
        BigDecimal oldStock = this.stock;
        this.stock = normalize(this.stock.add(qty));
        if (batch != null) {
            batches.add(batch);
        }
        notifyObservers(StockEvent.restocked(this, oldStock, this.stock));
    }

    /**
     * Removes stock (a sale). Throws when insufficient — the caller rolls back. Fires a LOW_STOCK
     * event when the reorder level is crossed.
     */
    public synchronized void consume(BigDecimal quantity) {
        BigDecimal qty = normalize(quantity);
        if (qty.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive for " + name);
        }
        if (qty.compareTo(this.stock) > 0) {
            throw new IllegalStateException(
                    "Insufficient stock for " + name + ": have " + this.stock + ", need " + qty);
        }
        BigDecimal oldStock = this.stock;
        this.stock = normalize(this.stock.subtract(qty));
        if (oldStock.compareTo(BigDecimal.valueOf(reorderLevel)) > 0
                && this.stock.compareTo(BigDecimal.valueOf(reorderLevel)) <= 0) {
            notifyObservers(StockEvent.lowStock(this, oldStock, this.stock, reorderLevel));
        }
    }

    /** Adds stock back silently (void/return/rollback paths — no event noise). */
    public synchronized void restore(BigDecimal quantity) {
        BigDecimal qty = normalize(quantity);
        if (qty.signum() <= 0) {
            throw new IllegalArgumentException("Restore quantity must be positive for " + name);
        }
        this.stock = normalize(this.stock.add(qty));
    }

    /** Silently overwrites the stock level (physical count corrections). */
    protected synchronized void setStockLevel(BigDecimal newStock) {
        this.stock = normalize(newStock);
    }

    /**
     * Changes the selling price and fires a PRICE_CHANGE event. Route through the inventory
     * service so the change is persisted.
     */
    public synchronized void setPrice(BigDecimal newPrice) {
        BigDecimal price = MoneyUtil.round(MoneyUtil.requirePositive(newPrice, "Price for " + name));
        BigDecimal old = getPrice();
        applyPrice(price);
        if (old.compareTo(price) != 0) {
            notifyObservers(StockEvent.priceChanged(this, old, price));
        }
    }

    // ---- Accessors ----

    public String getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(String supplierId) {
        this.supplierId = supplierId;
    }

    public ProductUnit getUnit() {
        return unit;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = MoneyUtil.round(costPrice == null ? BigDecimal.ZERO : costPrice);
    }

    public BigDecimal getStock() {
        return stock;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(int reorderLevel) {
        if (reorderLevel < 0) {
            throw new IllegalArgumentException("Reorder level cannot be negative");
        }
        this.reorderLevel = reorderLevel;
    }

    public List<Batch> getBatches() {
        return List.copyOf(batches);
    }

    /**
     * Records a batch without touching stock — the persistence hook used when reloading products
     * from storage (stock is already set on the loaded object). Live receipts use
     * {@link #restock} instead, which moves stock and records the batch together.
     */
    public synchronized void addBatch(Batch batch) {
        if (batch != null) {
            batches.add(batch);
        }
    }

    /** {@code true} when stock is at or below the reorder level. */
    public boolean isLowStock() {
        return stock.compareTo(BigDecimal.valueOf(reorderLevel)) <= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product p)) return false;
        return Objects.equals(id, p.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

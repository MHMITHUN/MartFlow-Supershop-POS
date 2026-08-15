package com.martflow.catalog;

import com.martflow.common.MoneyUtil;
import com.martflow.inventory.StockEvent;
import com.martflow.inventory.StockObserver;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A combo/hamper sold as one line at the till — "Eid Hamper", "Breakfast Combo".
 *
 * <p><b>Pattern: Composite.</b> A combo is a {@link Product} like any leaf item: it can be
 * added to a bill, priced and stocked. Operations fan out to the components:
 * <ul>
 *   <li>price = {@code fixedPrice} when set, else the sum of component prices;</li>
 *   <li>stock = the scarcest component (that is how many combos can actually be built);</li>
 *   <li>{@code consume} deducts from every component (each firing its own low-stock event), so
 *       component-level stock stays truthful;</li>
 *   <li>observer subscriptions fan out too, so components keep raising their own alerts.</li>
 * </ul>
 * Restocking and batches happen on the components (via goods receipts), never on the combo.
 */
public class ComboProduct extends Product {

    private final List<Product> components;
    private BigDecimal fixedPrice; // nullable — null means "sum of components"

    public ComboProduct(String id, String sku, String barcode, String name, String description,
                        String categoryId, List<Product> components, BigDecimal fixedPrice) {
        super(id, sku, barcode, name, description, categoryId, null, ProductUnit.PACK,
                combinedCost(components), BigDecimal.ZERO, 0);
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("Combo " + name + " needs at least one component");
        }
        for (Product component : components) {
            if (component instanceof ComboProduct) {
                throw new IllegalArgumentException(
                        "Combo-in-combo is not allowed: " + component.getName() + " is itself a combo");
            }
        }
        this.components = List.copyOf(components);
        this.fixedPrice = fixedPrice == null ? null : MoneyUtil.round(fixedPrice);
    }

    private static BigDecimal combinedCost(List<Product> components) {
        BigDecimal total = BigDecimal.ZERO;
        for (Product component : components) {
            total = total.add(component.getCostPrice());
        }
        return total;
    }

    public List<Product> getComponents() {
        return components;
    }

    public List<String> getComponentIds() {
        List<String> ids = new ArrayList<>();
        for (Product component : components) {
            ids.add(component.getId());
        }
        return ids;
    }

    public BigDecimal getFixedPrice() {
        return fixedPrice;
    }

    /** Combo stock is the scarcest component — how many complete combos exist. */
    @Override
    public BigDecimal getStock() {
        BigDecimal min = null;
        for (Product component : components) {
            BigDecimal s = component.getStock();
            if (min == null || s.compareTo(min) < 0) {
                min = s;
            }
        }
        return min == null ? BigDecimal.ZERO : min;
    }

    @Override
    public boolean isLowStock() {
        // A combo is low when any component is low — the combo itself has no reorder level.
        for (Product component : components) {
            if (component.isLowStock()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getType() {
        return "COMBO";
    }

    @Override
    public BigDecimal getPrice() {
        if (fixedPrice != null) {
            return fixedPrice;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Product component : components) {
            sum = sum.add(component.getPrice());
        }
        return sum;
    }

    @Override
    protected void applyPrice(BigDecimal newPrice) {
        this.fixedPrice = newPrice;
    }

    @Override
    public synchronized void consume(java.math.BigDecimal quantity) {
        // Pre-check every component first so a short one cannot leave the others consumed.
        for (Product component : components) {
            if (component.getStock().compareTo(quantity) < 0) {
                throw new IllegalStateException("Insufficient stock for " + component.getName()
                        + ": have " + component.getStock() + ", need " + quantity);
            }
        }
        for (Product component : components) {
            component.consume(quantity);
        }
    }

    @Override
    public synchronized void restock(java.math.BigDecimal quantity, Batch batch) {
        throw new UnsupportedOperationException(
                "Combos are stocked through their components — receive a goods receipt for " + getName());
    }

    @Override
    public synchronized void restore(java.math.BigDecimal quantity) {
        for (Product component : components) {
            component.restore(quantity);
        }
    }

    @Override
    protected synchronized void setStockLevel(java.math.BigDecimal newStock) {
        throw new UnsupportedOperationException("Combo stock derives from components: " + getName());
    }

    // ---- Observer fan-out ----

    @Override
    public void subscribe(StockObserver observer) {
        for (Product component : components) {
            component.subscribe(observer);
        }
    }

    @Override
    public void unsubscribe(StockObserver observer) {
        for (Product component : components) {
            component.unsubscribe(observer);
        }
    }

    @Override
    protected void notifyObservers(StockEvent event) {
        // Combos carry no observers of their own — price/stock events originate from the
        // components, which have their own subscriptions. Silently ignore instead of throwing
        // so base-class mutators (e.g. setPrice) keep working on combos.
    }
}

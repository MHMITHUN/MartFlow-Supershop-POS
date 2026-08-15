package com.martflow.inventory;

import com.martflow.catalog.Product;

import java.math.BigDecimal;

/**
 * Immutable inventory event fired by a {@link StockSubject} (or raised directly by a service for
 * expiry/wastage). Carries plain data instead of the product object so the inventory package
 * stays decoupled from the catalog.
 */
public final class StockEvent {

    public enum Type {
        RESTOCK,
        LOW_STOCK,
        PRICE_CHANGE,
        EXPIRY_SOON,
        SHRINKAGE
    }

    private final Type type;
    private final String productId;
    private final String productName;
    private final BigDecimal oldStock;   // nullable
    private final BigDecimal newStock;   // nullable
    private final String message;

    private StockEvent(Type type, String productId, String productName,
                       BigDecimal oldStock, BigDecimal newStock, String message) {
        this.type = type;
        this.productId = productId;
        this.productName = productName;
        this.oldStock = oldStock;
        this.newStock = newStock;
        this.message = message;
    }

    // ---- Event factories ----

    public static StockEvent restocked(Product p, BigDecimal oldStock, BigDecimal newStock) {
        return new StockEvent(Type.RESTOCK, p.getId(), p.getName(), oldStock, newStock,
                p.getName() + " restocked: " + oldStock.stripTrailingZeros().toPlainString()
                        + " -> " + newStock.stripTrailingZeros().toPlainString() + " " + p.getUnit());
    }

    public static StockEvent lowStock(Product p, BigDecimal oldStock, BigDecimal newStock, int reorderLevel) {
        return new StockEvent(Type.LOW_STOCK, p.getId(), p.getName(), oldStock, newStock,
                p.getName() + " is low: " + newStock.stripTrailingZeros().toPlainString()
                        + " " + p.getUnit() + " left (reorder level " + reorderLevel + ")");
    }

    public static StockEvent priceChanged(Product p, BigDecimal oldPrice, BigDecimal newPrice) {
        return new StockEvent(Type.PRICE_CHANGE, p.getId(), p.getName(), null, null,
                p.getName() + " price changed: BDT " + oldPrice + " -> " + newPrice);
    }

    /** Raised by the expiry watcher (not by the product itself). */
    public static StockEvent expirySoon(Product p, String detail) {
        return new StockEvent(Type.EXPIRY_SOON, p.getId(), p.getName(), null, null,
                p.getName() + " — " + detail);
    }

    /** Custom-message event derived from another one (the reorder suggester uses this). */
    public static StockEvent custom(Type type, String productId, String productName,
                                    BigDecimal oldStock, BigDecimal newStock, String message) {
        return new StockEvent(type, productId, productName, oldStock, newStock, message);
    }

    /** Raised by the wastage/shrinkage flow (damage, loss, theft, count correction). */
    public static StockEvent shrinkage(Product p, String detail) {
        return new StockEvent(Type.SHRINKAGE, p.getId(), p.getName(), null, null,
                p.getName() + " — " + detail);
    }

    public Type getType() {
        return type;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getOldStock() {
        return oldStock;
    }

    public BigDecimal getNewStock() {
        return newStock;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "[" + type + "] " + message;
    }
}

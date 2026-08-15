package com.martflow.inventory;

import com.martflow.catalog.Batch;
import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.Product;
import com.martflow.common.MoneyUtil;
import com.martflow.common.NotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The only sanctioned path for stock and price mutations — the stock chokepoint.
 *
 * <p>Fixes the storefront's worst bug: stock decrements used to mutate the in-memory object and
 * never persisted, so a restart silently restored sold stock. Every method here mutates the
 * product (which fires the Observer events) and then persists through the catalog before
 * returning. All operations are synchronized so concurrent bills and goods receipts serialize.
 */
public class InventoryService {

    private final InventoryCatalog catalog;

    public InventoryService(InventoryCatalog catalog) {
        this.catalog = catalog;
    }

    /** Removes stock (a sale / a reservation). Persists before returning. */
    public synchronized void consume(String productId, BigDecimal quantity) {
        Product product = require(productId);
        product.consume(quantity);
        persist(product);
    }

    /** Puts stock back (a voided/rolled-back tender). Persists before returning. */
    public synchronized void restore(String productId, BigDecimal quantity) {
        Product product = require(productId);
        product.restore(quantity);
        persist(product);
    }

    /** Adds stock (a goods receipt / manual restock), recording the batch. Persists. */
    public synchronized void restock(String productId, BigDecimal quantity, String batchNo, LocalDate expiry) {
        Product product = require(productId);
        Batch batch = batchNo == null || batchNo.isBlank() ? null
                : new Batch(batchNo, expiry, quantity);
        product.restock(quantity, batch);
        persist(product);
    }

    /**
     * Records shrinkage: damage, loss, theft or a count correction. Positive quantities remove
     * stock, negative quantities add it back (count corrections can go either way). Raises a
     * SHRINKAGE event. Persists.
     */
    public synchronized void adjust(String productId, StockAdjustment.Reason reason,
                                    BigDecimal deltaQuantity, String note) {
        Product product = require(productId);
        BigDecimal delta = deltaQuantity == null ? BigDecimal.ZERO : deltaQuantity;
        if (delta.signum() == 0) {
            throw new IllegalArgumentException("Adjustment quantity cannot be zero");
        }
        if (delta.signum() > 0) {
            product.consume(delta);
        } else {
            product.restore(delta.negate());
        }
        String detail = reason.label() + ": "
                + delta.abs().stripTrailingZeros().toPlainString() + " " + product.getUnit()
                + (note == null || note.isBlank() ? "" : " — " + note);
        persist(product);
        product.raise(StockEvent.shrinkage(product, detail));
    }

    /** Applies a new selling price (MRP / per-unit) and persists. Fires PRICE_CHANGE. */
    public synchronized void applyPrice(String productId, BigDecimal newPrice) {
        Product product = require(productId);
        product.setPrice(newPrice);
        persist(product);
    }

    /**
     * Updates an item's cost price (goods receipts land the latest purchase cost). Persisted;
     * no event — cost is internal accounting, not a shelf-price change.
     */
    public synchronized void updateCost(String productId, BigDecimal newCost) {
        Product product = require(productId);
        product.setCostPrice(MoneyUtil.round(newCost));
        persist(product);
    }

    private Product require(String productId) {
        return catalog.findById(productId)
                .orElseThrow(() -> new NotFoundException("Unknown product: " + productId));
    }

    private void persist(Product product) {
        catalog.replaceProduct(product);
    }
}

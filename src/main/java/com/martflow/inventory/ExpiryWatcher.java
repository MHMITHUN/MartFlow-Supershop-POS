package com.martflow.inventory;

import com.martflow.catalog.Batch;
import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.Product;
import com.martflow.common.TimeSource;

import java.time.LocalDate;

/**
 * Raises EXPIRY_SOON alerts for batches nearing their use-by date (and already-expired ones —
 * they need pulling off the shelf even more urgently). Runs once at startup after the observers
 * are wired, and again whenever a goods receipt lands new batches (purchasing phase).
 */
public class ExpiryWatcher {

    private final InventoryCatalog catalog;

    public ExpiryWatcher(InventoryCatalog catalog) {
        this.catalog = catalog;
    }

    /** Raises alerts for every batch expiring within {@code days} (default 14). */
    public void watch(int days) {
        watch(days, null);
    }

    /**
     * Same check scoped to the given product ids — used after a goods receipt so only the
     * freshly landed batches are examined (no duplicate alerts for old stock on every GRN).
     */
    public void watch(int days, java.util.Collection<String> productIds) {
        LocalDate today = TimeSource.today();
        LocalDate cutoff = today.plusDays(days);
        for (Product product : catalog.getAll()) {
            if (productIds != null && !productIds.contains(product.getId())) {
                continue;
            }
            for (Batch batch : product.getBatches()) {
                if (batch.expiry() == null || batch.expiry().isAfter(cutoff)) {
                    continue;
                }
                String detail = batch.expiry().isBefore(today)
                        ? "batch " + batch.batchNo() + " EXPIRED on " + batch.expiry() + " — pull it off the shelf"
                        : "batch " + batch.batchNo() + " expires on " + batch.expiry()
                                + " (" + java.time.temporal.ChronoUnit.DAYS.between(today, batch.expiry())
                                + " days) — markdown or return to supplier";
                product.raise(StockEvent.expirySoon(product, detail));
            }
        }
    }
}

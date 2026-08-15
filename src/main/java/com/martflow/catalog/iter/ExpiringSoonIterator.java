package com.martflow.catalog.iter;

import com.martflow.catalog.Batch;
import com.martflow.catalog.Product;

import java.time.LocalDate;
import java.util.List;

/**
 * <b>Pattern: Iterator.</b> Iterates only over items that have a batch expiring on or before a
 * cutoff date (already-expired batches included — they need action even more urgently). This is
 * the wastage-control walk: markdown or pull the item before it spoils.
 */
public final class ExpiringSoonIterator extends AbstractProductIterator {

    private final LocalDate cutoff;

    public ExpiringSoonIterator(List<Product> source, LocalDate cutoff) {
        super(source);
        this.cutoff = cutoff;
    }

    @Override
    protected boolean matches(Product product) {
        for (Batch batch : product.getBatches()) {
            if (batch.expiry() != null && !batch.expiry().isAfter(cutoff)) {
                return true;
            }
        }
        return false;
    }
}

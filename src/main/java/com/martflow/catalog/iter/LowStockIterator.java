package com.martflow.catalog.iter;

import com.martflow.catalog.Product;

import java.util.List;

/**
 * <b>Pattern: Iterator.</b> Iterates only over items at or below their reorder level — the
 * purchasing team's daily "what to reorder" walk of the shelves.
 */
public final class LowStockIterator extends AbstractProductIterator {

    public LowStockIterator(List<Product> source) {
        super(source);
    }

    @Override
    protected boolean matches(Product product) {
        return product.isLowStock();
    }
}

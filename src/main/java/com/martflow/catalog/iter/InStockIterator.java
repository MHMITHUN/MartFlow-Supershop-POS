package com.martflow.catalog.iter;

import com.martflow.catalog.Product;

import java.util.List;

/**
 * <b>Pattern: Iterator.</b> Iterates only over inventory items that currently have stock
 * on hand, skipping sold-out items — the default "what can I sell right now" view.
 */
public final class InStockIterator extends AbstractProductIterator {

    public InStockIterator(List<Product> source) {
        super(source);
    }

    @Override
    protected boolean matches(Product product) {
        return product.getStock().signum() > 0;
    }
}

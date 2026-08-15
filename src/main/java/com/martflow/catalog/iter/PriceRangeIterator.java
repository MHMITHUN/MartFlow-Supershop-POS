package com.martflow.catalog.iter;

import com.martflow.catalog.Product;

import java.math.BigDecimal;
import java.util.List;

/**
 * <b>Pattern: Iterator.</b> Iterates only over items priced at or below a maximum — a
 * "budget browse" of the shelves.
 */
public final class PriceRangeIterator extends AbstractProductIterator {

    private final BigDecimal maxPrice;

    public PriceRangeIterator(List<Product> source, BigDecimal maxPrice) {
        super(source);
        this.maxPrice = maxPrice;
    }

    @Override
    protected boolean matches(Product product) {
        return product.getPrice().compareTo(maxPrice) <= 0;
    }
}

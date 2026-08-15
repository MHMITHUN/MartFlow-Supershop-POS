package com.martflow.catalog.iter;

import com.martflow.catalog.Product;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Skeleton for a filtering {@link ProductIterator}: advances the cursor past non-matching products,
 * and provides {@link #reset()}. Subclasses just define {@link #matches(Product)}.
 */
abstract class AbstractProductIterator implements ProductIterator {

    protected final List<Product> source;
    protected int cursor = 0;

    protected AbstractProductIterator(List<Product> source) {
        this.source = source;
    }

    protected abstract boolean matches(Product product);

    @Override
    public boolean hasNext() {
        while (cursor < source.size() && !matches(source.get(cursor))) {
            cursor++;
        }
        return cursor < source.size();
    }

    @Override
    public Product next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return source.get(cursor++);
    }

    @Override
    public void reset() {
        cursor = 0;
    }
}

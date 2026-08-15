package com.martflow.catalog.iter;

import com.martflow.catalog.Product;

/**
 * <b>Pattern: Iterator.</b> A custom, re-iterable cursor over (possibly filtered) catalog products.
 * Unlike a Java {@code stream}, it supports {@link #reset()} so the same filtered view can be walked
 * more than once, and concrete iterators encode domain queries ("in stock", "under $X") as reusable,
 * named objects rather than inline predicates.
 */
public interface ProductIterator {

    boolean hasNext();

    Product next();

    /** Returns the cursor to the start so the iteration can be repeated. */
    void reset();
}

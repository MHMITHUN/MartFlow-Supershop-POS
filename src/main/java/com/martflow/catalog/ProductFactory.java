package com.martflow.catalog;

import com.martflow.common.MoneyUtil;

import java.math.BigDecimal;

/**
 * <b>Pattern: Factory Method.</b> Declares the creation interface for inventory items; each
 * subclass decides which concrete {@link Product} type materializes from a {@link ProductInput}.
 * The manager's "add item" form therefore has exactly one creation path regardless of whether the
 * item is sold per piece or per kilo.
 */
public abstract class ProductFactory {

    /** Template: validates the shared input, then defers to the subclass creation hook. */
    public final Product create(String id, ProductInput in) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Product id is required");
        }
        if (in == null || in.name() == null || in.name().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        MoneyUtil.requirePositive(in.price(), "Price for " + (in == null ? "?" : in.name()));
        BigDecimal stock = in.stock() == null ? BigDecimal.ZERO : in.stock();
        if (stock.signum() < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        return createProduct(id, in);
    }

    /** The creation hook subclasses implement. */
    protected abstract Product createProduct(String id, ProductInput in);

    /** The type discriminator this factory produces: "UNIT" or "WEIGHED". */
    public abstract String supportedType();
}

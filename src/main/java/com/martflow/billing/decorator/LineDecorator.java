package com.martflow.billing.decorator;

import com.martflow.billing.item.BillableItem;

import java.math.BigDecimal;

/**
 * <b>Pattern: Decorator.</b> Wraps any {@link BillableItem} and modifies what the line costs —
 * a promotion discount, a carry-bag charge, a delivery fee, the till's round-off — without
 * touching the line classes. Everything not overridden delegates to the wrapped item, so
 * decorators stack transparently.
 */
public abstract class LineDecorator implements BillableItem {

    protected final BillableItem inner;

    protected LineDecorator(BillableItem inner) {
        this.inner = inner;
    }

    /** The wrapped item — visitors and tests use this to inspect the chain. */
    public BillableItem inner() {
        return inner;
    }

    @Override
    public BigDecimal quantity() {
        return inner.quantity();
    }

    @Override
    public BigDecimal unitPrice() {
        return inner.unitPrice();
    }

    @Override
    public String name() {
        return inner.name();
    }

    @Override
    public String sku() {
        return inner.sku();
    }

    @Override
    public BigDecimal vatRate() {
        return inner.vatRate();
    }

    @Override
    public String categoryId() {
        return inner.categoryId();
    }

    @Override
    public String productId() {
        return inner.productId();
    }

    @Override
    public BigDecimal unitCost() {
        return inner.unitCost();
    }
}

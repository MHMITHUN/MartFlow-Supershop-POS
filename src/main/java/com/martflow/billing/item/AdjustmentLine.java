package com.martflow.billing.item;

import com.martflow.billing.visitor.BillItemVisitor;

import java.math.BigDecimal;

/**
 * A zero-priced anchor line that exists to be wrapped by bill-level charge decorators (carry
 * bag, delivery, round-off). Not stockable, not taxable by itself — the decorators on top of it
 * carry the amount.
 */
public final class AdjustmentLine implements BillableItem {

    private final String label;

    public AdjustmentLine(String label) {
        this.label = label == null || label.isBlank() ? "Adjustment" : label;
    }

    @Override
    public BigDecimal quantity() {
        return BigDecimal.ONE;
    }

    @Override
    public BigDecimal unitPrice() {
        return BigDecimal.ZERO.setScale(2);
    }

    @Override
    public BigDecimal lineNet() {
        return BigDecimal.ZERO.setScale(2);
    }

    @Override
    public String describe() {
        return label;
    }

    @Override
    public String name() {
        return label;
    }

    @Override
    public String sku() {
        return null;
    }

    @Override
    public BigDecimal vatRate() {
        return BigDecimal.ZERO;
    }

    @Override
    public String categoryId() {
        return null;
    }

    @Override
    public String productId() {
        return null;
    }

    @Override
    public BigDecimal unitCost() {
        return null;
    }

    @Override
    public void accept(BillItemVisitor visitor) {
        visitor.visit(this);
    }
}

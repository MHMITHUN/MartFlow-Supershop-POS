package com.martflow.billing.decorator;

import com.martflow.billing.item.BillableItem;
import com.martflow.billing.visitor.BillItemVisitor;
import com.martflow.common.MoneyUtil;

import java.math.BigDecimal;

/**
 * A percentage promotion applied to one line (category sale, member price): reduces the line's
 * net by {@code percentOff}% while keeping its identity for stock and VAT purposes.
 */
public final class LineDiscount extends LineDecorator {

    private final BigDecimal percentOff;

    public LineDiscount(BillableItem inner, BigDecimal percentOff) {
        super(inner);
        if (percentOff == null || percentOff.signum() <= 0 || percentOff.compareTo(new BigDecimal("100")) >= 0) {
            throw new IllegalArgumentException("Discount percent must be between 0 and 100");
        }
        this.percentOff = percentOff;
    }

    public BigDecimal percentOff() {
        return percentOff;
    }

    /** The amount saved on this line — feeds the bill's discount total. */
    public BigDecimal savedAmount() {
        return MoneyUtil.round(inner.lineNet().subtract(lineNet()));
    }

    @Override
    public BigDecimal lineNet() {
        BigDecimal factor = BigDecimal.ONE.subtract(
                percentOff.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP));
        return MoneyUtil.round(inner.lineNet().multiply(factor));
    }

    @Override
    public String describe() {
        return inner.describe() + " (-" + percentOff.stripTrailingZeros().toPlainString() + "%)";
    }

    @Override
    public void accept(BillItemVisitor visitor) {
        visitor.visit(this);
    }
}

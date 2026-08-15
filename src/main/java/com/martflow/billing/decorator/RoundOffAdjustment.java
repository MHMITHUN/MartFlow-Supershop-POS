package com.martflow.billing.decorator;

import com.martflow.billing.item.BillableItem;
import com.martflow.billing.visitor.BillItemVisitor;
import com.martflow.common.MoneyUtil;

import java.math.BigDecimal;

/**
 * The till's round-off: brings the payable total to a whole taka (cash drawers hate coins).
 * The delta can be positive or negative and is VAT-exempt by construction.
 */
public final class RoundOffAdjustment extends LineDecorator {

    private final BigDecimal delta;

    public RoundOffAdjustment(BillableItem inner, BigDecimal delta) {
        super(inner);
        if (delta == null || delta.signum() == 0) {
            throw new IllegalArgumentException("Round-off delta cannot be zero");
        }
        this.delta = MoneyUtil.round(delta);
    }

    public BigDecimal delta() {
        return delta;
    }

    @Override
    public BigDecimal lineNet() {
        return MoneyUtil.round(inner.lineNet().add(delta));
    }

    @Override
    public BigDecimal vatRate() {
        return BigDecimal.ZERO;
    }

    @Override
    public String describe() {
        String sign = delta.signum() > 0 ? "+" : "";
        return "Round Off " + sign + delta;
    }

    @Override
    public void accept(BillItemVisitor visitor) {
        visitor.visit(this);
    }
}

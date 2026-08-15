package com.martflow.billing.decorator;

import com.martflow.billing.item.BillableItem;
import com.martflow.billing.visitor.BillItemVisitor;
import com.martflow.common.MoneyUtil;

import java.math.BigDecimal;

/**
 * Home-delivery charge for phone orders picked up at the till: flat fee on a decorated anchor
 * line. VAT-exempt (it is a service charge on the invoice, not merchandise).
 */
public final class DeliveryFee extends LineDecorator {

    private final BigDecimal fee;

    public DeliveryFee(BillableItem inner, BigDecimal fee) {
        super(inner);
        this.fee = MoneyUtil.round(MoneyUtil.requirePositive(fee, "Delivery fee"));
    }

    public BigDecimal fee() {
        return fee;
    }

    @Override
    public BigDecimal lineNet() {
        return MoneyUtil.round(inner.lineNet().add(fee));
    }

    @Override
    public BigDecimal vatRate() {
        return BigDecimal.ZERO;
    }

    @Override
    public String describe() {
        return "Home Delivery";
    }

    @Override
    public void accept(BillItemVisitor visitor) {
        visitor.visit(this);
    }
}

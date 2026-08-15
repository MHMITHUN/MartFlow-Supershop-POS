package com.martflow.billing.decorator;

import com.martflow.billing.item.BillableItem;
import com.martflow.billing.visitor.BillItemVisitor;
import com.martflow.common.MoneyUtil;

import java.math.BigDecimal;

/**
 * The carry-bag charge (BD shops charge per bag): a flat fee added to the bill as a decorated
 * adjustment anchor. VAT-exempt.
 */
public final class CarryBagFee extends LineDecorator {

    private final BigDecimal fee;

    public CarryBagFee(BillableItem inner, BigDecimal fee) {
        super(inner);
        this.fee = MoneyUtil.round(MoneyUtil.requirePositive(fee, "Carry bag fee"));
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
        return BigDecimal.ZERO; // bag fee is not VAT-bearing
    }

    @Override
    public String describe() {
        return "Carry Bag";
    }

    @Override
    public void accept(BillItemVisitor visitor) {
        visitor.visit(this);
    }
}

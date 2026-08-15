package com.martflow.pricing;

import com.martflow.billing.decorator.LineDiscount;
import com.martflow.billing.item.BillableItem;

import java.math.BigDecimal;
import java.util.Objects;

/** The loyalty benefit: members get a small percent off every line. */
public final class MemberPrice implements PricingStrategy {

    private final BigDecimal percentOff;

    public MemberPrice(BigDecimal percentOff) {
        this.percentOff = Objects.requireNonNull(percentOff);
    }

    @Override
    public String name() {
        return "Member Price";
    }

    @Override
    public BillableItem apply(BillableItem line, PricingContext context) {
        if (context != null && context.customer() != null) {
            return new LineDiscount(line, percentOff);
        }
        return line;
    }
}

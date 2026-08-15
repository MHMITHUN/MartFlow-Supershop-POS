package com.martflow.pricing;

import com.martflow.billing.item.BillableItem;

/** The default: the shelf price, no decoration. */
public final class RegularPrice implements PricingStrategy {

    @Override
    public String name() {
        return "Regular";
    }

    @Override
    public BillableItem apply(BillableItem line, PricingContext context) {
        return line;
    }
}

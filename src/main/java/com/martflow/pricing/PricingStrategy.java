package com.martflow.pricing;

import com.martflow.billing.item.BillableItem;

/**
 * <b>Pattern: Strategy.</b> Decides how one line gets priced — regular MRP, a category sale,
 * a loyalty member price. Strategies are swapped per line and per day by the
 * {@code PromotionEngine} without any billing code changing: the till always calls
 * {@code strategy.apply(line, context)} and does not care which one won.
 */
public interface PricingStrategy {

    String name();

    /**
     * Returns the priced line — the same item, possibly wrapped in a discount decorator. Must
     * not mutate the input.
     */
    BillableItem apply(BillableItem line, PricingContext context);
}

package com.martflow.pricing;

import com.martflow.billing.decorator.LineDiscount;
import com.martflow.billing.item.BillableItem;

import java.math.BigDecimal;
import java.util.Objects;

/** A percent-off promotion on one merchandise category (e.g. "Beverages -10% this week"). */
public final class CategorySale implements PricingStrategy {

    private final String categoryId;
    private final BigDecimal percentOff;
    private final String promoName;

    public CategorySale(String categoryId, BigDecimal percentOff, String promoName) {
        this.categoryId = Objects.requireNonNull(categoryId);
        this.percentOff = Objects.requireNonNull(percentOff);
        this.promoName = promoName == null ? "Category Sale" : promoName;
    }

    public String categoryId() {
        return categoryId;
    }

    public BigDecimal percentOff() {
        return percentOff;
    }

    @Override
    public String name() {
        return promoName;
    }

    @Override
    public BillableItem apply(BillableItem line, PricingContext context) {
        if (categoryId.equals(line.categoryId())) {
            return new LineDiscount(line, percentOff);
        }
        return line;
    }
}

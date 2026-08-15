package com.martflow.billing.item;

import com.martflow.billing.visitor.BillItemVisitor;
import com.martflow.catalog.CategoryRegistry;
import com.martflow.catalog.WeighedProduct;
import com.martflow.common.MoneyUtil;

import java.math.BigDecimal;

/**
 * A weighed/measured line: {@code weight x pricePerUnit} (potatoes at 1.25 kg). Weight carries
 * 3 decimals; the line total is rounded to 2.
 */
public final class WeighedLine implements BillableItem {

    private final String productId;
    private final String sku;
    private final String name;
    private final String categoryId;
    private final BigDecimal vatRate;
    private final BigDecimal unitCost; // cost per kg
    private final BigDecimal weight;
    private final BigDecimal pricePerUnit;

    public WeighedLine(WeighedProduct product, BigDecimal weight) {
        this.productId = product.getId();
        this.sku = product.getSku();
        this.name = product.getName();
        this.categoryId = product.getCategoryId();
        this.vatRate = CategoryRegistry.vatRateOf(product.getCategoryId());
        this.unitCost = product.getCostPrice();
        this.pricePerUnit = product.getPricePerUnit();
        BigDecimal w = MoneyUtil.roundQty(weight);
        if (w.signum() <= 0) {
            throw new IllegalArgumentException("Weight must be positive for " + product.getName());
        }
        this.weight = w;
    }

    /** Reconstruction path: rebuilds the snapshot from a persisted sale line. */
    public WeighedLine(String productId, String sku, String name, String categoryId,
                       BigDecimal vatRate, BigDecimal unitCost, BigDecimal weight,
                       BigDecimal pricePerUnit) {
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.categoryId = categoryId;
        this.vatRate = vatRate;
        this.unitCost = unitCost;
        this.weight = MoneyUtil.roundQty(weight);
        this.pricePerUnit = pricePerUnit;
    }

    @Override
    public BigDecimal quantity() {
        return weight;
    }

    @Override
    public BigDecimal unitPrice() {
        return pricePerUnit;
    }

    @Override
    public BigDecimal lineNet() {
        return MoneyUtil.round(weight.multiply(pricePerUnit));
    }

    @Override
    public String describe() {
        return name + " " + weight.stripTrailingZeros().toPlainString() + "kg";
    }

    @Override
    public BigDecimal vatRate() {
        return vatRate;
    }

    @Override
    public String categoryId() {
        return categoryId;
    }

    @Override
    public String productId() {
        return productId;
    }

    @Override
    public BigDecimal unitCost() {
        return unitCost;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }


    @Override
    public String name() {
        return name;
    }

    @Override
    public String sku() {
        return sku;
    }

    @Override
    public void accept(BillItemVisitor visitor) {
        visitor.visit(this);
    }
}

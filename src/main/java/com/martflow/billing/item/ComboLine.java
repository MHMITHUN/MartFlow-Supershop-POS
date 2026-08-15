package com.martflow.billing.item;

import com.martflow.billing.visitor.BillItemVisitor;
import com.martflow.catalog.CategoryRegistry;
import com.martflow.catalog.ComboProduct;
import com.martflow.common.MoneyUtil;

import java.math.BigDecimal;

/**
 * A combo/hamper line (Composite on the billing side): priced as the combo price
 * ({@code fixedPrice} or component sum); stock reservation fans out to components through the
 * product layer.
 */
public final class ComboLine implements BillableItem {

    private final String productId;
    private final String sku;
    private final String name;
    private final String categoryId;
    private final BigDecimal vatRate;
    private final int quantity;
    private final BigDecimal comboPrice;
    private final BigDecimal unitCost; // combined component cost

    public ComboLine(ComboProduct product, int quantity) {
        this.productId = product.getId();
        this.sku = product.getSku();
        this.name = product.getName();
        this.categoryId = product.getCategoryId();
        this.vatRate = CategoryRegistry.vatRateOf(product.getCategoryId());
        this.comboPrice = product.getPrice();
        this.unitCost = product.getCostPrice();
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive for " + product.getName());
        }
        this.quantity = quantity;
    }

    /** Reconstruction path: rebuilds the snapshot from a persisted sale line. */
    public ComboLine(String productId, String sku, String name, String categoryId,
                     BigDecimal vatRate, int quantity, BigDecimal comboPrice, BigDecimal unitCost) {
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.categoryId = categoryId;
        this.vatRate = vatRate;
        this.quantity = quantity;
        this.comboPrice = comboPrice;
        this.unitCost = unitCost;
    }

    @Override
    public BigDecimal quantity() {
        return BigDecimal.valueOf(quantity);
    }

    @Override
    public BigDecimal unitPrice() {
        return comboPrice;
    }

    @Override
    public BigDecimal lineNet() {
        return MoneyUtil.round(comboPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    @Override
    public String describe() {
        return name + " x" + quantity;
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

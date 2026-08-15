package com.martflow.billing.item;

import com.martflow.billing.visitor.BillItemVisitor;
import com.martflow.catalog.UnitProduct;
import com.martflow.common.MoneyUtil;

import java.math.BigDecimal;

/**
 * A scanned piece/pack line: {@code quantity x MRP}. Snapshots the product's identity, price,
 * VAT rate and cost at construction time.
 */
public final class UnitLine implements BillableItem {

    private final String productId;
    private final String sku;
    private final String name;
    private final String categoryId;
    private final BigDecimal vatRate;
    private final BigDecimal unitCost;
    private final int quantity;
    private final BigDecimal mrp;

    public UnitLine(UnitProduct product, int quantity) {
        this.productId = product.getId();
        this.sku = product.getSku();
        this.name = product.getName();
        this.categoryId = product.getCategoryId();
        this.vatRate = com.martflow.catalog.CategoryRegistry.vatRateOf(product.getCategoryId());
        this.unitCost = product.getCostPrice();
        this.mrp = product.getMrp();
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive for " + product.getName());
        }
        this.quantity = quantity;
    }

    /** Reconstruction path: rebuilds the snapshot from a persisted sale line. */
    public UnitLine(String productId, String sku, String name, String categoryId,
                    BigDecimal vatRate, BigDecimal unitCost, int quantity, BigDecimal mrp) {
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.categoryId = categoryId;
        this.vatRate = vatRate;
        this.unitCost = unitCost;
        this.mrp = mrp;
        this.quantity = quantity;
    }

    @Override
    public BigDecimal quantity() {
        return BigDecimal.valueOf(quantity);
    }

    @Override
    public BigDecimal unitPrice() {
        return mrp;
    }

    @Override
    public BigDecimal lineNet() {
        return MoneyUtil.round(mrp.multiply(BigDecimal.valueOf(quantity)));
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

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
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

package com.martflow.catalog;

import com.martflow.common.MoneyUtil;

import java.math.BigDecimal;

/**
 * An item sold per piece or pack at a fixed MRP (VAT-inclusive) — a bottle of oil, a soap, a
 * carton of juice. Stock is whole-numbered.
 */
public class UnitProduct extends Product {

    private BigDecimal mrp;

    public UnitProduct(String id, String sku, String barcode, String name, String description,
                       String categoryId, String supplierId, ProductUnit unit,
                       BigDecimal costPrice, BigDecimal mrp, BigDecimal stock, int reorderLevel) {
        super(id, sku, barcode, name, description, categoryId, supplierId, unit, costPrice, stock, reorderLevel);
        this.mrp = MoneyUtil.round(MoneyUtil.requirePositive(mrp, "MRP for " + name));
    }

    @Override
    public String getType() {
        return "UNIT";
    }

    @Override
    public BigDecimal getPrice() {
        return mrp;
    }

    @Override
    protected void applyPrice(BigDecimal newPrice) {
        this.mrp = newPrice;
    }

    public BigDecimal getMrp() {
        return mrp;
    }
}

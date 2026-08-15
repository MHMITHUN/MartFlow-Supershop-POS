package com.martflow.catalog;

import com.martflow.common.MoneyUtil;

import java.math.BigDecimal;

/**
 * An item sold by weight or measure (KG/LITRE) at a price per unit — loose potatoes, fish,
 * cooking oil from the tank. The till weighs the goods and the line total is
 * {@code weight x pricePerUnit}. Stock is fractional at 3dp.
 */
public class WeighedProduct extends Product {

    private BigDecimal pricePerUnit;

    public WeighedProduct(String id, String sku, String barcode, String name, String description,
                          String categoryId, String supplierId, ProductUnit unit,
                          BigDecimal costPrice, BigDecimal pricePerUnit,
                          BigDecimal stock, int reorderLevel) {
        super(id, sku, barcode, name, description, categoryId, supplierId,
                unit == null ? ProductUnit.KG : unit, costPrice, stock, reorderLevel);
        if (!getUnit().isWeighed()) {
            throw new IllegalArgumentException(name + " is weighed — unit must be KG or LITRE, got " + unit);
        }
        this.pricePerUnit = MoneyUtil.round(MoneyUtil.requirePositive(pricePerUnit, "Price per unit for " + name));
    }

    @Override
    public String getType() {
        return "WEIGHED";
    }

    @Override
    public BigDecimal getPrice() {
        return pricePerUnit;
    }

    @Override
    protected void applyPrice(BigDecimal newPrice) {
        this.pricePerUnit = newPrice;
    }

    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }
}

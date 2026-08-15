package com.martflow.catalog;

/**
 * Creates items sold by weight/measure at a price per unit (Factory Method for
 * {@link WeighedProduct}) — loose vegetables, fish, tank oil.
 */
public class WeighedProductFactory extends ProductFactory {

    @Override
    protected Product createProduct(String id, ProductInput in) {
        ProductUnit unit = in.unit() == null ? ProductUnit.KG : in.unit();
        return new WeighedProduct(id, in.sku(), in.barcode(), in.name(), in.description(),
                in.categoryId(), in.supplierId(), unit, in.costPrice(), in.price(),
                in.stock() == null ? java.math.BigDecimal.ZERO : in.stock(), in.reorderLevel());
    }

    @Override
    public String supportedType() {
        return "WEIGHED";
    }
}

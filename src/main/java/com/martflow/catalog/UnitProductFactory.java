package com.martflow.catalog;

/**
 * Creates items sold per piece/pack at a fixed MRP (Factory Method for {@link UnitProduct}).
 */
public class UnitProductFactory extends ProductFactory {

    @Override
    protected Product createProduct(String id, ProductInput in) {
        ProductUnit unit = in.unit() == null ? ProductUnit.PIECE : in.unit();
        if (unit.isWeighed()) {
            throw new IllegalArgumentException(
                    in.name() + " is sold by " + unit + " — create it as a WEIGHED item instead");
        }
        return new UnitProduct(id, in.sku(), in.barcode(), in.name(), in.description(),
                in.categoryId(), in.supplierId(), unit, in.costPrice(), in.price(),
                in.stock() == null ? java.math.BigDecimal.ZERO : in.stock(), in.reorderLevel());
    }

    @Override
    public String supportedType() {
        return "UNIT";
    }
}

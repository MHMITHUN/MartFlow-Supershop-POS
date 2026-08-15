package com.martflow.catalog;

import java.math.BigDecimal;

/**
 * Creation payload for a new inventory item. {@code price} is the MRP for unit items and the
 * price-per-unit-of-measure for weighed items — the concrete {@link ProductFactory} decides.
 */
public record ProductInput(
        String sku,
        String barcode,
        String name,
        String description,
        String categoryId,
        String supplierId,
        ProductUnit unit,
        BigDecimal costPrice,
        BigDecimal price,
        BigDecimal stock,
        int reorderLevel) {
}

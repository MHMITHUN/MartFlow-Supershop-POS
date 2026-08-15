package com.martflow.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.martflow.common.TimeSource;

/**
 * One shrinkage entry: why stock went down outside of sales — damage, loss, theft, or a physical
 * count correction. Supershops track this because wastage directly eats the margin.
 */
public record StockAdjustment(
        String productId,
        Reason reason,
        BigDecimal quantity,
        String note,
        LocalDateTime at) {

    public StockAdjustment {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Adjustment needs a product id");
        }
        if (reason == null) {
            throw new IllegalArgumentException("Adjustment needs a reason");
        }
        if (quantity == null || quantity.signum() == 0) {
            throw new IllegalArgumentException("Adjustment quantity cannot be zero");
        }
        if (at == null) {
            at = TimeSource.now();
        }
    }

    public enum Reason {
        DAMAGE("Damaged goods written off"),
        LOSS("Lost / unaccounted"),
        THEFT("Theft incident"),
        COUNT("Physical count correction");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}

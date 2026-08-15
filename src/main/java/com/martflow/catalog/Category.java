package com.martflow.catalog;

import java.math.BigDecimal;

/**
 * A merchandise category carrying its NBR VAT rate (as a percent: 0, 7.5, 15). MartFlow prices
 * VAT-inclusive, so the bill back-calculates output VAT per line from this rate.
 */
public record Category(String id, String name, BigDecimal vatRatePercent) {

    public Category {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Category id is required");
        }
        if (vatRatePercent == null || vatRatePercent.signum() < 0) {
            throw new IllegalArgumentException("Category VAT rate must be 0 or positive: " + id);
        }
    }
}

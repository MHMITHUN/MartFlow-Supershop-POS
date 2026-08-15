package com.martflow.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One received batch of a product (from a goods receipt note). Perishables carry an expiry date;
 * non-perishables keep {@code expiry == null}. Received quantity is the amount that entered stock
 * in this batch — consumption draws from the aggregate product stock, batches exist for expiry
 * tracking and audit.
 */
public record Batch(String batchNo, LocalDate expiry, BigDecimal receivedQty) {

    public Batch {
        if (batchNo == null || batchNo.isBlank()) {
            throw new IllegalArgumentException("Batch number is required");
        }
        if (receivedQty == null || receivedQty.signum() <= 0) {
            throw new IllegalArgumentException("Batch quantity must be positive");
        }
    }
}

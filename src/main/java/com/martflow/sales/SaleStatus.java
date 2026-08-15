package com.martflow.sales;

/**
 * Lifecycle of a completed sale. A POS sale has no long-lived per-status behaviour — void and
 * return are one-shot operations with their own commands — so unlike purchase orders (which get
 * the full State pattern), this is deliberately a plain enum. Parsing is lenient so a
 * hand-edited or future status string can never 400 an entire sales list.
 */
public enum SaleStatus {

    COMPLETED,
    VOIDED,
    PARTIALLY_RETURNED,
    RETURNED;

    /** Lenient parse: unknown/null values fall back to COMPLETED instead of blowing up. */
    public static SaleStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return COMPLETED;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            return COMPLETED;
        }
    }
}

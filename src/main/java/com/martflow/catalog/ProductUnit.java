package com.martflow.catalog;

/**
 * How an inventory item is sold. PIECE/PACK items stock as whole numbers; KG/LITRE items are
 * weighed/measured at the till and stock fractionally (3dp).
 */
public enum ProductUnit {
    PIECE,
    PACK,
    KG,
    LITRE;

    /** {@code true} for units sold by weight/measure (fractional quantities at the till). */
    public boolean isWeighed() {
        return this == KG || this == LITRE;
    }
}

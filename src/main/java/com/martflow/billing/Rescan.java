package com.martflow.billing;

import com.martflow.billing.item.BillableItem;
import com.martflow.billing.item.ComboLine;
import com.martflow.billing.item.UnitLine;
import com.martflow.billing.item.WeighedLine;

import java.math.BigDecimal;

/**
 * Rebuilds a raw scanned line with a new quantity/weight (the cashier edits a mis-scan instead
 * of removing and rescanning). Bill lines are stored undecorated — promotions are applied at
 * compute time — so the rebuild loses nothing.
 */
final class Rescan {

    private Rescan() {
    }

    static BillableItem of(BillableItem line, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (line instanceof UnitLine u) {
            return new UnitLine(u.productId(), u.sku(), u.name(), u.categoryId(),
                    u.vatRate(), u.unitCost(), quantity.intValueExact(), u.unitPrice());
        }
        if (line instanceof WeighedLine w) {
            return new WeighedLine(w.productId(), w.sku(), w.name(), w.categoryId(),
                    w.vatRate(), w.unitCost(), quantity, w.unitPrice());
        }
        if (line instanceof ComboLine c) {
            return new ComboLine(c.productId(), c.sku(), c.name(), c.categoryId(),
                    c.vatRate(), quantity.intValueExact(), c.unitPrice(), c.unitCost());
        }
        throw new IllegalArgumentException("Cannot change quantity of " + line.describe());
    }
}

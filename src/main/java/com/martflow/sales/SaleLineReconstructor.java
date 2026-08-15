package com.martflow.sales;

import com.martflow.billing.decorator.CarryBagFee;
import com.martflow.billing.decorator.DeliveryFee;
import com.martflow.billing.decorator.LineDiscount;
import com.martflow.billing.decorator.RoundOffAdjustment;
import com.martflow.billing.item.AdjustmentLine;
import com.martflow.billing.item.BillableItem;
import com.martflow.billing.item.ComboLine;
import com.martflow.billing.item.UnitLine;
import com.martflow.billing.item.WeighedLine;

import java.util.List;

/**
 * Rebuilds a live {@link BillableItem} chain from a persisted {@link SaleLine} — the base line
 * from its snapshot fields, then every recorded decoration in order. Visitors (VAT, profit,
 * receipt formatting) therefore behave identically on fresh bills and Mongo-loaded history:
 * reprinting a last-year receipt shows the same numbers it showed on the day.
 */
public final class SaleLineReconstructor {

    private SaleLineReconstructor() {
    }

    public static BillableItem rebuild(SaleLine line) {
        BillableItem base = baseOf(line);
        for (SaleLine.Decoration decoration : line.decorations()) {
            base = decorate(base, decoration);
        }
        return base;
    }

    private static BillableItem baseOf(SaleLine line) {
        return switch (line.kind() == null ? "" : line.kind()) {
            case "WEIGHED" -> new WeighedLine(line.productId(), line.sku(), line.name(),
                    line.categoryId(), line.vatRatePercent(), line.unitCost(),
                    line.quantity(), line.unitPrice());
            case "COMBO" -> new ComboLine(line.productId(), line.sku(), line.name(),
                    line.categoryId(), line.vatRatePercent(),
                    line.quantity().intValueExact(), line.unitPrice(), line.unitCost());
            case "ADJUSTMENT" -> new AdjustmentLine(line.name());
            default -> new UnitLine(line.productId(), line.sku(), line.name(),
                    line.categoryId(), line.vatRatePercent(), line.unitCost(),
                    line.quantity().intValueExact(), line.unitPrice());
        };
    }

    private static BillableItem decorate(BillableItem base, SaleLine.Decoration decoration) {
        String type = decoration.type() == null ? "" : decoration.type();
        return switch (type) {
            case "LINE_DISCOUNT" -> new LineDiscount(base, decoration.param());
            case "CARRY_BAG_FEE" -> new CarryBagFee(base, decoration.param());
            case "DELIVERY_FEE" -> new DeliveryFee(base, decoration.param());
            case "ROUND_OFF" -> new RoundOffAdjustment(base, decoration.param());
            default -> base; // unknown decoration from a future version: degrade gracefully
        };
    }

    /** Convenience: rebuild every line of a sale. */
    public static List<BillableItem> rebuildAll(Sale sale) {
        return sale.getLines().stream().map(SaleLineReconstructor::rebuild).toList();
    }
}

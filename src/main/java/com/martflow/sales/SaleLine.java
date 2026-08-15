package com.martflow.sales;

import java.math.BigDecimal;
import java.util.List;

/**
 * One persisted bill line: a COMPLETE snapshot — kind, identity, quantity, prices, VAT and every
 * decoration — so a sale reloads into exactly what the customer was charged, forever. This is
 * the fix for the storefront bug where Mongo-loaded orders lost their item chains and reported
 * zero VAT on reprints.
 *
 * @param decorations the decorator layers applied at sale time, outermost last
 */
public record SaleLine(
        int lineNo,
        String kind,               // UNIT | WEIGHED | COMBO | ADJUSTMENT
        String productId,
        String sku,
        String name,
        String categoryId,
        BigDecimal vatRatePercent,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal gross,          // quantity x unitPrice, before promotions
        BigDecimal discount,       // promotion savings on this line
        BigDecimal net,            // what the customer actually paid for the line
        BigDecimal vatAmount,      // back-calculated output VAT inside net
        BigDecimal unitCost,       // per-unit cost at sale time (null for adjustments)
        List<Decoration> decorations) {

    /** One decorator layer that was active when the line was sold. */
    public record Decoration(String type, BigDecimal param, BigDecimal amount) {
    }
}

package com.martflow.billing.item;

import com.martflow.billing.visitor.BillItemVisitor;

import java.math.BigDecimal;

/**
 * One line of a bill. Implementations are <b>self-contained snapshots</b>: they capture name,
 * SKU, price, VAT rate and unit cost at scan time, so a price change mid-bill (or mid-year)
 * never rewrites history — and a persisted sale reconstructs into exactly these objects for
 * reprinting and reporting (that is the fix for the storefront's "Mongo orders lose their item
 * chains" bug).
 *
 * <p>Decorator layers (discounts, fees, round-off) wrap any {@code BillableItem} transparently.
 */
public interface BillableItem {

    /** Pieces, or kg/litres for weighed goods. */
    BigDecimal quantity();

    /** Price per piece or per unit-of-measure (for combos: the combo price). */
    BigDecimal unitPrice();

    /** What this line costs the customer after all decorator layers. */
    BigDecimal lineNet();

    /** Receipt text. */
    String describe();

    /** Merchandise name (adjustments carry their label). */
    String name();

    /** SKU ({@code null} for adjustments). */
    String sku();

    /** VAT rate in percent (inclusive prices: output VAT is back-calculated from net). */
    BigDecimal vatRate();

    String categoryId();

    /** {@code null} for pure adjustments (carry bag anchor, round-off). */
    String productId();

    /** Cost price per unit at sale time — feeds the profit report. {@code null} for adjustments. */
    BigDecimal unitCost();

    /** Double-dispatch hook: the item announces its concrete type to the visitor. */
    void accept(BillItemVisitor visitor);
}

package com.martflow.suppliers;

import java.math.BigDecimal;

/**
 * One line of a purchase order: what was ordered, what has physically arrived (goods receipts
 * accumulate here) and the agreed unit cost. The product identity is snapshotted so the PO
 * survives catalog edits.
 */
public class PurchaseOrderLine {

    private final String productId;
    private final String sku;
    private final String name;
    private final BigDecimal orderedQty;
    private BigDecimal receivedQty;
    private final BigDecimal unitCost;

    public PurchaseOrderLine(String productId, String sku, String name,
                             BigDecimal orderedQty, BigDecimal receivedQty, BigDecimal unitCost) {
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.orderedQty = orderedQty;
        this.receivedQty = receivedQty == null ? BigDecimal.ZERO : receivedQty;
        this.unitCost = unitCost;
    }

    public String getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getOrderedQty() {
        return orderedQty;
    }

    public BigDecimal getReceivedQty() {
        return receivedQty;
    }

    /** Goods-receipt accumulation (GRN path only). */
    void addReceived(BigDecimal qty) {
        this.receivedQty = this.receivedQty.add(qty);
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    /** Ordered value of this line (received or not). */
    public BigDecimal lineTotal() {
        return unitCost.multiply(orderedQty);
    }

    /** {@code true} when the ordered quantity has fully arrived. */
    public boolean isFullyReceived() {
        return receivedQty.compareTo(orderedQty) >= 0;
    }
}

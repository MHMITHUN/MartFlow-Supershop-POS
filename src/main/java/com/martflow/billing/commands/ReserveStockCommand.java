package com.martflow.billing.commands;

import com.martflow.inventory.InventoryService;

import java.math.BigDecimal;

/**
 * Consumes stock for one bill line (persisting immediately). Undo restores it — equally
 * persisted, which is the fix for the storefront bug where a failed checkout silently kept the
 * in-memory decrement while the database still had the old stock.
 */
public final class ReserveStockCommand implements BillingCommand {

    private final InventoryService inventory;
    private final String productId;
    private final BigDecimal quantity;

    public ReserveStockCommand(InventoryService inventory, String productId, BigDecimal quantity) {
        this.inventory = inventory;
        this.productId = productId;
        this.quantity = quantity;
    }

    @Override
    public void execute() {
        inventory.consume(productId, quantity);
    }

    @Override
    public void undo() {
        inventory.restore(productId, quantity);
    }
}

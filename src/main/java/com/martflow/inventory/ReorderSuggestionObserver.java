package com.martflow.inventory;

/**
 * <b>Pattern: Observer.</b> Watches inventory items for LOW_STOCK events and turns each one into
 * an actionable reorder suggestion in the alert center ("reorder X — supplier Y, phone Z").
 *
 * <p>In this phase the suggestion names the item and SKU; from the purchasing phase onward it is
 * enriched with the supplier's contact once suppliers are wired in.
 */
public class ReorderSuggestionObserver implements StockObserver {

    private final AlertService alerts;

    public ReorderSuggestionObserver(AlertService alerts) {
        this.alerts = alerts;
    }

    @Override
    public void update(StockEvent event) {
        if (event.getType() != StockEvent.Type.LOW_STOCK) {
            return;
        }
        alerts.raise(StockEvent.custom(StockEvent.Type.LOW_STOCK, event.getProductId(),
                event.getProductName(), event.getOldStock(), event.getNewStock(),
                "Reorder suggestion: restock '" + event.getProductName() + "' (id "
                        + event.getProductId() + ") — check the supplier catalogue"));
    }
}

package com.martflow.inventory;

/**
 * <b>Pattern: Subject</b> of the Observer pattern. Implemented by {@code Product} — inventory
 * items announce restocks, low-stock crossings, price changes and wastage so the alert center and
 * reorder suggester can react without the product knowing about them.
 */
public interface StockSubject {

    void subscribe(StockObserver observer);

    void unsubscribe(StockObserver observer);
}

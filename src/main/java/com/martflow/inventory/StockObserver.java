package com.martflow.inventory;

/**
 * <b>Pattern: Observer.</b> Anything that wants to react to inventory events implements this and
 * subscribes to products ({@code StockSubject}).
 */
public interface StockObserver {

    void update(StockEvent event);
}

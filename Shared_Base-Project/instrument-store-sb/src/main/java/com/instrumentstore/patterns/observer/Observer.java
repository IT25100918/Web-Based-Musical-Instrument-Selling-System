package com.instrumentstore.patterns.observer;

/**
 * Observer pattern (lecture "Observer" - the Observer interface).
 * Anything that wants to be told when a Subject changes implements update().
 * Used by Delivery (DeliveryStatusSubject) and Inventory (StockLevelSubject).
 */
public interface Observer<E> {
    void update(E event);
}

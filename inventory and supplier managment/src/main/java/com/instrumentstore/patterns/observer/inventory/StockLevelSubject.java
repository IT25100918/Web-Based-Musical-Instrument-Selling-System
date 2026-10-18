package com.instrumentstore.patterns.observer.inventory;

import com.instrumentstore.patterns.observer.AbstractSubject;
import com.instrumentstore.patterns.observer.Observer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Observer pattern - ConcreteSubject for Inventory &amp; Supply.
 * Whenever stock is changed by the Inventory manager (manual update, or a purchase order being
 * received) {@link #stockChanged} tells every observer the new level.
 */
@Component
public class StockLevelSubject extends AbstractSubject<StockChangeEvent> {

    public StockLevelSubject(List<Observer<StockChangeEvent>> observers) {
        observers.forEach(this::addObserver);
    }

    public void stockChanged(int productId, String productName, int newQuantity) {
        notifyObservers(new StockChangeEvent(productId, productName, newQuantity));
    }
}

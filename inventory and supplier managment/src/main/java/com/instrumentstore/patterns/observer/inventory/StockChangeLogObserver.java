package com.instrumentstore.patterns.observer.inventory;

import com.instrumentstore.patterns.observer.Observer;
import org.springframework.stereotype.Component;

/** ConcreteObserver: logs every stock change so there is a trace of who-changed-what-to-what. */
@Component
public class StockChangeLogObserver implements Observer<StockChangeEvent> {

    @Override
    public void update(StockChangeEvent event) {
        System.out.println("[stock] " + event.productName() + " (product #" + event.productId()
                + ") stock is now " + event.newQuantity());
    }
}

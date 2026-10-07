package com.instrumentstore.patterns.observer.inventory;

import com.instrumentstore.patterns.observer.Observer;
import org.springframework.stereotype.Component;

/** ConcreteObserver: raises a console warning when a product falls to the low-stock threshold or below. */
@Component
public class LowStockAlertObserver implements Observer<StockChangeEvent> {

    /** Same threshold the inventory page uses to flag low-stock rows. */
    public static final int LOW_STOCK_THRESHOLD = 5;

    @Override
    public void update(StockChangeEvent event) {
        if (event.newQuantity() <= LOW_STOCK_THRESHOLD) {
            System.out.println("[LOW STOCK] " + event.productName() + " (product #" + event.productId()
                    + ") has only " + event.newQuantity() + " left - consider raising a purchase order.");
        }
    }
}

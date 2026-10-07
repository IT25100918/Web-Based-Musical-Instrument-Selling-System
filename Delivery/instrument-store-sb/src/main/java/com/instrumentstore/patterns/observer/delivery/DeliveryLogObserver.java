package com.instrumentstore.patterns.observer.delivery;

import com.instrumentstore.patterns.observer.Observer;
import org.springframework.stereotype.Component;

/** ConcreteObserver: writes one console line per delivery change (a second, independent reaction to the same event). */
@Component
public class DeliveryLogObserver implements Observer<DeliveryEvent> {

    @Override
    public void update(DeliveryEvent event) {
        System.out.println("[delivery] order #" + event.orderId() + " - " + event.subject());
    }
}

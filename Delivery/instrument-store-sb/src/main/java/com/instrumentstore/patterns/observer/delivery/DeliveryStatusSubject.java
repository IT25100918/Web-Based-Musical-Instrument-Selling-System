package com.instrumentstore.patterns.observer.delivery;

import com.instrumentstore.patterns.observer.AbstractSubject;
import com.instrumentstore.patterns.observer.Observer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Observer pattern - ConcreteSubject for the Delivery function.
 * AdminDeliveryController calls {@link #deliveryChanged} after it assigns a delivery or
 * changes its status; every registered observer (customer email, console log, ...) reacts.
 * Spring injects every Observer&lt;DeliveryEvent&gt; bean, so adding a new reaction later means
 * adding one new observer class - this class and the controller do not change.
 */
@Component
public class DeliveryStatusSubject extends AbstractSubject<DeliveryEvent> {

    public DeliveryStatusSubject(List<Observer<DeliveryEvent>> observers) {
        observers.forEach(this::addObserver);
    }

    /** Equivalent of "setMessage" in the lecture's Tsunami example: change state, then notify. */
    public void deliveryChanged(int orderId, String subject, String body) {
        notifyObservers(new DeliveryEvent(orderId, subject, body));
    }
}

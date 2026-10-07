package com.instrumentstore.patterns.observer.delivery;

/** What changed for a delivery: which order, and the message to tell people about it. */
public record DeliveryEvent(int orderId, String subject, String body) {
}

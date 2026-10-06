package com.instrumentstore.service.payment;

import java.math.BigDecimal;

/**
 * Strategy interface for one payment method. Each implementation knows how
 * to "process" a payment for its own method (in this student project, that
 * always means simulating success and generating a fake transaction
 * reference - there's no real payment gateway wired in) and what method
 * name it's registered under in {@link PaymentProcessorFactory}.
 */
public interface PaymentProcessor {

    /**
     * Simulates processing a payment of {@code amount} for {@code orderId}
     * and returns a transaction reference to store alongside the payment
     * record. A real implementation would call an external payment
     * provider's API here instead.
     */
    String process(BigDecimal amount, int orderId);

    /** The payment_method value this processor handles (CARD, COD, BANK_TRANSFER - matches the DB CHECK constraint). */
    String getMethodName();
}

package com.instrumentstore.service.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulated card payment processor. No real card gateway is wired in - this
 * always "succeeds" and generates a fake transaction reference.
 */
@Component
public class CardPaymentProcessor implements PaymentProcessor {

    @Override
    public String process(BigDecimal amount, int orderId) {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public String getMethodName() {
        return "CARD";
    }
}

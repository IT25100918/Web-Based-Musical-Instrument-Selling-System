package com.instrumentstore.service.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cash-on-delivery "payment" processor. There's nothing to charge yet (the
 * customer pays the delivery person on arrival) - this just records a
 * reference so the order has a payment row and moves to PROCESSING like
 * any other method. No real gateway involved, same as the other processors.
 */
@Component
public class CodPaymentProcessor implements PaymentProcessor {

    @Override
    public String process(BigDecimal amount, int orderId) {
        return "COD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public String getMethodName() {
        return "COD";
    }
}

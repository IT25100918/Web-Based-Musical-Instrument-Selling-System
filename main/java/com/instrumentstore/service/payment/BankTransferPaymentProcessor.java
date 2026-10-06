package com.instrumentstore.service.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulated bank transfer payment processor. No real bank integration is
 * wired in - this always "succeeds" and generates a fake reference, as if
 * the customer had transferred the funds and it had already cleared.
 */
@Component
public class BankTransferPaymentProcessor implements PaymentProcessor {

    @Override
    public String process(BigDecimal amount, int orderId) {
        return "BANK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public String getMethodName() {
        return "BANK_TRANSFER";
    }
}

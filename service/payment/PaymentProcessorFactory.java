package com.instrumentstore.service.payment;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory pattern: Spring constructor-injects every {@link PaymentProcessor}
 * bean as a List (it finds CardPaymentProcessor, CodPaymentProcessor and
 * BankTransferPaymentProcessor automatically via @Component - nothing else
 * to register), and this class turns that list into a lookup map keyed by
 * each processor's {@link PaymentProcessor#getMethodName()}.
 *
 * Adding a new payment method later means only adding one new
 * PaymentProcessor implementation - this factory and its callers don't change.
 */
@Component
public class PaymentProcessorFactory {

    private final Map<String, PaymentProcessor> processorsByMethod;

    public PaymentProcessorFactory(List<PaymentProcessor> processors) {
        this.processorsByMethod = processors.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentProcessor::getMethodName, p -> p));
    }

    /**
     * @throws IllegalArgumentException if no processor is registered for this method name
     *         (e.g. an unknown/tampered value from the payment form)
     */
    public PaymentProcessor getProcessor(String methodName) {
        PaymentProcessor processor = processorsByMethod.get(methodName);
        if (processor == null) {
            throw new IllegalArgumentException("Unsupported payment method: " + methodName);
        }
        return processor;
    }
}

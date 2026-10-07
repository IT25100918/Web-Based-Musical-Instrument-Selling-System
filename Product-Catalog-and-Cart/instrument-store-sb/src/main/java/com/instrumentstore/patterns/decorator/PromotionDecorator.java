package com.instrumentstore.patterns.decorator;

import java.math.BigDecimal;

/**
 * Decorator pattern - ConcreteDecorator: replaces the wrapped price with the promotional price
 * (the same price the cart already charges - see CartDAO), and adds ", Promotion" to the description.
 * More decorators (e.g. a loyalty discount) could be wrapped around this one without changing it.
 */
public class PromotionDecorator extends PriceDecorator {

    private final BigDecimal promoPrice;

    public PromotionDecorator(PriceComponent wrapped, BigDecimal promoPrice) {
        super(wrapped);
        this.promoPrice = promoPrice;
    }

    @Override
    public String getDescription() {
        return wrapped.getDescription() + ", Promotion";
    }

    @Override
    public BigDecimal getAmount() {
        return promoPrice;
    }

    /** How much the customer saves compared with the wrapped (undecorated) price. */
    public BigDecimal getSavings() {
        return wrapped.getAmount().subtract(promoPrice);
    }
}

package com.instrumentstore.patterns.decorator;

import java.math.BigDecimal;

/** Decorator pattern - abstract Decorator: wraps another PriceComponent and delegates to it by default. */
public abstract class PriceDecorator implements PriceComponent {

    protected final PriceComponent wrapped;

    protected PriceDecorator(PriceComponent wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public String getDescription() {
        return wrapped.getDescription();
    }

    @Override
    public BigDecimal getAmount() {
        return wrapped.getAmount();
    }
}

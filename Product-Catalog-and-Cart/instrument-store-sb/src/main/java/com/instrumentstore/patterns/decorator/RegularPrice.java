package com.instrumentstore.patterns.decorator;

import java.math.BigDecimal;

/** Decorator pattern - ConcreteComponent: the product's normal list price (like SimpleCoffee). */
public class RegularPrice implements PriceComponent {

    private final BigDecimal price;

    public RegularPrice(BigDecimal price) {
        this.price = price;
    }

    @Override
    public String getDescription() {
        return "Regular price";
    }

    @Override
    public BigDecimal getAmount() {
        return price;
    }
}

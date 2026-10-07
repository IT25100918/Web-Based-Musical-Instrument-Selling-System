package com.instrumentstore.patterns.decorator;

import java.math.BigDecimal;

/** Decorator pattern - Component: something that has a description and an amount (like Coffee in the lecture). */
public interface PriceComponent {
    String getDescription();

    BigDecimal getAmount();
}

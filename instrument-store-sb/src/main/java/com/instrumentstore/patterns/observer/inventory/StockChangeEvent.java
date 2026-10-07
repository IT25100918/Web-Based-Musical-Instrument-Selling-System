package com.instrumentstore.patterns.observer.inventory;

/** A product's stock was changed (manual correction or purchase order received). */
public record StockChangeEvent(int productId, String productName, int newQuantity) {
}

package com.instrumentstore.service;

import java.util.Map;
import java.util.Set;

/**
 * The allowed status changes for orders and deliveries, in one place.
 * Before this, an admin could set an unpaid PENDING order straight to DELIVERED, or move a
 * DELIVERED order back to PENDING. Anything not listed here is rejected.
 */
public final class StatusRules {

    private StatusRules() {
    }

    // PENDING -> PROCESSING is NOT listed on purpose: an order only becomes PROCESSING when the
    // customer pays (OrderService.payOrder), never by hand.
    // DELIVERED -> CANCELLED is kept so a returned/refunded order can still put its stock back.
    private static final Map<String, Set<String>> ORDER = Map.of(
            "PENDING", Set.of("CANCELLED"),
            "PROCESSING", Set.of("SHIPPED", "CANCELLED"),
            "SHIPPED", Set.of("DELIVERED", "CANCELLED"),
            "DELIVERED", Set.of("CANCELLED"),
            "CANCELLED", Set.of());

    // A failed delivery can be re-attempted (FAILED -> ASSIGNED); DELIVERED is final.
    private static final Map<String, Set<String>> DELIVERY = Map.of(
            "ASSIGNED", Set.of("IN_TRANSIT", "FAILED"),
            "IN_TRANSIT", Set.of("DELIVERED", "FAILED"),
            "FAILED", Set.of("ASSIGNED"),
            "DELIVERED", Set.of());

    public static boolean canChangeOrder(String from, String to) {
        return ORDER.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean canChangeDelivery(String from, String to) {
        return DELIVERY.getOrDefault(from, Set.of()).contains(to);
    }
}

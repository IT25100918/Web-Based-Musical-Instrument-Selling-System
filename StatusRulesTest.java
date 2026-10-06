package com.instrumentstore.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusRulesTest {

    @Test
    void unpaidOrderCannotBeShippedOrDelivered() {
        assertFalse(StatusRules.canChangeOrder("PENDING", "SHIPPED"));
        assertFalse(StatusRules.canChangeOrder("PENDING", "DELIVERED"));
        assertFalse(StatusRules.canChangeOrder("PENDING", "PROCESSING")); // only a payment does that
    }

    @Test
    void orderMovesForwardOnly() {
        assertTrue(StatusRules.canChangeOrder("PROCESSING", "SHIPPED"));
        assertTrue(StatusRules.canChangeOrder("SHIPPED", "DELIVERED"));
        assertFalse(StatusRules.canChangeOrder("DELIVERED", "PENDING"));
        assertFalse(StatusRules.canChangeOrder("SHIPPED", "PROCESSING"));
    }

    @Test
    void anyLiveOrderCanBeCancelledButCancelledIsFinal() {
        for (String from : new String[]{"PENDING", "PROCESSING", "SHIPPED", "DELIVERED"}) {
            assertTrue(StatusRules.canChangeOrder(from, "CANCELLED"), from);
        }
        assertFalse(StatusRules.canChangeOrder("CANCELLED", "PROCESSING"));
        assertFalse(StatusRules.canChangeOrder("CANCELLED", "PENDING"));
    }

    @Test
    void deliveryRules() {
        assertTrue(StatusRules.canChangeDelivery("ASSIGNED", "IN_TRANSIT"));
        assertTrue(StatusRules.canChangeDelivery("IN_TRANSIT", "DELIVERED"));
        assertTrue(StatusRules.canChangeDelivery("FAILED", "ASSIGNED"));
        assertFalse(StatusRules.canChangeDelivery("DELIVERED", "ASSIGNED"));
        assertFalse(StatusRules.canChangeDelivery("ASSIGNED", "DELIVERED"));
    }

    @Test
    void unknownStatusIsRejected() {
        assertFalse(StatusRules.canChangeOrder("NOPE", "CANCELLED"));
        assertFalse(StatusRules.canChangeDelivery("ASSIGNED", "NOPE"));
    }
}

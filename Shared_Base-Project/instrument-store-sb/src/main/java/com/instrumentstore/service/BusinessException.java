package com.instrumentstore.service;

/**
 * Thrown by any service for an expected, user-facing problem (empty cart, not enough stock,
 * order not payable, purchase order already received...). Lives in its own class so that
 * OrderService and InventoryService don't depend on each other. Being a RuntimeException,
 * it also rolls back a @Transactional method.
 */
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }
}

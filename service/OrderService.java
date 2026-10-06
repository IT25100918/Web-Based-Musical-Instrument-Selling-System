package com.instrumentstore.service;

import com.instrumentstore.dao.CartDAO;
import com.instrumentstore.dao.OrderDAO;
import com.instrumentstore.dao.PaymentDAO;
import com.instrumentstore.dao.ProductDAO;
import com.instrumentstore.model.CartItem;
import com.instrumentstore.model.Order;
import com.instrumentstore.model.OrderItem;
import com.instrumentstore.model.Payment;
import com.instrumentstore.service.payment.PaymentProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Multi-step order operations that must succeed or fail as ONE unit.
 *
 * Previously the controllers ran these steps one after another with no transaction, so a failure
 * half-way (or two customers buying the last unit at the same moment) left the database
 * inconsistent: orders without stock taken, stock taken with the cart never cleared, cancelled
 * orders that never gave their stock back, and so on. Any RuntimeException thrown from a
 * @Transactional method here rolls every step back.
 */
@Service
public class OrderService {

    private final CartDAO cartDAO;
    private final OrderDAO orderDAO;
    private final ProductDAO productDAO;
    private final PaymentDAO paymentDAO;

    public OrderService(CartDAO cartDAO, OrderDAO orderDAO, ProductDAO productDAO, PaymentDAO paymentDAO) {
        this.cartDAO = cartDAO;
        this.orderDAO = orderDAO;
        this.productDAO = productDAO;
        this.paymentDAO = paymentDAO;
    }

    /** Turns the user's cart into a PENDING order, taking the stock, and empties the cart. Returns the order id. */
    @Transactional
    public int checkout(int userId) {
        List<CartItem> items = cartDAO.getCartItems(userId);
        if (items.isEmpty()) {
            throw new BusinessException("Your cart is empty.");
        }

        // Atomic "take stock only if enough is left" - safe even if two customers check out at once.
        for (CartItem item : items) {
            if (item.getQuantity() <= 0 || !productDAO.decrementStock(item.getProductId(), item.getQuantity())) {
                throw new BusinessException("Not enough stock for " + item.getProductName() + ". Please update your cart.");
            }
        }

        int orderId = orderDAO.placeOrder(userId, items);
        cartDAO.clearCart(userId);
        return orderId;
    }

    /**
     * Pays for an order in ONE transaction: PENDING -> PROCESSING first (so an order can't be paid
     * twice or after being cancelled), then the processor "charges" the customer, then the payment
     * record is saved. If the processor throws (a real gateway declining or timing out), the status
     * change is rolled back, so the customer is never left with a PROCESSING order and no payment.
     *
     * @return the transaction reference returned by the processor
     */
    @Transactional
    public String payOrder(int orderId, BigDecimal amount, PaymentProcessor processor) {
        if (!orderDAO.transitionStatus(orderId, "PENDING", "PROCESSING")) {
            throw new BusinessException("This order can no longer be paid.");
        }
        String transactionRef = processor.process(amount, orderId);
        paymentDAO.recordPayment(orderId, amount, processor.getMethodName(), "SUCCESS", transactionRef);
        return transactionRef;
    }

    /**
     * Cancels an order, returns its items to stock, and refunds any successful payment.
     *
     * @param onlyIfPending true for a customer cancelling their own order (only allowed before payment)
     * @return false if the order wasn't in a cancellable state (nothing was changed)
     */
    @Transactional
    public boolean cancelOrder(int orderId, boolean onlyIfPending) {
        boolean changed = onlyIfPending
                ? orderDAO.transitionStatus(orderId, "PENDING", "CANCELLED")
                : orderDAO.cancelIfNotCancelled(orderId);
        if (!changed) {
            return false;
        }
        for (OrderItem item : orderDAO.getOrderItems(orderId)) {
            productDAO.increaseStock(item.getProductId(), item.getQuantity());
        }
        paymentDAO.refundSuccessfulForOrder(orderId);
        return true;
    }

    /**
     * Refunds one payment from the admin payments page. A successful payment is refunded THROUGH
     * the order: the order is cancelled, its stock goes back and every successful payment on it
     * becomes REFUNDED - so the payment, the order and the stock can't disagree.
     */
    @Transactional
    public void refundPayment(int paymentId) {
        Payment payment = paymentDAO.getById(paymentId);
        if (payment == null) {
            throw new BusinessException("Payment not found.");
        }
        if (!"SUCCESS".equals(payment.getPaymentStatus())) {
            throw new BusinessException("Only a successful payment can be refunded.");
        }
        Order order = orderDAO.getOrderById(payment.getOrderId());
        if (order != null && !"CANCELLED".equals(order.getStatus())) {
            cancelOrder(order.getOrderId(), false);
        } else {
            paymentDAO.refund(paymentId); // order already cancelled: only the payment record is left to fix
        }
    }

    /**
     * Manual payment status change (FAILED / PENDING / SUCCESS) from the admin payments page.
     * Refunds go through {@link #refundPayment}. Rules keep payments and orders consistent.
     */
    @Transactional
    public void changePaymentStatus(int paymentId, String newStatus) {
        Payment payment = paymentDAO.getById(paymentId);
        if (payment == null) {
            throw new BusinessException("Payment not found.");
        }
        String current = payment.getPaymentStatus();
        if (current.equals(newStatus)) {
            return;
        }
        if ("REFUNDED".equals(newStatus)) {
            refundPayment(paymentId);
            return;
        }
        if ("REFUNDED".equals(current)) {
            throw new BusinessException("A refunded payment can't be changed.");
        }
        if ("SUCCESS".equals(current)) {
            throw new BusinessException("A successful payment can only be refunded, not changed to " + newStatus + ".");
        }
        // current is PENDING or FAILED from here on
        if ("SUCCESS".equals(newStatus)) {
            Order order = orderDAO.getOrderById(payment.getOrderId());
            if (order == null || "CANCELLED".equals(order.getStatus())) {
                throw new BusinessException("This payment belongs to a cancelled order and can't be marked successful.");
            }
            // An unpaid order becomes PROCESSING as soon as its payment is confirmed
            orderDAO.transitionStatus(order.getOrderId(), "PENDING", "PROCESSING");
        }
        paymentDAO.updateStatus(paymentId, newStatus);
    }

    /** Deletes an invalid payment record. Successful and refunded payments are financial records and stay. */
    @Transactional
    public void deleteInvalidPayment(int paymentId) {
        Payment payment = paymentDAO.getById(paymentId);
        if (payment == null) {
            throw new BusinessException("Payment not found.");
        }
        if ("SUCCESS".equals(payment.getPaymentStatus()) || "REFUNDED".equals(payment.getPaymentStatus())) {
            throw new BusinessException("Successful and refunded payments are financial records and can't be deleted.");
        }
        paymentDAO.deletePayment(paymentId);
    }
}

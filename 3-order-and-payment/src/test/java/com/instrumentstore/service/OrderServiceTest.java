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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OrderService with the DAOs mocked. (The @Transactional rollback itself is Spring's job and
 * is not exercised here - these tests check the decisions the service makes and the calls it does / doesn't make.)
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock CartDAO cartDAO;
    @Mock OrderDAO orderDAO;
    @Mock ProductDAO productDAO;
    @Mock PaymentDAO paymentDAO;
    @Mock PaymentProcessor processor;

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(cartDAO, orderDAO, productDAO, paymentDAO);
    }

    private CartItem cartItem(int productId, int quantity) {
        CartItem item = new CartItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setProductName("Guitar");
        return item;
    }

    private Order order(int id, String status) {
        Order o = new Order();
        o.setOrderId(id);
        o.setStatus(status);
        return o;
    }

    private Payment payment(int id, int orderId, String status) {
        Payment p = new Payment();
        p.setPaymentId(id);
        p.setOrderId(orderId);
        p.setPaymentStatus(status);
        return p;
    }

    // ---------------- checkout ----------------

    @Test
    void checkoutWithEmptyCartIsRejected() {
        when(cartDAO.getCartItems(1)).thenReturn(List.of());
        BusinessException e = assertThrows(BusinessException.class, () -> service.checkout(1));
        assertEquals("Your cart is empty.", e.getMessage());
        verify(orderDAO, never()).placeOrder(anyInt(), anyList());
    }

    @Test
    void checkoutFailsWhenStockRunsOutAndNoOrderIsCreated() {
        when(cartDAO.getCartItems(1)).thenReturn(List.of(cartItem(7, 2)));
        when(productDAO.decrementStock(7, 2)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.checkout(1));
        verify(orderDAO, never()).placeOrder(anyInt(), anyList());
        verify(cartDAO, never()).clearCart(anyInt());
    }

    @Test
    void checkoutTakesStockCreatesOrderAndClearsCart() {
        List<CartItem> items = List.of(cartItem(7, 2));
        when(cartDAO.getCartItems(1)).thenReturn(items);
        when(productDAO.decrementStock(7, 2)).thenReturn(true);
        when(orderDAO.placeOrder(1, items)).thenReturn(42);
        assertEquals(42, service.checkout(1));
        verify(cartDAO).clearCart(1);
    }

    // ---------------- paying ----------------

    @Test
    void payingAnOrderThatIsNotPendingIsRejectedAndRecordsNothing() {
        when(orderDAO.transitionStatus(5, "PENDING", "PROCESSING")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.payOrder(5, new BigDecimal("10.00"), processor));
        verify(processor, never()).process(any(), anyInt());
        verify(paymentDAO, never()).recordPayment(anyInt(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void payingRecordsASuccessfulPaymentWithTheProcessorReference() {
        BigDecimal amount = new BigDecimal("10.00");
        when(orderDAO.transitionStatus(5, "PENDING", "PROCESSING")).thenReturn(true);
        when(processor.process(amount, 5)).thenReturn("TXN-1");
        when(processor.getMethodName()).thenReturn("CARD");
        assertEquals("TXN-1", service.payOrder(5, amount, processor));
        verify(paymentDAO).recordPayment(5, amount, "CARD", "SUCCESS", "TXN-1");
    }

    @Test
    void ifTheProcessorFailsNoPaymentIsRecorded() {
        BigDecimal amount = new BigDecimal("10.00");
        when(orderDAO.transitionStatus(5, "PENDING", "PROCESSING")).thenReturn(true);
        when(processor.process(amount, 5)).thenThrow(new IllegalStateException("gateway down"));
        assertThrows(IllegalStateException.class, () -> service.payOrder(5, amount, processor));
        verify(paymentDAO, never()).recordPayment(anyInt(), any(), anyString(), anyString(), anyString());
    }

    // ---------------- cancelling ----------------

    @Test
    void cancellingPutsStockBackAndRefundsPayments() {
        OrderItem item = new OrderItem();
        item.setProductId(7);
        item.setQuantity(3);
        when(orderDAO.cancelIfNotCancelled(5)).thenReturn(true);
        when(orderDAO.getOrderItems(5)).thenReturn(List.of(item));
        assertEquals(true, service.cancelOrder(5, false));
        verify(productDAO).increaseStock(7, 3);
        verify(paymentDAO).refundSuccessfulForOrder(5);
    }

    @Test
    void cancellingAnAlreadyCancelledOrderDoesNotRestockTwice() {
        when(orderDAO.cancelIfNotCancelled(5)).thenReturn(false);
        assertEquals(false, service.cancelOrder(5, false));
        verify(productDAO, never()).increaseStock(anyInt(), anyInt());
        verify(paymentDAO, never()).refundSuccessfulForOrder(anyInt());
    }

    @Test
    void customerCanOnlyCancelWhilePending() {
        when(orderDAO.transitionStatus(5, "PENDING", "CANCELLED")).thenReturn(false);
        assertEquals(false, service.cancelOrder(5, true));
        verify(productDAO, never()).increaseStock(anyInt(), anyInt());
    }

    // ---------------- admin payment actions ----------------

    @Test
    void refundingASuccessfulPaymentCancelsTheOrderSoStockComesBack() {
        when(paymentDAO.getById(9)).thenReturn(payment(9, 5, "SUCCESS"));
        when(orderDAO.getOrderById(5)).thenReturn(order(5, "PROCESSING"));
        when(orderDAO.cancelIfNotCancelled(5)).thenReturn(true);
        when(orderDAO.getOrderItems(5)).thenReturn(List.of());
        service.refundPayment(9);
        verify(orderDAO).cancelIfNotCancelled(5);
        verify(paymentDAO).refundSuccessfulForOrder(5);
    }

    @Test
    void onlyASuccessfulPaymentCanBeRefunded() {
        when(paymentDAO.getById(9)).thenReturn(payment(9, 5, "FAILED"));
        assertThrows(BusinessException.class, () -> service.refundPayment(9));
        verify(orderDAO, never()).cancelIfNotCancelled(anyInt());
    }

    @Test
    void aSuccessfulPaymentCannotBeSwitchedToFailed() {
        when(paymentDAO.getById(9)).thenReturn(payment(9, 5, "SUCCESS"));
        assertThrows(BusinessException.class, () -> service.changePaymentStatus(9, "FAILED"));
        verify(paymentDAO, never()).updateStatus(anyInt(), anyString());
    }

    @Test
    void aPaymentOfACancelledOrderCannotBeMarkedSuccessful() {
        when(paymentDAO.getById(9)).thenReturn(payment(9, 5, "FAILED"));
        when(orderDAO.getOrderById(5)).thenReturn(order(5, "CANCELLED"));
        assertThrows(BusinessException.class, () -> service.changePaymentStatus(9, "SUCCESS"));
        verify(paymentDAO, never()).updateStatus(anyInt(), anyString());
    }

    @Test
    void confirmingAPendingPaymentMovesAPendingOrderToProcessing() {
        when(paymentDAO.getById(9)).thenReturn(payment(9, 5, "PENDING"));
        when(orderDAO.getOrderById(5)).thenReturn(order(5, "PENDING"));
        service.changePaymentStatus(9, "SUCCESS");
        verify(orderDAO).transitionStatus(5, "PENDING", "PROCESSING");
        verify(paymentDAO).updateStatus(9, "SUCCESS");
    }

    @Test
    void successfulAndRefundedPaymentsCannotBeDeleted() {
        when(paymentDAO.getById(1)).thenReturn(payment(1, 5, "SUCCESS"));
        when(paymentDAO.getById(2)).thenReturn(payment(2, 5, "REFUNDED"));
        assertThrows(BusinessException.class, () -> service.deleteInvalidPayment(1));
        assertThrows(BusinessException.class, () -> service.deleteInvalidPayment(2));
        verify(paymentDAO, never()).deletePayment(anyInt());
    }

    @Test
    void aFailedPaymentCanBeDeleted() {
        when(paymentDAO.getById(3)).thenReturn(payment(3, 5, "FAILED"));
        service.deleteInvalidPayment(3);
        verify(paymentDAO).deletePayment(3);
    }
}

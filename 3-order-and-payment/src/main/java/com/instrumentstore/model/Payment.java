package com.instrumentstore.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class Payment {
    private int paymentId;
    private int orderId;
    private BigDecimal amount;
    private String paymentMethod;  // CARD, COD, BANK_TRANSFER
    private String paymentStatus;  // PENDING, SUCCESS, FAILED, REFUNDED
    private String transactionRef;
    private Timestamp paidAt;
    private BigDecimal orderTotal; // joined in for reconciliation (PBI-25); not a real column

    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public Timestamp getPaidAt() { return paidAt; }
    public void setPaidAt(Timestamp paidAt) { this.paidAt = paidAt; }

    public BigDecimal getOrderTotal() { return orderTotal; }
    public void setOrderTotal(BigDecimal orderTotal) { this.orderTotal = orderTotal; }

    // Reconciliation check (PBI-25): does the recorded payment amount match
    // the order it's supposed to be paying for?
    public boolean isReconciled() {
        return orderTotal == null || amount == null || amount.compareTo(orderTotal) == 0;
    }
}

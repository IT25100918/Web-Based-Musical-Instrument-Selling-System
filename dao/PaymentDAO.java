package com.instrumentstore.dao;

import com.instrumentstore.model.Payment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class PaymentDAO {

    private final JdbcTemplate jdbcTemplate;

    public PaymentDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Payment> paymentRowMapper = (rs, rowNum) -> {
        Payment p = new Payment();
        p.setPaymentId(rs.getInt("payment_id"));
        p.setOrderId(rs.getInt("order_id"));
        p.setAmount(rs.getBigDecimal("amount"));
        p.setPaymentMethod(rs.getString("payment_method"));
        p.setPaymentStatus(rs.getString("payment_status"));
        p.setTransactionRef(rs.getString("transaction_ref"));
        p.setPaidAt(rs.getTimestamp("paid_at"));
        return p;
    };

    // CREATE: records a new payment attempt/result for an order
    public boolean recordPayment(int orderId, BigDecimal amount, String method, String status, String transactionRef) {
        String sql = "INSERT INTO payments (order_id, amount, payment_method, payment_status, transaction_ref) VALUES (?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql, orderId, amount, method, status, transactionRef) > 0;
    }

    // READ: payment for a specific order
    public Payment getByOrderId(int orderId) {
        String sql = "SELECT TOP 1 * FROM payments WHERE order_id = ? ORDER BY paid_at DESC";
        List<Payment> results = jdbcTemplate.query(sql, paymentRowMapper, orderId);
        return results.isEmpty() ? null : results.get(0);
    }

    // READ: all payments (admin)
    public List<Payment> getAllPayments() {
        String sql = "SELECT * FROM payments ORDER BY paid_at DESC";
        return jdbcTemplate.query(sql, paymentRowMapper);
    }

    // READ: all payments joined with their order's total, for reconciliation
    // against the gateway/order records (PBI-25) - flags amount mismatches.
    public List<Payment> getAllPaymentsWithReconciliation() {
        String sql = "SELECT p.*, o.total_amount AS order_total FROM payments p " +
                "JOIN orders o ON p.order_id = o.order_id ORDER BY p.paid_at DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Payment payment = paymentRowMapper.mapRow(rs, rowNum);
            payment.setOrderTotal(rs.getBigDecimal("order_total"));
            return payment;
        });
    }

    // READ: a customer's payment history, joined via their orders
    public List<Payment> getPaymentHistoryByUser(int userId) {
        String sql = "SELECT pay.* FROM payments pay JOIN orders o ON pay.order_id = o.order_id " +
                "WHERE o.user_id = ? ORDER BY pay.paid_at DESC";
        return jdbcTemplate.query(sql, paymentRowMapper, userId);
    }

    // UPDATE: process a refund
    public boolean refund(int paymentId) {
        String sql = "UPDATE payments SET payment_status = 'REFUNDED' WHERE payment_id = ?";
        return jdbcTemplate.update(sql, paymentId) > 0;
    }

    // UPDATE: when an order is cancelled after being paid, its successful payment(s) become REFUNDED
    public int refundSuccessfulForOrder(int orderId) {
        String sql = "UPDATE payments SET payment_status = 'REFUNDED' WHERE order_id = ? AND payment_status = 'SUCCESS'";
        return jdbcTemplate.update(sql, orderId);
    }

    // UPDATE: generic status change (e.g. marking a payment FAILED after review)
    public boolean updateStatus(int paymentId, String status) {
        String sql = "UPDATE payments SET payment_status = ? WHERE payment_id = ?";
        return jdbcTemplate.update(sql, status, paymentId) > 0;
    }

    // DELETE: remove an invalid payment record (admin only, per spec)
    public boolean deletePayment(int paymentId) {
        String sql = "DELETE FROM payments WHERE payment_id = ?";
        return jdbcTemplate.update(sql, paymentId) > 0;
    }

    // Financial statement (PBI-28): total amount for a given payment status (e.g. total revenue
    // from SUCCESS payments, or total refunded from REFUNDED payments)
    public BigDecimal getTotalByStatus(String status) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE payment_status = ?";
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, status);
    }
}

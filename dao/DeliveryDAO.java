package com.instrumentstore.dao;

import com.instrumentstore.model.Delivery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.Map;

@Repository
public class DeliveryDAO {

    private final JdbcTemplate jdbcTemplate;

    public DeliveryDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Delivery> deliveryRowMapper = (rs, rowNum) -> {
        Delivery d = new Delivery();
        d.setDeliveryId(rs.getInt("delivery_id"));
        d.setOrderId(rs.getInt("order_id"));
        d.setDeliveryAddress(rs.getString("delivery_address"));
        d.setScheduledDate(rs.getDate("scheduled_date"));
        d.setStatus(rs.getString("status"));
        d.setNotes(rs.getString("notes"));
        d.setAddressVerified(rs.getBoolean("address_verified"));
        d.setDeliveryPerson(rs.getString("delivery_person"));
        return d;
    };

    // Basic automatic sanity check: does the address look plausible?
    // (has a street number, isn't suspiciously short). This is NOT a real
    // address-validation API (e.g. Google Maps) - just enough of a check to
    // flag obviously-wrong entries for a human to double-check.
    private boolean looksLikeValidAddress(String address) {
        if (address == null || address.trim().length() < 8) return false;
        return address.matches(".*\\d.*"); // contains at least one digit (house/street number)
    }

    // CREATE: assign a delivery to an order. Runs the automatic address sanity check on creation.
    public boolean createDelivery(int orderId, String address, Date scheduledDate) {
        return createDelivery(orderId, address, scheduledDate, null);
    }

    // CREATE: assign a delivery to an order, optionally naming the delivery
    // person who will handle it so each order goes to the right person (PBI-14).
    public boolean createDelivery(int orderId, String address, Date scheduledDate, String deliveryPerson) {
        boolean autoVerified = looksLikeValidAddress(address);
        String sql = "INSERT INTO deliveries (order_id, delivery_address, scheduled_date, status, address_verified, delivery_person) VALUES (?, ?, ?, 'ASSIGNED', ?, ?)";
        return jdbcTemplate.update(sql, orderId, address, scheduledDate, autoVerified, deliveryPerson) > 0;
    }

    // READ: all deliveries (admin)
    public List<Delivery> getAllDeliveries() {
        String sql = "SELECT * FROM deliveries ORDER BY scheduled_date DESC";
        return jdbcTemplate.query(sql, deliveryRowMapper);
    }

    // READ: one delivery by its own id
    public Delivery getById(int deliveryId) {
        String sql = "SELECT * FROM deliveries WHERE delivery_id = ?";
        List<Delivery> results = jdbcTemplate.query(sql, deliveryRowMapper, deliveryId);
        return results.isEmpty() ? null : results.get(0);
    }

    // READ: delivery for a specific order (used for customer tracking on the order page)
    public Delivery getByOrderId(int orderId) {
        String sql = "SELECT * FROM deliveries WHERE order_id = ?";
        List<Delivery> results = jdbcTemplate.query(sql, deliveryRowMapper, orderId);
        return results.isEmpty() ? null : results.get(0);
    }

    // Orders that have been paid for but don't have a delivery assigned yet,
    // with the customer's saved address pulled in for convenience.
    public List<Map<String, Object>> getOrdersAwaitingDelivery() {
        String sql = "SELECT o.order_id, o.status AS order_status, u.address AS user_address " +
                "FROM orders o " +
                "JOIN users u ON o.user_id = u.user_id " +
                "LEFT JOIN deliveries d ON o.order_id = d.order_id " +
                "WHERE d.delivery_id IS NULL AND o.status IN ('PROCESSING','SHIPPED') " +
                "ORDER BY o.order_date DESC";
        return jdbcTemplate.queryForList(sql);
    }

    // UPDATE: track delivery status (assign -> in transit -> delivered, or failed)
    public boolean updateStatus(int deliveryId, String status, String notes) {
        String sql = "UPDATE deliveries SET status = ?, notes = ? WHERE delivery_id = ?";
        return jdbcTemplate.update(sql, status, notes, deliveryId) > 0;
    }

    // UPDATE: reschedule a delivery (Update Delivery Schedule sub-function)
    public boolean updateStatusAndSchedule(int deliveryId, String status, String notes, Date scheduledDate) {
        String sql = "UPDATE deliveries SET status = ?, notes = ?, scheduled_date = ? WHERE delivery_id = ?";
        return jdbcTemplate.update(sql, status, notes, scheduledDate, deliveryId) > 0;
    }

    // UPDATE: assign or reassign the delivery person handling this order (PBI-14)
    public boolean assignDeliveryPerson(int deliveryId, String deliveryPerson) {
        String sql = "UPDATE deliveries SET delivery_person = ? WHERE delivery_id = ?";
        return jdbcTemplate.update(sql, deliveryPerson, deliveryId) > 0;
    }

    // UPDATE: admin manually confirms an address is correct (Verify Addresses sub-function)
    public boolean setAddressVerified(int deliveryId, boolean verified) {
        String sql = "UPDATE deliveries SET address_verified = ? WHERE delivery_id = ?";
        return jdbcTemplate.update(sql, verified, deliveryId) > 0;
    }

    // DELETE: remove a delivery record (e.g. cleaning up a failed/cancelled entry)
    public boolean deleteDelivery(int deliveryId) {
        String sql = "DELETE FROM deliveries WHERE delivery_id = ?";
        return jdbcTemplate.update(sql, deliveryId) > 0;
    }
}

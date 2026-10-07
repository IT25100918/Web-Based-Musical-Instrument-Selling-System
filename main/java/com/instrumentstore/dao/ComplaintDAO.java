package com.instrumentstore.dao;

import com.instrumentstore.model.Complaint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ComplaintDAO {

    private final JdbcTemplate jdbcTemplate;

    public ComplaintDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Complaint> complaintRowMapper = (rs, rowNum) -> {
        Complaint c = new Complaint();
        c.setComplaintId(rs.getInt("complaint_id"));
        c.setUserId(rs.getInt("user_id"));
        c.setCustomerName(rs.getString("full_name"));
        int orderId = rs.getInt("order_id");
        c.setOrderId(rs.wasNull() ? null : orderId);
        c.setSubject(rs.getString("subject"));
        c.setMessage(rs.getString("message"));
        c.setStatus(rs.getString("status"));
        c.setAdminResponse(rs.getString("admin_response"));
        c.setCreatedAt(rs.getTimestamp("created_at"));
        c.setUpdatedAt(rs.getTimestamp("updated_at"));
        return c;
    };

    // CREATE: customer submits a complaint/return/query, optionally tied to an order
    public boolean submitComplaint(int userId, Integer orderId, String subject, String message) {
        String sql = "INSERT INTO complaints (user_id, order_id, subject, message) VALUES (?, ?, ?, ?)";
        return jdbcTemplate.update(sql, userId, orderId, subject, message) > 0;
    }

    // READ: one customer's own complaints, for tracking status (PBI-04)
    public List<Complaint> getByUser(int userId) {
        String sql = "SELECT c.*, u.full_name FROM complaints c JOIN users u ON c.user_id = u.user_id " +
                "WHERE c.user_id = ? ORDER BY c.created_at DESC";
        return jdbcTemplate.query(sql, complaintRowMapper, userId);
    }

    // READ: all complaints, for the admin/CRM panel (PBI-08)
    public List<Complaint> getAll() {
        String sql = "SELECT c.*, u.full_name FROM complaints c JOIN users u ON c.user_id = u.user_id " +
                "ORDER BY (CASE c.status WHEN 'OPEN' THEN 0 WHEN 'IN_PROGRESS' THEN 1 ELSE 2 END), c.created_at DESC";
        return jdbcTemplate.query(sql, complaintRowMapper);
    }

    // READ: single complaint
    public Complaint getById(int complaintId) {
        String sql = "SELECT c.*, u.full_name FROM complaints c JOIN users u ON c.user_id = u.user_id " +
                "WHERE c.complaint_id = ?";
        List<Complaint> results = jdbcTemplate.query(sql, complaintRowMapper, complaintId);
        return results.isEmpty() ? null : results.get(0);
    }

    // UPDATE: admin responds to and/or changes the status of a complaint (PBI-08)
    public boolean respond(int complaintId, String status, String adminResponse) {
        String sql = "UPDATE complaints SET status = ?, admin_response = ?, updated_at = SYSDATETIME() WHERE complaint_id = ?";
        return jdbcTemplate.update(sql, status, adminResponse, complaintId) > 0;
    }

    // Count of OPEN complaints, shown as a badge on the admin dashboard
    public int countOpen() {
        String sql = "SELECT COUNT(*) FROM complaints WHERE status = 'OPEN'";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }
}

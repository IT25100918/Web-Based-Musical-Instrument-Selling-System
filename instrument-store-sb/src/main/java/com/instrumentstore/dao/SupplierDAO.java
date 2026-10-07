package com.instrumentstore.dao;

import com.instrumentstore.model.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class SupplierDAO {

    private final JdbcTemplate jdbcTemplate;

    public SupplierDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Supplier> supplierRowMapper = (rs, rowNum) -> {
        Supplier s = new Supplier();
        s.setSupplierId(rs.getInt("supplier_id"));
        s.setName(rs.getString("name"));
        s.setContactEmail(rs.getString("contact_email"));
        s.setPhone(rs.getString("phone"));
        s.setAddress(rs.getString("address"));
        return s;
    };

    // CREATE
    public boolean addSupplier(Supplier s) {
        String sql = "INSERT INTO suppliers (name, contact_email, phone, address) VALUES (?, ?, ?, ?)";
        return jdbcTemplate.update(sql, s.getName(), s.getContactEmail(), s.getPhone(), s.getAddress()) > 0;
    }

    // READ: all suppliers
    public List<Supplier> getAllSuppliers() {
        String sql = "SELECT * FROM suppliers ORDER BY name";
        return jdbcTemplate.query(sql, supplierRowMapper);
    }

    // READ: one supplier
    public Supplier getSupplierById(int supplierId) {
        String sql = "SELECT * FROM suppliers WHERE supplier_id = ?";
        List<Supplier> results = jdbcTemplate.query(sql, supplierRowMapper, supplierId);
        return results.isEmpty() ? null : results.get(0);
    }

    // UPDATE
    public boolean updateSupplier(Supplier s) {
        String sql = "UPDATE suppliers SET name=?, contact_email=?, phone=?, address=? WHERE supplier_id=?";
        return jdbcTemplate.update(sql, s.getName(), s.getContactEmail(), s.getPhone(), s.getAddress(), s.getSupplierId()) > 0;
    }

    // DELETE - returns false if it doesn't exist or still has purchase orders (foreign key)
    public boolean deleteSupplier(int supplierId) {
        String sql = "DELETE FROM suppliers WHERE supplier_id = ?";
        try {
            return jdbcTemplate.update(sql, supplierId) > 0;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    // Supplier Performance Report: total vs received purchase orders per supplier
    public List<Map<String, Object>> getSupplierPerformance() {
        String sql = "SELECT s.supplier_id, s.name, " +
                "COUNT(po.purchase_order_id) AS total_orders, " +
                "SUM(CASE WHEN po.status = 'RECEIVED' THEN 1 ELSE 0 END) AS received_orders " +
                "FROM suppliers s LEFT JOIN purchase_orders po ON s.supplier_id = po.supplier_id " +
                "GROUP BY s.supplier_id, s.name ORDER BY s.name";
        return jdbcTemplate.queryForList(sql);
    }
}

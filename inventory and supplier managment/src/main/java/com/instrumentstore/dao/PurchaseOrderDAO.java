package com.instrumentstore.dao;

import com.instrumentstore.model.PurchaseOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PurchaseOrderDAO {

    private final JdbcTemplate jdbcTemplate;

    public PurchaseOrderDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PurchaseOrder> poRowMapper = (rs, rowNum) -> {
        PurchaseOrder po = new PurchaseOrder();
        po.setPurchaseOrderId(rs.getInt("purchase_order_id"));
        po.setSupplierId(rs.getInt("supplier_id"));
        po.setProductId(rs.getInt("product_id"));
        po.setQuantity(rs.getInt("quantity"));
        po.setStatus(rs.getString("status"));
        po.setOrderedAt(rs.getTimestamp("ordered_at"));
        po.setSupplierName(rs.getString("supplier_name"));
        po.setProductName(rs.getString("product_name"));
        return po;
    };

    // CREATE: request a new purchase order from a supplier
    public boolean createPurchaseOrder(int supplierId, int productId, int quantity) {
        String sql = "INSERT INTO purchase_orders (supplier_id, product_id, quantity, status) VALUES (?, ?, ?, 'REQUESTED')";
        return jdbcTemplate.update(sql, supplierId, productId, quantity) > 0;
    }

    // READ: all purchase orders, joined with supplier + product names
    public List<PurchaseOrder> getAllPurchaseOrders() {
        String sql = "SELECT po.*, s.name AS supplier_name, p.name AS product_name " +
                "FROM purchase_orders po " +
                "JOIN suppliers s ON po.supplier_id = s.supplier_id " +
                "JOIN products p ON po.product_id = p.product_id " +
                "ORDER BY po.ordered_at DESC";
        return jdbcTemplate.query(sql, poRowMapper);
    }

    // READ: one purchase order
    public PurchaseOrder getById(int purchaseOrderId) {
        String sql = "SELECT po.*, s.name AS supplier_name, p.name AS product_name " +
                "FROM purchase_orders po " +
                "JOIN suppliers s ON po.supplier_id = s.supplier_id " +
                "JOIN products p ON po.product_id = p.product_id " +
                "WHERE po.purchase_order_id = ?";
        List<PurchaseOrder> results = jdbcTemplate.query(sql, poRowMapper, purchaseOrderId);
        return results.isEmpty() ? null : results.get(0);
    }

    // UPDATE: track a purchase order through its lifecycle (approve/receive/cancel)
    public boolean updateStatus(int purchaseOrderId, String status) {
        String sql = "UPDATE purchase_orders SET status = ? WHERE purchase_order_id = ?";
        return jdbcTemplate.update(sql, status, purchaseOrderId) > 0;
    }

    // DELETE: remove a purchase order
    public boolean deletePurchaseOrder(int purchaseOrderId) {
        String sql = "DELETE FROM purchase_orders WHERE purchase_order_id = ?";
        return jdbcTemplate.update(sql, purchaseOrderId) > 0;
    }

    // Financial statement (PBI-28): estimated value of goods received from suppliers.
    // This is a simplified proxy (received quantity x current selling price) since the
    // schema doesn't track a separate supplier invoice/cost amount per purchase order.
    public java.math.BigDecimal getEstimatedReceivedGoodsValue() {
        String sql = "SELECT COALESCE(SUM(po.quantity * p.price), 0) FROM purchase_orders po " +
                "JOIN products p ON po.product_id = p.product_id WHERE po.status = 'RECEIVED'";
        return jdbcTemplate.queryForObject(sql, java.math.BigDecimal.class);
    }
}
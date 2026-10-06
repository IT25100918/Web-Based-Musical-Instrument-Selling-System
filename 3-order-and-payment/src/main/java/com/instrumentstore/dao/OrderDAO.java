package com.instrumentstore.dao;

import com.instrumentstore.model.CartItem;
import com.instrumentstore.model.Order;
import com.instrumentstore.model.OrderItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class OrderDAO {

    private final JdbcTemplate jdbcTemplate;

    public OrderDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Order> orderRowMapper = (rs, rowNum) -> {
        Order order = new Order();
        order.setOrderId(rs.getInt("order_id"));
        order.setUserId(rs.getInt("user_id"));
        order.setTotalAmount(rs.getBigDecimal("total_amount"));
        order.setStatus(rs.getString("status"));
        order.setOrderDate(rs.getTimestamp("order_date"));
        return order;
    };

    private final RowMapper<OrderItem> orderItemRowMapper = (rs, rowNum) -> {
        OrderItem item = new OrderItem();
        item.setOrderItemId(rs.getInt("order_item_id"));
        item.setOrderId(rs.getInt("order_id"));
        item.setProductId(rs.getInt("product_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setUnitPrice(rs.getBigDecimal("unit_price"));
        item.setProductName(rs.getString("name"));
        return item;
    };

    // CREATE: turns a user's cart into a placed order. Returns the new order's id.
    public int placeOrder(int userId, List<CartItem> cartItems) {
        BigDecimal total = cartItems.stream()
                .map(CartItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String orderSql = "INSERT INTO orders (user_id, total_amount, status) VALUES (?, ?, 'PENDING')";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, userId);
            ps.setBigDecimal(2, total);
            return ps;
        }, keyHolder);

        int orderId = keyHolder.getKey().intValue();

        String itemSql = "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)";
        for (CartItem item : cartItems) {
            jdbcTemplate.update(itemSql, orderId, item.getProductId(), item.getQuantity(), item.getPrice());
        }

        return orderId;
    }

    // READ: a customer's own order history
    public List<Order> getOrdersByUser(int userId) {
        String sql = "SELECT * FROM orders WHERE user_id = ? ORDER BY order_date DESC";
        return jdbcTemplate.query(sql, orderRowMapper, userId);
    }

    // READ: all orders (admin view)
    public List<Order> getAllOrders() {
        String sql = "SELECT * FROM orders ORDER BY order_date DESC";
        return jdbcTemplate.query(sql, orderRowMapper);
    }

    // READ: single order
    public Order getOrderById(int orderId) {
        String sql = "SELECT * FROM orders WHERE order_id = ?";
        List<Order> results = jdbcTemplate.query(sql, orderRowMapper, orderId);
        return results.isEmpty() ? null : results.get(0);
    }

    // READ: line items for one order, joined with product name
    public List<OrderItem> getOrderItems(int orderId) {
        String sql = "SELECT oi.*, p.name FROM order_items oi " +
                "JOIN products p ON oi.product_id = p.product_id WHERE oi.order_id = ?";
        return jdbcTemplate.query(sql, orderItemRowMapper, orderId);
    }

    // UPDATE: change order status (admin, or customer cancelling)
    public boolean updateStatus(int orderId, String status) {
        String sql = "UPDATE orders SET status = ? WHERE order_id = ?";
        return jdbcTemplate.update(sql, status, orderId) > 0;
    }

    // Atomic compare-and-set on the order status: only changes the row if it is currently in
    // fromStatus. Used so an order can't be paid twice or paid after being cancelled.
    public boolean transitionStatus(int orderId, String fromStatus, String toStatus) {
        String sql = "UPDATE orders SET status = ? WHERE order_id = ? AND status = ?";
        return jdbcTemplate.update(sql, toStatus, orderId, fromStatus) > 0;
    }

    // Marks an order CANCELLED unless it already is. Returns false if nothing changed, which lets the
    // caller avoid restocking the same order twice.
    public boolean cancelIfNotCancelled(int orderId) {
        String sql = "UPDATE orders SET status = 'CANCELLED' WHERE order_id = ? AND status <> 'CANCELLED'";
        return jdbcTemplate.update(sql, orderId) > 0;
    }

    // READ: has this user actually bought (paid for) this product? Gates who may leave a
    // review (Review Management). Unpaid PENDING and CANCELLED orders don't count.
    public boolean hasUserPurchasedProduct(int userId, int productId) {
        String sql = "SELECT COUNT(*) FROM orders o " +
                "JOIN order_items oi ON o.order_id = oi.order_id " +
                "WHERE o.user_id = ? AND oi.product_id = ? AND o.status IN ('PROCESSING','SHIPPED','DELIVERED')";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, productId);
        return count != null && count > 0;
    }

    // Sales report (PBI-20): order counts and revenue grouped by status
    public List<java.util.Map<String, Object>> getOrderCountsByStatus() {
        String sql = "SELECT status, COUNT(*) AS order_count, SUM(total_amount) AS total_amount FROM orders GROUP BY status";
        return jdbcTemplate.queryForList(sql);
    }

    // Sales report (PBI-20): total revenue across paid orders (unpaid PENDING and CANCELLED orders
    // aren't revenue)
    public BigDecimal getTotalRevenue() {
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE status NOT IN ('CANCELLED','PENDING')";
        return jdbcTemplate.queryForObject(sql, BigDecimal.class);
    }

    // Sales report (PBI-20): daily order count/revenue for the last N days
    public List<java.util.Map<String, Object>> getDailySales(int days) {
        String sql = "SELECT CAST(order_date AS DATE) AS sale_date, COUNT(*) AS order_count, SUM(total_amount) AS total_amount " +
                "FROM orders WHERE order_date >= DATEADD(DAY, -?, SYSDATETIME()) AND status NOT IN ('CANCELLED','PENDING') " +
                "GROUP BY CAST(order_date AS DATE) ORDER BY sale_date";
        return jdbcTemplate.queryForList(sql, days);
    }
}

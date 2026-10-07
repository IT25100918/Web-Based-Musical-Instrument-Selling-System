package com.instrumentstore.dao;

import com.instrumentstore.model.Product;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductDAO {

    private final JdbcTemplate jdbcTemplate;

    public ProductDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Product> productRowMapper = (rs, rowNum) -> {
        Product p = new Product();
        p.setProductId(rs.getInt("product_id"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setPrice(rs.getBigDecimal("price"));
        p.setStockQuantity(rs.getInt("stock_quantity"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setCategoryName(rs.getString("category_name"));
        p.setImageUrl(rs.getString("image_url"));
        p.setPromoPrice(rs.getBigDecimal("promo_price"));
        return p;
    };

    // CREATE
    public boolean addProduct(Product p) {
        String sql = "INSERT INTO products (name, description, price, stock_quantity, category_id, image_url, promo_price) VALUES (?, ?, ?, ?, ?, ?, ?)";
        int rows = jdbcTemplate.update(sql, p.getName(), p.getDescription(), p.getPrice(),
                p.getStockQuantity(), p.getCategoryId(), p.getImageUrl(), p.getPromoPrice());
        return rows > 0;
    }

    // CREATE, returning the generated product_id (used so newly-uploaded gallery images can be linked)
    public int addProductReturningId(Product p) {
        String sql = "INSERT INTO products (name, description, price, stock_quantity, category_id, image_url, promo_price) VALUES (?, ?, ?, ?, ?, ?, ?)";
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, p.getName());
            ps.setString(2, p.getDescription());
            ps.setBigDecimal(3, p.getPrice());
            ps.setInt(4, p.getStockQuantity());
            ps.setInt(5, p.getCategoryId());
            ps.setString(6, p.getImageUrl());
            ps.setBigDecimal(7, p.getPromoPrice());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    // READ: all products with category name joined in
    public List<Product> getAllProducts() {
        String sql = "SELECT p.*, c.name AS category_name FROM products p " +
                "LEFT JOIN categories c ON p.category_id = c.category_id ORDER BY p.product_id DESC";
        return jdbcTemplate.query(sql, productRowMapper);
    }

    // READ: single product
    public Product getProductById(int productId) {
        String sql = "SELECT p.*, c.name AS category_name FROM products p " +
                "LEFT JOIN categories c ON p.category_id = c.category_id WHERE p.product_id = ?";
        List<Product> results = jdbcTemplate.query(sql, productRowMapper, productId);
        return results.isEmpty() ? null : results.get(0);
    }

    // READ: search + optional category filter (Search & Browse function)
    public List<Product> searchProducts(String keyword, Integer categoryId) {
        StringBuilder sql = new StringBuilder(
            "SELECT p.*, c.name AS category_name FROM products p " +
            "LEFT JOIN categories c ON p.category_id = c.category_id WHERE p.name LIKE ? ESCAPE '\\'"
        );
        // The customer's text is searched literally: %, _ and [ would otherwise act as wildcards
        String pattern = "%" + escapeLike(keyword) + "%";
        if (categoryId != null) {
            sql.append(" AND p.category_id = ?");
            return jdbcTemplate.query(sql.toString(), productRowMapper, pattern, categoryId);
        }
        return jdbcTemplate.query(sql.toString(), productRowMapper, pattern);
    }

    // Puts a backslash in front of the characters that mean something inside a SQL Server LIKE pattern
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
    }

    // UPDATE (name, description, prices, category, image - not stock, see below)
    public boolean updateProduct(Product p) {
        // stock_quantity is deliberately NOT updated here: the product form holds a stale copy, so saving it
        // would wipe out sales that happened while it was open. Stock changes only through checkout/cancel,
        // purchase orders and Inventory's "update stock".
        String sql = "UPDATE products SET name=?, description=?, price=?, category_id=?, image_url=?, promo_price=? WHERE product_id=?";
        int rows = jdbcTemplate.update(sql, p.getName(), p.getDescription(), p.getPrice(),
                p.getCategoryId(), p.getImageUrl(), p.getPromoPrice(), p.getProductId());
        return rows > 0;
    }

    // DELETE
    // Returns true if deleted, false if the product doesn't exist OR can't be
    // deleted because it's still referenced elsewhere (past orders, cart items,
    // reviews, purchase orders, etc. all have a FOREIGN KEY on product_id with
    // no ON DELETE CASCADE). Without this catch, that FK violation bubbles up
    // as an unhandled SQLException and Spring shows the generic Whitelabel
    // Error Page instead of a normal redirect with a helpful message.
    public boolean deleteProduct(int productId) {
        String sql = "DELETE FROM products WHERE product_id = ?";
        try {
            int rows = jdbcTemplate.update(sql, productId);
            return rows > 0;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    // Used by Order placement (Shopping Cart & Order Management) to reduce stock
    public boolean decrementStock(int productId, int quantity) {
        String sql = "UPDATE products SET stock_quantity = stock_quantity - ? WHERE product_id = ? AND stock_quantity >= ?";
        int rows = jdbcTemplate.update(sql, quantity, productId, quantity);
        return rows > 0;
    }

    // Used by Inventory Management: manually set stock to an exact value
    public boolean updateStock(int productId, int newQuantity) {
        String sql = "UPDATE products SET stock_quantity = ? WHERE product_id = ?";
        return jdbcTemplate.update(sql, newQuantity, productId) > 0;
    }

    // Used by Inventory Management: called when a purchase order is marked RECEIVED
    public boolean increaseStock(int productId, int quantityReceived) {
        String sql = "UPDATE products SET stock_quantity = stock_quantity + ? WHERE product_id = ?";
        return jdbcTemplate.update(sql, quantityReceived, productId) > 0;
    }

    // Inventory report (PBI-24): current stock, units sold, units received per product
    public List<java.util.Map<String, Object>> getInventoryReport() {
        String sql = "SELECT p.product_id, p.name, p.stock_quantity, " +
                "(SELECT COALESCE(SUM(oi.quantity),0) FROM order_items oi JOIN orders o ON oi.order_id = o.order_id " +
                "   WHERE oi.product_id = p.product_id AND o.status <> 'CANCELLED') AS units_sold, " +
                "(SELECT COALESCE(SUM(po.quantity),0) FROM purchase_orders po " +
                "   WHERE po.product_id = p.product_id AND po.status = 'RECEIVED') AS units_received " +
                "FROM products p ORDER BY p.name";
        return jdbcTemplate.queryForList(sql);
    }
}

package com.instrumentstore.dao;

import com.instrumentstore.model.CartItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * DAO for Shopping Cart & Order Management (cart part).
 * Each user has exactly one cart row; cart_items holds the products in it.
 */
@Repository
public class CartDAO {

    private final JdbcTemplate jdbcTemplate;

    public CartDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<CartItem> cartItemRowMapper = (rs, rowNum) -> {
        CartItem item = new CartItem();
        item.setCartItemId(rs.getInt("cart_item_id"));
        item.setCartId(rs.getInt("cart_id"));
        item.setProductId(rs.getInt("product_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setProductName(rs.getString("name"));
        item.setPrice(rs.getBigDecimal("price"));
        item.setImageUrl(rs.getString("image_url"));
        item.setAvailableStock(rs.getInt("stock_quantity"));
        return item;
    };

    // Finds the user's cart id, creating one if they don't have one yet
    public int getOrCreateCartId(int userId) {
        String findSql = "SELECT cart_id FROM cart WHERE user_id = ?";
        List<Integer> existing = jdbcTemplate.query(findSql, (rs, rowNum) -> rs.getInt("cart_id"), userId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String insertSql = "INSERT INTO cart (user_id) VALUES (?)";
        jdbcTemplate.update(insertSql, userId);
        return jdbcTemplate.queryForObject("SELECT cart_id FROM cart WHERE user_id = ?", Integer.class, userId);
    }

    // CREATE / UPDATE: adds a product, or increases quantity if it's already in the cart.
    // Returns false (and changes nothing) when the quantity isn't positive, the product doesn't
    // exist, or it's out of stock. The resulting line quantity is capped at the available stock.
    public boolean addToCart(int userId, int productId, int quantity) {
        if (quantity <= 0) {
            return false;
        }

        List<Integer> stockRows = jdbcTemplate.query(
                "SELECT stock_quantity FROM products WHERE product_id = ?",
                (rs, rowNum) -> rs.getInt("stock_quantity"), productId);
        if (stockRows.isEmpty() || stockRows.get(0) <= 0) {
            return false;
        }
        int stock = stockRows.get(0);

        int cartId = getOrCreateCartId(userId);

        String checkSql = "SELECT cart_item_id, quantity FROM cart_items WHERE cart_id = ? AND product_id = ?";
        List<int[]> existingItem = jdbcTemplate.query(checkSql,
                (rs, rowNum) -> new int[]{rs.getInt("cart_item_id"), rs.getInt("quantity")}, cartId, productId);

        if (!existingItem.isEmpty()) {
            int newQuantity = Math.min(existingItem.get(0)[1] + quantity, stock);
            String updateSql = "UPDATE cart_items SET quantity = ? WHERE cart_item_id = ?";
            return jdbcTemplate.update(updateSql, newQuantity, existingItem.get(0)[0]) > 0;
        } else {
            String insertSql = "INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?, ?, ?)";
            return jdbcTemplate.update(insertSql, cartId, productId, Math.min(quantity, stock)) > 0;
        }
    }

    // READ: all items in a user's cart, joined with product details
    public List<CartItem> getCartItems(int userId) {
        String sql = "SELECT ci.cart_item_id, ci.cart_id, ci.product_id, ci.quantity, " +
                "p.name, " +
                // Charge the price the customer was shown: the promo price when it is active
                "CASE WHEN p.promo_price IS NOT NULL AND p.promo_price < p.price THEN p.promo_price ELSE p.price END AS price, " +
                "p.image_url, p.stock_quantity " +
                "FROM cart_items ci " +
                "JOIN cart c ON ci.cart_id = c.cart_id " +
                "JOIN products p ON ci.product_id = p.product_id " +
                "WHERE c.user_id = ?";
        return jdbcTemplate.query(sql, cartItemRowMapper, userId);
    }

    public BigDecimal getCartTotal(int userId) {
        return getCartItems(userId).stream()
                .map(CartItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // UPDATE: change quantity of one cart line. Scoped to the owning user - without the user_id
    // check any logged-in customer could edit anybody else's cart just by guessing cart_item_ids.
    // The quantity is capped at the available stock; a product with no stock left is removed.
    public boolean updateQuantity(int userId, int cartItemId, int quantity) {
        List<Integer> stockRows = jdbcTemplate.query(
                "SELECT p.stock_quantity FROM cart_items ci " +
                "JOIN cart c ON ci.cart_id = c.cart_id " +
                "JOIN products p ON ci.product_id = p.product_id " +
                "WHERE ci.cart_item_id = ? AND c.user_id = ?",
                (rs, rowNum) -> rs.getInt("stock_quantity"), cartItemId, userId);
        if (stockRows.isEmpty()) {
            return false; // not this user's cart line
        }
        int stock = stockRows.get(0);
        if (stock <= 0) {
            return removeItem(userId, cartItemId);
        }
        String sql = "UPDATE ci SET ci.quantity = ? FROM cart_items ci " +
                "JOIN cart c ON ci.cart_id = c.cart_id WHERE ci.cart_item_id = ? AND c.user_id = ?";
        return jdbcTemplate.update(sql, Math.min(quantity, stock), cartItemId, userId) > 0;
    }

    // DELETE: remove one item from the cart (only if it belongs to this user)
    public boolean removeItem(int userId, int cartItemId) {
        String sql = "DELETE ci FROM cart_items ci JOIN cart c ON ci.cart_id = c.cart_id " +
                "WHERE ci.cart_item_id = ? AND c.user_id = ?";
        return jdbcTemplate.update(sql, cartItemId, userId) > 0;
    }

    // Called after an order is placed successfully
    public void clearCart(int userId) {
        String sql = "DELETE ci FROM cart_items ci JOIN cart c ON ci.cart_id = c.cart_id WHERE c.user_id = ?";
        jdbcTemplate.update(sql, userId);
    }
}

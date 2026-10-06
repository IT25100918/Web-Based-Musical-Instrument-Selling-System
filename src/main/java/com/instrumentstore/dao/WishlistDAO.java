package com.instrumentstore.dao;

import com.instrumentstore.model.WishlistItem;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class WishlistDAO {

    private final JdbcTemplate jdbcTemplate;

    public WishlistDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<WishlistItem> wishlistRowMapper = (rs, rowNum) -> {
        WishlistItem item = new WishlistItem();
        item.setWishlistId(rs.getInt("wishlist_id"));
        item.setUserId(rs.getInt("user_id"));
        item.setProductId(rs.getInt("product_id"));
        item.setProductName(rs.getString("name"));
        item.setPrice(rs.getBigDecimal("price"));
        item.setImageUrl(rs.getString("image_url"));
        item.setStockQuantity(rs.getInt("stock_quantity"));
        return item;
    };

    // CREATE: add a product to the user's wishlist (ignore if already there)
    public boolean addToWishlist(int userId, int productId) {
        if (isInWishlist(userId, productId)) {
            return true;
        }
        String sql = "INSERT INTO wishlist (user_id, product_id) VALUES (?, ?)";
        try {
            return jdbcTemplate.update(sql, userId, productId) > 0;
        } catch (DataIntegrityViolationException e) {
            return false; // product doesn't exist (foreign key)
        }
    }

    // READ: all wishlist items for a user, joined with product details
    public List<WishlistItem> getWishlistByUser(int userId) {
        String sql = "SELECT w.wishlist_id, w.user_id, w.product_id, p.name, " +
                "CASE WHEN p.promo_price IS NOT NULL AND p.promo_price < p.price THEN p.promo_price ELSE p.price END AS price, " +
                "p.image_url, p.stock_quantity " +
                "FROM wishlist w JOIN products p ON w.product_id = p.product_id " +
                "WHERE w.user_id = ? ORDER BY w.added_at DESC";
        return jdbcTemplate.query(sql, wishlistRowMapper, userId);
    }

    public boolean isInWishlist(int userId, int productId) {
        String sql = "SELECT COUNT(*) FROM wishlist WHERE user_id = ? AND product_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, productId);
        return count != null && count > 0;
    }

    // DELETE: remove one item from the wishlist (only if it belongs to this user)
    public boolean removeFromWishlist(int userId, int wishlistId) {
        String sql = "DELETE FROM wishlist WHERE wishlist_id = ? AND user_id = ?";
        return jdbcTemplate.update(sql, wishlistId, userId) > 0;
    }
}

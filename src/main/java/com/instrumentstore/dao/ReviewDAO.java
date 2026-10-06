package com.instrumentstore.dao;

import com.instrumentstore.model.Review;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * DAO for the `reviews` table (Customer Review Management).
 * Customers get at most one review per product - submitting again edits
 * the existing row instead of creating a duplicate.
 */
@Repository
public class ReviewDAO {

    private final JdbcTemplate jdbcTemplate;

    public ReviewDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Review> reviewRowMapper = (rs, rowNum) -> {
        Review r = new Review();
        r.setReviewId(rs.getInt("review_id"));
        r.setProductId(rs.getInt("product_id"));
        r.setUserId(rs.getInt("user_id"));
        r.setReviewerName(rs.getString("full_name"));
        r.setRating(rs.getInt("rating"));
        r.setComment(rs.getString("comment"));
        r.setHidden(rs.getBoolean("is_hidden"));
        r.setFlagCount(rs.getInt("flag_count"));
        r.setCreatedAt(rs.getTimestamp("created_at"));
        r.setUpdatedAt(rs.getTimestamp("updated_at"));
        return r;
    };

    // CREATE or UPDATE: one review per (user, product) - re-submitting edits it.
    // SQL Server has no "ON DUPLICATE KEY UPDATE"; MERGE is the T-SQL equivalent.
    public void addOrUpdateReview(int productId, int userId, int rating, String comment) {
        String sql = "MERGE INTO reviews AS target " +
                "USING (SELECT ? AS product_id, ? AS user_id) AS src " +
                "ON target.product_id = src.product_id AND target.user_id = src.user_id " +
                "WHEN MATCHED THEN " +
                "UPDATE SET rating = ?, comment = ?, updated_at = SYSDATETIME() " +
                "WHEN NOT MATCHED THEN " +
                "INSERT (product_id, user_id, rating, comment) VALUES (src.product_id, src.user_id, ?, ?);";
        jdbcTemplate.update(sql, productId, userId, rating, comment, rating, comment);
    }

    // READ: all reviews for a product, newest first, joined with the reviewer's name.
    // Hidden (moderated) reviews are excluded - this is what the public product page uses.
    public List<Review> getReviewsForProduct(int productId) {
        String sql = "SELECT r.*, u.full_name FROM reviews r " +
                "JOIN users u ON r.user_id = u.user_id " +
                "WHERE r.product_id = ? AND r.is_hidden = 0 ORDER BY r.updated_at DESC";
        return jdbcTemplate.query(sql, reviewRowMapper, productId);
    }

    // READ: this user's own review for a product, if any (used to prefill the edit form)
    public Review getUserReviewForProduct(int userId, int productId) {
        String sql = "SELECT r.*, u.full_name FROM reviews r " +
                "JOIN users u ON r.user_id = u.user_id " +
                "WHERE r.user_id = ? AND r.product_id = ?";
        List<Review> results = jdbcTemplate.query(sql, reviewRowMapper, userId, productId);
        return results.isEmpty() ? null : results.get(0);
    }

    // READ: average rating for a product (null if no reviews yet)
    public Double getAverageRating(int productId) {
        String sql = "SELECT AVG(rating) FROM reviews WHERE product_id = ?";
        return jdbcTemplate.queryForObject(sql, Double.class, productId);
    }

    // READ: number of reviews for a product
    public int getReviewCount(int productId) {
        String sql = "SELECT COUNT(*) FROM reviews WHERE product_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, productId);
        return count == null ? 0 : count;
    }

    // DELETE: only the review's own author may remove it
    public boolean deleteReview(int productId, int userId) {
        String sql = "DELETE FROM reviews WHERE product_id = ? AND user_id = ?";
        return jdbcTemplate.update(sql, productId, userId) > 0;
    }

    // ============================================================
    // Review moderation (admin / Customer Relations Manager)
    // ============================================================

    private final RowMapper<Review> adminReviewRowMapper = (rs, rowNum) -> {
        Review r = reviewRowMapper.mapRow(rs, rowNum);
        r.setProductName(rs.getString("product_name"));
        return r;
    };

    // READ: every review regardless of hidden status, worst-flagged first, for the moderation queue
    public List<Review> getAllReviewsForAdmin() {
        String sql = "SELECT r.*, u.full_name, p.name AS product_name FROM reviews r " +
                "JOIN users u ON r.user_id = u.user_id " +
                "JOIN products p ON r.product_id = p.product_id " +
                "ORDER BY r.flag_count DESC, r.updated_at DESC";
        return jdbcTemplate.query(sql, adminReviewRowMapper);
    }

    // UPDATE: hide a review from the public product page without deleting it
    public boolean hideReview(int reviewId) {
        String sql = "UPDATE reviews SET is_hidden = 1 WHERE review_id = ?";
        return jdbcTemplate.update(sql, reviewId) > 0;
    }

    // UPDATE: restore a previously hidden review
    public boolean unhideReview(int reviewId) {
        String sql = "UPDATE reviews SET is_hidden = 0 WHERE review_id = ?";
        return jdbcTemplate.update(sql, reviewId) > 0;
    }

    // ============================================================
    // Report / flag a review (any logged-in user, not just staff)
    // ============================================================


    // Community flagging: one flag per user per review. Returns true only if THIS call added a new flag.
    // Flagging your own review, or one that doesn't exist, adds nothing. The review_flags table's
    // primary key (review_id, user_id) is the safety net if two identical requests arrive together.
    public boolean flagReview(int reviewId, int userId) {
        String insertSql = "INSERT INTO review_flags (review_id, user_id) " +
                "SELECT ?, ? WHERE EXISTS (SELECT 1 FROM reviews WHERE review_id = ? AND user_id <> ?) " +
                "AND NOT EXISTS (SELECT 1 FROM review_flags WHERE review_id = ? AND user_id = ?)";
        int inserted;
        try {
            inserted = jdbcTemplate.update(insertSql, reviewId, userId, reviewId, userId, reviewId, userId);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return false;
        }
        if (inserted == 0) {
            return false;
        }
        jdbcTemplate.update("UPDATE reviews SET flag_count = COALESCE(flag_count, 0) + 1 WHERE review_id = ?", reviewId);
        return true;
    }
}

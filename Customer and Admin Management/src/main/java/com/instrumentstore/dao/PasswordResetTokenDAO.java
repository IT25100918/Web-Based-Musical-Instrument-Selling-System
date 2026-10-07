package com.instrumentstore.dao;

import com.instrumentstore.model.PasswordResetToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * DAO for the `password_reset_tokens` table - one-time, expiring, single-use
 * links mailed to a user who requested a password reset while logged out.
 */
@Repository
public class PasswordResetTokenDAO {

    private final JdbcTemplate jdbcTemplate;

    public PasswordResetTokenDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PasswordResetToken> rowMapper = (rs, rowNum) -> {
        PasswordResetToken t = new PasswordResetToken();
        t.setTokenId(rs.getInt("token_id"));
        t.setUserId(rs.getInt("user_id"));
        t.setToken(rs.getString("token"));
        t.setExpiresAt(rs.getTimestamp("expires_at"));
        t.setUsed(rs.getBoolean("used"));
        t.setCreatedAt(rs.getTimestamp("created_at"));
        return t;
    };

    // CREATE: issues a new token valid for 30 minutes
    public void createToken(int userId, String token) {
        String sql = "INSERT INTO password_reset_tokens (user_id, token, expires_at) VALUES (?, ?, ?)";
        Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + 30L * 60 * 1000);
        jdbcTemplate.update(sql, userId, token, expiresAt);
    }

    // READ: look up a token when the user clicks the emailed link / submits the reset form
    public PasswordResetToken findByToken(String token) {
        String sql = "SELECT * FROM password_reset_tokens WHERE token = ?";
        List<PasswordResetToken> results = jdbcTemplate.query(sql, rowMapper, token);
        return results.isEmpty() ? null : results.get(0);
    }

    // UPDATE: mark a token as used so it can't be replayed
    public void markUsed(int tokenId) {
        String sql = "UPDATE password_reset_tokens SET used = 1 WHERE token_id = ?";
        jdbcTemplate.update(sql, tokenId);
    }

    // DELETE: clear out any outstanding tokens for a user before issuing a fresh one
    public void deleteTokensForUser(int userId) {
        String sql = "DELETE FROM password_reset_tokens WHERE user_id = ?";
        jdbcTemplate.update(sql, userId);
    }
}

package com.instrumentstore.dao;

import com.instrumentstore.model.EmailVerificationToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * DAO for the `email_verification_tokens` table - one-time links mailed to
 * new users to confirm their email address (Session/Account Verification).
 */
@Repository
public class EmailVerificationTokenDAO {

    private final JdbcTemplate jdbcTemplate;

    public EmailVerificationTokenDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<EmailVerificationToken> rowMapper = (rs, rowNum) -> {
        EmailVerificationToken t = new EmailVerificationToken();
        t.setTokenId(rs.getInt("token_id"));
        t.setUserId(rs.getInt("user_id"));
        t.setToken(rs.getString("token"));
        t.setExpiresAt(rs.getTimestamp("expires_at"));
        t.setCreatedAt(rs.getTimestamp("created_at"));
        return t;
    };

    // CREATE: issues a new token valid for 24 hours
    public void createToken(int userId, String token) {
        String sql = "INSERT INTO email_verification_tokens (user_id, token, expires_at) VALUES (?, ?, ?)";
        Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + 24L * 60 * 60 * 1000);
        jdbcTemplate.update(sql, userId, token, expiresAt);
    }

    // READ: look up a token when the user clicks the emailed link
    public EmailVerificationToken findByToken(String token) {
        String sql = "SELECT * FROM email_verification_tokens WHERE token = ?";
        List<EmailVerificationToken> results = jdbcTemplate.query(sql, rowMapper, token);
        return results.isEmpty() ? null : results.get(0);
    }

    // DELETE: clear out any outstanding tokens for a user once they've verified
    // (or before issuing a fresh one on resend, so old links stop working)
    public void deleteTokensForUser(int userId) {
        String sql = "DELETE FROM email_verification_tokens WHERE user_id = ?";
        jdbcTemplate.update(sql, userId);
    }
}

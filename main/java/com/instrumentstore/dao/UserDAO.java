package com.instrumentstore.dao;

import com.instrumentstore.model.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * DAO (Data Access Object) pattern: this class is the ONLY place that writes
 * SQL for the `users` table. Controllers never write SQL directly.
 *
 * @Repository tells Spring to create one instance of this class automatically
 * and hand it to whatever controller asks for it (Dependency Injection) -
 * that's why there's no `new UserDAO()` anywhere; Spring does that for us.
 *
 * JdbcTemplate is injected automatically because Spring Boot already knows
 * how to connect to the DB (it read application.properties).
 */
@Repository
public class UserDAO {

    private final JdbcTemplate jdbcTemplate;

    // Spring automatically supplies JdbcTemplate here (constructor injection)
    public UserDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Converts one database row into a User object
    private final RowMapper<User> userRowMapper = (rs, rowNum) -> {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPassword(rs.getString("password"));
        user.setPhone(rs.getString("phone"));
        user.setAddress(rs.getString("address"));
        user.setRole(rs.getString("role"));
        user.setEmailVerified(rs.getBoolean("email_verified"));
        user.setStatus(rs.getString("status"));
        return user;
    };

    // CREATE
    public boolean registerUser(User user) {
        String sql = "INSERT INTO users (full_name, email, password, phone, address, role) VALUES (?, ?, ?, ?, ?, ?)";
        try {
            int rows = jdbcTemplate.update(sql, user.getFullName(), user.getEmail(), user.getPassword(),
                    user.getPhone(), user.getAddress(), user.getRole());
            return rows > 0;
        } catch (DataIntegrityViolationException e) {
            return false; // e.g. someone registered the same email a moment earlier (UNIQUE constraint)
        }
    }

    // READ: used for login
    public User findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        List<User> results = jdbcTemplate.query(sql, userRowMapper, email);
        return results.isEmpty() ? null : results.get(0);
    }

    // READ: profile lookup
    public User findById(int userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        List<User> results = jdbcTemplate.query(sql, userRowMapper, userId);
        return results.isEmpty() ? null : results.get(0);
    }

    // UPDATE: edit profile
    public boolean updateProfile(User user) {
        String sql = "UPDATE users SET full_name = ?, phone = ?, address = ? WHERE user_id = ?";
        int rows = jdbcTemplate.update(sql, user.getFullName(), user.getPhone(), user.getAddress(), user.getUserId());
        return rows > 0;
    }

    // UPDATE: change password
    public boolean changePassword(int userId, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE user_id = ?";
        int rows = jdbcTemplate.update(sql, newPassword, userId);
        return rows > 0;
    }

    // UPDATE: mark the account's email as verified (Session/Account Verification)
    public boolean markEmailVerified(int userId) {
        String sql = "UPDATE users SET email_verified = 1 WHERE user_id = ?";
        return jdbcTemplate.update(sql, userId) > 0;
    }

    // UPDATE: suspend or reactivate a customer account. status must be "ACTIVE" or "SUSPENDED"
    // (matches the DB CHECK constraint) - callers are expected to validate before calling this.
    public boolean updateStatus(int userId, String status) {
        String sql = "UPDATE users SET status = ? WHERE user_id = ?";
        return jdbcTemplate.update(sql, status, userId) > 0;
    }

    // DELETE - returns false if the account can't be removed because it still owns orders
    // (orders.user_id has no ON DELETE CASCADE, so the database refuses)
    public boolean deleteAccount(int userId) {
        // Orders are history that must be kept - check first so nothing else is touched in that case
        Integer orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, userId);
        if (orderCount != null && orderCount > 0) {
            return false;
        }
        // Flags this user raised on other people's reviews: take them back out of those reviews' counts,
        // then delete them (review_flags.user_id has no cascade - SQL Server forbids a second cascade path).
        jdbcTemplate.update("UPDATE reviews SET flag_count = flag_count - 1 " +
                "WHERE flag_count > 0 AND review_id IN (SELECT review_id FROM review_flags WHERE user_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM review_flags WHERE user_id = ?", userId);

        String sql = "DELETE FROM users WHERE user_id = ?";
        try {
            int rows = jdbcTemplate.update(sql, userId);
            return rows > 0;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    // READ: Customer Relations dashboard (PBI-05, PBI-06) - customers with an
    // unverified email, missing/incomplete profile/delivery info, or who are
    // currently suspended, plus how many times they've requested a password
    // reset (repeated attempts can signal an account issue worth following up on).
    public List<java.util.Map<String, Object>> getFlaggedCustomers() {
        String sql = "SELECT u.user_id, u.full_name, u.email, u.phone, u.address, u.email_verified, u.status, " +
                "(SELECT COUNT(*) FROM password_reset_tokens t WHERE t.user_id = u.user_id) AS reset_attempts " +
                "FROM users u " +
                "WHERE u.role = 'CUSTOMER' AND (" +
                "  u.email_verified = 0 " +
                "  OR u.phone IS NULL OR u.phone = '' " +
                "  OR u.address IS NULL OR u.address = '' " +
                "  OR u.status = 'SUSPENDED' " +
                "  OR (SELECT COUNT(*) FROM password_reset_tokens t WHERE t.user_id = u.user_id) >= 3" +
                ") ORDER BY reset_attempts DESC, u.email_verified ASC";
        return jdbcTemplate.queryForList(sql);
    }
}

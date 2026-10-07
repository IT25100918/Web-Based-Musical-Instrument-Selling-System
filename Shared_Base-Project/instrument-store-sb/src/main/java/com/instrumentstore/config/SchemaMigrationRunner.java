package com.instrumentstore.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Self-healing schema migration: runs once every time the app starts, checks
 * whether the columns/constraints added after the original database.sql
 * (account suspension, review moderation, review flagging) already exist in
 * THIS database, and adds whichever ones are missing.
 *
 * This exists because database.sql only runs once, by hand, when a database
 * is first created - it does nothing for a database that already existed
 * before a column was added. Rather than relying on someone remembering to
 * run a separate migration script in SSMS/Azure Data Studio, the app now
 * fixes its own schema on every startup. Safe to run repeatedly: each check
 * is "does this already exist?" before doing anything, so it's a no-op once
 * the database is up to date.
 */
@Component
public class SchemaMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        widenImageUrl("products", "NULL");
        widenImageUrl("product_images", "NOT NULL");
        addColumnIfMissing("users", "status", "VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'");
        addConstraintIfMissing("users", "CK_users_status", "CHECK (status IN ('ACTIVE', 'SUSPENDED'))");
        addColumnIfMissing("reviews", "is_hidden", "BIT DEFAULT 0");
        addColumnIfMissing("reviews", "flag_count", "INT DEFAULT 0");
        // One flag per user per review (review_id cascades; user_id deliberately does not - see UserDAO.deleteAccount)
        createTableIfMissing("review_flags",
                "CREATE TABLE review_flags (" +
                "review_id INT NOT NULL, " +
                "user_id INT NOT NULL, " +
                "created_at DATETIME2 DEFAULT SYSDATETIME(), " +
                "PRIMARY KEY (review_id, user_id), " +
                "FOREIGN KEY (review_id) REFERENCES reviews(review_id) ON DELETE CASCADE, " +
                "FOREIGN KEY (user_id) REFERENCES users(user_id))");
    }

    // Product image URLs were VARCHAR(255); widen to 1000 on existing databases (no-op once done)
    private void widenImageUrl(String table, String nullability) {
        Integer len = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = 'image_url'",
                Integer.class, table);
        if (len != null && len != -1 && len < 1000) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN image_url VARCHAR(1000) " + nullability);
            System.out.println("[schema migration] widened " + table + ".image_url to 1000");
        }
    }

    private void createTableIfMissing(String table, String createSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?", Integer.class, table);
        if (count == null || count == 0) {
            jdbcTemplate.execute(createSql);
            System.out.println("[schema migration] created missing table " + table);
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        if (!columnExists(table, column)) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + column + " " + definition);
            System.out.println("[schema migration] added missing column " + table + "." + column);
        }
    }

    private boolean constraintExists(String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys.check_constraints WHERE name = ?",
                Integer.class, constraintName);
        return count != null && count > 0;
    }

    private void addConstraintIfMissing(String table, String constraintName, String definition) {
        if (!constraintExists(constraintName)) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraintName + " " + definition);
            System.out.println("[schema migration] added missing constraint " + constraintName);
        }
    }
}

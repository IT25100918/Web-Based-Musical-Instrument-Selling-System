-- Run in SSMS against the server where you ran database.sql. Read-only: it changes nothing.
USE instrument_store;
GO

-- 1. All 18 tables exist (expect 18)
SELECT COUNT(*) AS table_count FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE';

-- 2. Seed data (expect: categories 4, products 4, users 6)
SELECT 'categories' AS tbl, COUNT(*) AS rows_found FROM categories
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'users', COUNT(*) FROM users;

-- 3. Duplicate check (expect NO rows from any of these three)
SELECT name, COUNT(*) AS copies FROM categories GROUP BY name HAVING COUNT(*) > 1;
SELECT name, COUNT(*) AS copies FROM products   GROUP BY name HAVING COUNT(*) > 1;
SELECT email, COUNT(*) AS copies FROM users     GROUP BY email HAVING COUNT(*) > 1;

-- 4. The six demo logins and their roles (expect 6 rows, all email_verified = 1)
SELECT email, role, email_verified FROM users ORDER BY role;

-- 5. Every product points at a real category (expect NO rows)
SELECT p.product_id, p.name FROM products p
LEFT JOIN categories c ON c.category_id = p.category_id
WHERE p.category_id IS NOT NULL AND c.category_id IS NULL;

-- 6. Foreign keys are present (expect a list; empty means the constraints were not created)
SELECT OBJECT_NAME(parent_object_id) AS child_table, name AS fk_name FROM sys.foreign_keys ORDER BY child_table;

-- 7. The app's login exists and can read/write (expect 1 row for each)
SELECT name FROM sys.database_principals WHERE name = 'instrument_app';
SELECT IS_ROLEMEMBER('db_owner', 'instrument_app') AS app_user_is_db_owner;

-- 8. Transaction smoke test: inserts a row and rolls it back, so nothing is left behind
BEGIN TRAN;
INSERT INTO categories (name, description) VALUES ('ZZ_TEST', 'rollback test');
SELECT COUNT(*) AS test_row_visible_inside_tran FROM categories WHERE name = 'ZZ_TEST';
ROLLBACK;
SELECT COUNT(*) AS test_row_after_rollback FROM categories WHERE name = 'ZZ_TEST';   -- expect 0
GO

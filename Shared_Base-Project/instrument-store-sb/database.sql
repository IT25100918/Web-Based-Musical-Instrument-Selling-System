-- ============================================================
-- Web-based Musical Instruments Selling System
-- Full Database Schema (SQL Server / T-SQL)
--
-- This is the ONLY database script for this project - it creates
-- the database and every table already in its final shape (all
-- former "schema-updates" migrations are folded in), plus seed/demo
-- data. There is nothing else to run afterwards.
--
-- Run this ONCE, in SSMS (New Query window) or sqlcmd, against a
-- fresh server.
-- ============================================================

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'instrument_store')
BEGIN
    CREATE DATABASE instrument_store;
END
GO

USE instrument_store;
GO

-- 1. CUSTOMER ACCOUNT MANAGEMENT ------------------------------
CREATE TABLE users (
    user_id INT IDENTITY(1,1) PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    address VARCHAR(255),
    -- Widened to VARCHAR(30) (was VARCHAR(10)) to fit persona role names.
    role VARCHAR(30) NOT NULL DEFAULT 'CUSTOMER',
    email_verified BIT DEFAULT 0,
    -- Account suspension (Customer Account & Admin Management): a SUSPENDED customer
    -- can't log in until an admin sets them back to ACTIVE.
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME2 DEFAULT SYSDATETIME(),
    CONSTRAINT CK_users_role CHECK (role IN (
        'CUSTOMER',
        'ADMIN',
        'CUSTOMER_RELATIONS_MANAGER',
        'CATALOG_MANAGER',
        'ORDER_PAYMENT_MANAGER',
        'INVENTORY_MANAGER',
        'DELIVERY_MANAGER'
    )),
    CONSTRAINT CK_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);
GO

-- Session/Account Verification (email confirmation) - one-time tokens mailed
-- to the user on registration; expired/used tokens are just left in place
-- (small table, simplicity over cleanup jobs for a project this size).
CREATE TABLE email_verification_tokens (
    token_id INT IDENTITY(1,1) PRIMARY KEY,
    user_id INT NOT NULL,
    token VARCHAR(100) NOT NULL UNIQUE,
    expires_at DATETIME2 NOT NULL,
    created_at DATETIME2 DEFAULT SYSDATETIME(),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
GO

-- Password Reset / Forgot Password - one-time, expiring, single-use tokens
CREATE TABLE password_reset_tokens (
    token_id INT IDENTITY(1,1) PRIMARY KEY,
    user_id INT NOT NULL,
    token VARCHAR(100) NOT NULL UNIQUE,
    expires_at DATETIME2 NOT NULL,
    used BIT DEFAULT 0,
    created_at DATETIME2 DEFAULT SYSDATETIME(),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
GO

CREATE TABLE wishlist (
    wishlist_id INT IDENTITY(1,1) PRIMARY KEY,
    user_id INT NOT NULL,
    product_id INT NOT NULL,
    added_at DATETIME2 DEFAULT SYSDATETIME(),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
GO

-- 2. PRODUCT CATALOG MANAGEMENT --------------------------------
CREATE TABLE categories (
    category_id INT IDENTITY(1,1) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    image_url VARCHAR(255)
);
GO

CREATE TABLE products (
    product_id INT IDENTITY(1,1) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(MAX),
    price DECIMAL(10,2) NOT NULL,
    stock_quantity INT DEFAULT 0,
    category_id INT,
    image_url VARCHAR(1000),
    -- Promotions (product sale/discount price).
    promo_price DECIMAL(10,2) NULL,
    created_at DATETIME2 DEFAULT SYSDATETIME(),
    FOREIGN KEY (category_id) REFERENCES categories(category_id)
);
GO

ALTER TABLE wishlist ADD FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE;
GO

-- Multiple images per product (galleries).
CREATE TABLE product_images (
    image_id INT IDENTITY(1,1) PRIMARY KEY,
    product_id INT NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
);
GO

-- Customer reviews on purchased products (its own function - Customer
-- Review Management). One review per customer per product - writing a
-- second one edits the first rather than creating a duplicate row.
CREATE TABLE reviews (
    review_id INT IDENTITY(1,1) PRIMARY KEY,
    product_id INT NOT NULL,
    user_id INT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(MAX),
    -- Review moderation (Customer Relations Manager can hide an abusive/spam review
    -- without deleting it) and community flagging (any logged-in user can report one).
    is_hidden BIT DEFAULT 0,
    flag_count INT DEFAULT 0,
    created_at DATETIME2 DEFAULT SYSDATETIME(),
    updated_at DATETIME2 DEFAULT SYSDATETIME(),
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uniq_user_product_review UNIQUE (user_id, product_id),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
);

-- One flag per user per review. review_id cascades with the review; user_id has NO cascade
-- because SQL Server rejects a second cascade path from users (users -> reviews -> review_flags).
CREATE TABLE review_flags (
    review_id INT NOT NULL,
    user_id INT NOT NULL,
    created_at DATETIME2 DEFAULT SYSDATETIME(),
    PRIMARY KEY (review_id, user_id),
    FOREIGN KEY (review_id) REFERENCES reviews(review_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
GO

-- 3. SHOPPING CART & ORDER MANAGEMENT ---------------------------
CREATE TABLE cart (
    cart_id INT IDENTITY(1,1) PRIMARY KEY,
    user_id INT NOT NULL,
    created_at DATETIME2 DEFAULT SYSDATETIME(),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
GO

CREATE TABLE cart_items (
    cart_item_id INT IDENTITY(1,1) PRIMARY KEY,
    cart_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    FOREIGN KEY (cart_id) REFERENCES cart(cart_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);
GO

CREATE TABLE orders (
    order_id INT IDENTITY(1,1) PRIMARY KEY,
    user_id INT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PROCESSING','SHIPPED','DELIVERED','CANCELLED')),
    order_date DATETIME2 DEFAULT SYSDATETIME(),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
GO

CREATE TABLE order_items (
    order_item_id INT IDENTITY(1,1) PRIMARY KEY,
    order_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);
GO

-- Complaints & Returns (Customer Relations Management)
CREATE TABLE complaints (
    complaint_id INT IDENTITY(1,1) PRIMARY KEY,
    user_id INT NOT NULL,
    order_id INT NULL,
    subject VARCHAR(150) NOT NULL,
    message VARCHAR(MAX) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED')),
    admin_response VARCHAR(MAX),
    created_at DATETIME2 DEFAULT SYSDATETIME(),
    updated_at DATETIME2 DEFAULT SYSDATETIME(),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (order_id) REFERENCES orders(order_id)
);
GO

-- 4. PAYMENT MANAGEMENT -----------------------------------------
CREATE TABLE payments (
    payment_id INT IDENTITY(1,1) PRIMARY KEY,
    order_id INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(15) NOT NULL DEFAULT 'CARD'
        CHECK (payment_method IN ('CARD','COD','BANK_TRANSFER')),
    payment_status VARCHAR(10) NOT NULL DEFAULT 'PENDING'
        CHECK (payment_status IN ('PENDING','SUCCESS','FAILED','REFUNDED')),
    transaction_ref VARCHAR(100),
    paid_at DATETIME2 DEFAULT SYSDATETIME(),
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE
);
GO

-- 5. INVENTORY & SUPPLIER MANAGEMENT ------------------------------
CREATE TABLE suppliers (
    supplier_id INT IDENTITY(1,1) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    contact_email VARCHAR(100),
    phone VARCHAR(20),
    address VARCHAR(255)
);
GO

CREATE TABLE purchase_orders (
    purchase_order_id INT IDENTITY(1,1) PRIMARY KEY,
    supplier_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED','APPROVED','RECEIVED','CANCELLED')),
    ordered_at DATETIME2 DEFAULT SYSDATETIME(),
    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);
GO

-- 6. DELIVERY MANAGEMENT ------------------------------------------
CREATE TABLE deliveries (
    delivery_id INT IDENTITY(1,1) PRIMARY KEY,
    order_id INT NOT NULL,
    delivery_address VARCHAR(255) NOT NULL,
    scheduled_date DATE,
    status VARCHAR(12) NOT NULL DEFAULT 'ASSIGNED'
        CHECK (status IN ('ASSIGNED','IN_TRANSIT','DELIVERED','FAILED')),
    notes VARCHAR(255),
    address_verified BIT DEFAULT 0,
    -- Delivery personnel assignment.
    delivery_person VARCHAR(100),
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE
);
GO

-- ============================================================
-- Application login. application.properties connects as
-- instrument_app / Instrument#2026 - without this login/user the app
-- can't connect at all ("Login failed for user 'instrument_app'").
-- Change the password here AND in application.properties for real use.
-- (Skip this block if you connect with a different account.)
-- ============================================================
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'instrument_app')
BEGIN
    CREATE LOGIN instrument_app WITH PASSWORD = 'Instrument#2026', CHECK_POLICY = OFF;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'instrument_app')
BEGIN
    CREATE USER instrument_app FOR LOGIN instrument_app;
END
GO

ALTER ROLE db_owner ADD MEMBER instrument_app;
GO

-- ============================================================
-- Sample seed data (optional, useful for demo)
-- ============================================================
-- Guarded so running this script a second time cannot create duplicate rows.
IF NOT EXISTS (SELECT 1 FROM categories)
INSERT INTO categories (name, description, image_url) VALUES
('Strings', 'Guitars, violins, ukuleles', '/images/instruments/guitar.svg'),
('Keyboards', 'Pianos, synthesizers', '/images/instruments/keyboard.svg'),
('Percussion', 'Drums, cymbals', '/images/instruments/drums.svg'),
('Wind', 'Flutes, saxophones, trumpets', '/images/instruments/trumpet.svg');
GO

IF NOT EXISTS (SELECT 1 FROM products)
INSERT INTO products (name, description, price, stock_quantity, category_id, image_url) VALUES
('Acoustic Guitar', 'Full-size steel string acoustic guitar', 55000.00, 15, 1, '/images/instruments/guitar.svg'),
('Electric Keyboard 61-key', 'Beginner-friendly digital keyboard', 72500.00, 10, 2, '/images/instruments/keyboard.svg'),
('5-Piece Drum Set', 'Standard drum kit with cymbals', 165000.00, 5, 3, '/images/instruments/drums.svg'),
('Student Trumpet', 'Bb trumpet for beginners', 58000.00, 8, 4, '/images/instruments/trumpet.svg');
GO

-- Password below is BCrypt-hashed "admin123" (log in with admin123, not the hash itself)
-- email_verified = 1 so the demo admin account isn't blocked by the verification flow
INSERT INTO users (full_name, email, password, role, email_verified) VALUES
('Admin User', 'admin@instrumentstore.com', '$2b$10$ZKOHARz4ohvUvARr.Ww5revu9J7vFAahwQayNUK9zvzxYO0ndtRLu', 'ADMIN', 1);
GO

-- One demo login per functional area. Password for every account below
-- is: Manager@123 (bcrypt-hashed, same scheme as the admin account above).
-- Change these passwords after first login in a real deployment.
INSERT INTO users (full_name, email, password, role, email_verified) VALUES
('Customer Relations Manager', 'crm@instrumentstore.com', '$2b$10$qdq5Gbx8fq7f9MlM26rJtuAC/UNJiqAFZPe4Bu3be/j6LzxmxDYLm', 'CUSTOMER_RELATIONS_MANAGER', 1),
('Product Catalog Manager', 'catalog@instrumentstore.com', '$2b$10$qdq5Gbx8fq7f9MlM26rJtuAC/UNJiqAFZPe4Bu3be/j6LzxmxDYLm', 'CATALOG_MANAGER', 1),
('Order & Payment Manager', 'orders@instrumentstore.com', '$2b$10$qdq5Gbx8fq7f9MlM26rJtuAC/UNJiqAFZPe4Bu3be/j6LzxmxDYLm', 'ORDER_PAYMENT_MANAGER', 1),
('Inventory Manager', 'inventory@instrumentstore.com', '$2b$10$qdq5Gbx8fq7f9MlM26rJtuAC/UNJiqAFZPe4Bu3be/j6LzxmxDYLm', 'INVENTORY_MANAGER', 1),
('Delivery Manager', 'delivery@instrumentstore.com', '$2b$10$qdq5Gbx8fq7f9MlM26rJtuAC/UNJiqAFZPe4Bu3be/j6LzxmxDYLm', 'DELIVERY_MANAGER', 1);
GO

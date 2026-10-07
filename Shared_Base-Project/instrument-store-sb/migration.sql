-- Migration: adds the columns introduced for account suspension and
-- review moderation/flagging to an EXISTING database. Run this once
-- against your current database (the one your app is already connected
-- to) - do not re-run database.sql, it would try to recreate tables that
-- already exist.

-- 1. Account suspension (Customer Account & Admin Management)
ALTER TABLE users ADD status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
GO

ALTER TABLE users ADD CONSTRAINT CK_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'));
GO

-- 2. Review moderation + flagging (Customer Review Management)
ALTER TABLE reviews ADD is_hidden BIT DEFAULT 0;
GO

ALTER TABLE reviews ADD flag_count INT DEFAULT 0;
GO

-- 3. One review flag per user (stops one user inflating a review's flag_count)
IF OBJECT_ID('review_flags', 'U') IS NULL
CREATE TABLE review_flags (
    review_id INT NOT NULL,
    user_id INT NOT NULL,
    created_at DATETIME2 DEFAULT SYSDATETIME(),
    PRIMARY KEY (review_id, user_id),
    FOREIGN KEY (review_id) REFERENCES reviews(review_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
GO

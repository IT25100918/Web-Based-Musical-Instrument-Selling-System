package com.instrumentstore.model;

import java.sql.Timestamp;

/**
 * Represents a row in the `reviews` table: one customer's rating + comment
 * on one product. Part of Customer Review Management.
 */
public class Review {
    private int reviewId;
    private int productId;
    private String productName; // joined from products.name, for the admin moderation view only
    private int userId;
    private String reviewerName; // joined from users.full_name, for display
    private int rating; // 1-5
    private String comment;
    private boolean isHidden; // moderation: hidden reviews are excluded from the public product page
    private int flagCount; // how many times other users have reported this review
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public int getReviewId() { return reviewId; }
    public void setReviewId(int reviewId) { this.reviewId = reviewId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public boolean isHidden() { return isHidden; }
    public void setHidden(boolean hidden) { isHidden = hidden; }

    public int getFlagCount() { return flagCount; }
    public void setFlagCount(int flagCount) { this.flagCount = flagCount; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

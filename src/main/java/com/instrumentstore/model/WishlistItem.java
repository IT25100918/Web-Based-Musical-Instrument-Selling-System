package com.instrumentstore.model;

import java.math.BigDecimal;

public class WishlistItem {
    private int wishlistId;
    private int userId;
    private int productId;

    // Joined from products table
    private String productName;
    private BigDecimal price;
    private String imageUrl;
    private int stockQuantity;

    public int getWishlistId() { return wishlistId; }
    public void setWishlistId(int wishlistId) { this.wishlistId = wishlistId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
}

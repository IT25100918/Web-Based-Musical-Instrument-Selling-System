package com.instrumentstore.model;

import java.math.BigDecimal;

public class Product {
    private int productId;
    private String name;
    private String description;
    private BigDecimal price;
    private int stockQuantity;
    private int categoryId;
    private String categoryName;
    private String imageUrl;
    private BigDecimal promoPrice; // nullable - set when a promotional/discounted price is active
    private java.util.List<String> extraImageUrls; // additional gallery images, loaded separately

    public Product() {}

    public Product(String name, String description, BigDecimal price, int stockQuantity, int categoryId) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.categoryId = categoryId;
    }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public BigDecimal getPromoPrice() { return promoPrice; }
    public void setPromoPrice(BigDecimal promoPrice) { this.promoPrice = promoPrice; }

    // True when a promotional price is set and actually lower than the regular price
    public boolean isOnPromotion() {
        return promoPrice != null && price != null && promoPrice.compareTo(price) < 0;
    }

    public java.util.List<String> getExtraImageUrls() { return extraImageUrls; }
    public void setExtraImageUrls(java.util.List<String> extraImageUrls) { this.extraImageUrls = extraImageUrls; }
}

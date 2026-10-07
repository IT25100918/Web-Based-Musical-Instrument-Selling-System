package com.instrumentstore.model;

/**
 * Represents a row in `product_images` - an additional gallery photo for a
 * product, beyond the single primary `products.image_url` (PBI-11).
 */
public class ProductImage {
    private int imageId;
    private int productId;
    private String imageUrl;

    public int getImageId() { return imageId; }
    public void setImageId(int imageId) { this.imageId = imageId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}

package com.instrumentstore.dao;

import com.instrumentstore.model.ProductImage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductImageDAO {

    private final JdbcTemplate jdbcTemplate;

    public ProductImageDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ProductImage> rowMapper = (rs, rowNum) -> {
        ProductImage img = new ProductImage();
        img.setImageId(rs.getInt("image_id"));
        img.setProductId(rs.getInt("product_id"));
        img.setImageUrl(rs.getString("image_url"));
        return img;
    };

    // CREATE: add one gallery image for a product (called once per uploaded file)
    public boolean addImage(int productId, String imageUrl) {
        String sql = "INSERT INTO product_images (product_id, image_url) VALUES (?, ?)";
        return jdbcTemplate.update(sql, productId, imageUrl) > 0;
    }

    // READ: all gallery images for one product, for the product detail page and edit form
    public List<ProductImage> getByProductId(int productId) {
        String sql = "SELECT * FROM product_images WHERE product_id = ? ORDER BY image_id";
        return jdbcTemplate.query(sql, rowMapper, productId);
    }

    // DELETE: remove one gallery image (e.g. admin removing a bad photo). Scoped to the product
    // it's supposed to belong to, so the "Delete" button on one product's edit page can't be used
    // (by changing the imageId in the request) to delete another product's photo.
    public boolean deleteImage(int imageId, int productId) {
        String sql = "DELETE FROM product_images WHERE image_id = ? AND product_id = ?";
        return jdbcTemplate.update(sql, imageId, productId) > 0;
    }

    // DELETE: all gallery images for a product (used when the product itself is deleted, though
    // the FK's ON DELETE CASCADE already handles this - kept for explicit admin "clear gallery" use)
    public boolean deleteAllForProduct(int productId) {
        String sql = "DELETE FROM product_images WHERE product_id = ?";
        return jdbcTemplate.update(sql, productId) >= 0;
    }
}

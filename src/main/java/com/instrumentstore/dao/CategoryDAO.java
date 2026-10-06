package com.instrumentstore.dao;

import com.instrumentstore.model.Category;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CategoryDAO {

    private final JdbcTemplate jdbcTemplate;

    public CategoryDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Category> categoryRowMapper = (rs, rowNum) -> {
        Category c = new Category();
        c.setCategoryId(rs.getInt("category_id"));
        c.setName(rs.getString("name"));
        c.setDescription(rs.getString("description"));
        c.setImageUrl(rs.getString("image_url"));
        return c;
    };

    // CREATE
    public boolean addCategory(Category c) {
        String sql = "INSERT INTO categories (name, description, image_url) VALUES (?, ?, ?)";
        return jdbcTemplate.update(sql, c.getName(), c.getDescription(), c.getImageUrl()) > 0;
    }

    // READ: all categories
    public List<Category> getAllCategories() {
        String sql = "SELECT * FROM categories ORDER BY name";
        return jdbcTemplate.query(sql, categoryRowMapper);
    }

    // READ: one category
    public Category getCategoryById(int categoryId) {
        String sql = "SELECT * FROM categories WHERE category_id = ?";
        List<Category> results = jdbcTemplate.query(sql, categoryRowMapper, categoryId);
        return results.isEmpty() ? null : results.get(0);
    }

    // UPDATE
    public boolean updateCategory(Category c) {
        String sql = "UPDATE categories SET name = ?, description = ?, image_url = ? WHERE category_id = ?";
        return jdbcTemplate.update(sql, c.getName(), c.getDescription(), c.getImageUrl(), c.getCategoryId()) > 0;
    }

    // DELETE - returns false if it doesn't exist or is still used by products (foreign key)
    public boolean deleteCategory(int categoryId) {
        String sql = "DELETE FROM categories WHERE category_id = ?";
        try {
            return jdbcTemplate.update(sql, categoryId) > 0;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}

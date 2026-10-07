package com.instrumentstore.controller;

import com.instrumentstore.dao.CategoryDAO;
import com.instrumentstore.dao.ProductDAO;
import com.instrumentstore.model.Product;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    private final CategoryDAO categoryDAO;
    private final ProductDAO productDAO;

    public HomeController(CategoryDAO categoryDAO, ProductDAO productDAO) {
        this.categoryDAO = categoryDAO;
        this.productDAO = productDAO;
    }

    // Storefront landing page - "We Love Music" style home page
    @GetMapping("/")
    public String home(HttpSession session, Model model) {
        List<Product> allProducts = productDAO.getAllProducts();

        // Show up to 8 products as "Featured Products" on the home page
        List<Product> featured = allProducts.size() > 8 ? allProducts.subList(0, 8) : allProducts;

        // Show the next 8 (or wrap back to the start if there aren't enough) as "Trending Gear"
        List<Product> trending;
        if (allProducts.size() > 8) {
            int end = Math.min(allProducts.size(), 16);
            trending = allProducts.subList(8, end);
        } else {
            trending = allProducts;
        }

        model.addAttribute("categories", categoryDAO.getAllCategories());
        model.addAttribute("featuredProducts", featured);
        model.addAttribute("trendingProducts", trending);
        model.addAttribute("loggedInUser", session.getAttribute("user"));
        return "home"; // renders templates/home.html
    }

    // Landing page for staff logins after login. Which stat-cards are shown
    // depends on the session role - ADMIN sees everything, each of the six
    // manager roles sees only their own module (they're blocked by
    // AdminAuthInterceptor from the others anyway, but hiding the links
    // avoids a confusing "redirected back" surprise on click).
    @GetMapping("/admin/dashboard")
    public String adminDashboard(HttpSession session, Model model) {
        model.addAttribute("role", session.getAttribute("role"));
        return "admin-dashboard"; // renders templates/admin-dashboard.html
    }
}
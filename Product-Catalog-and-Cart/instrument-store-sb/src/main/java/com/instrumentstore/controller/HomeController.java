package com.instrumentstore.controller;

import com.instrumentstore.dao.CategoryDAO;
import com.instrumentstore.dao.ProductDAO;
import com.instrumentstore.model.Product;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.instrumentstore.model.User;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

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
        Object roleAttr = session.getAttribute("role");
        String role = roleAttr != null ? roleAttr.toString() : "";
        Object userAttr = session.getAttribute("user");
        String name = "Admin";
        String status = "ACTIVE";
        if (userAttr instanceof User u) {
            if (u.getFullName() != null && !u.getFullName().isBlank()) name = u.getFullName().trim();
            if (u.getStatus() != null) status = u.getStatus();
        }
        int hour = LocalTime.now().getHour();
        String greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";

        Map<String, String[]> info = Map.of(
                "ADMIN", new String[]{"ADMIN", "FULL ACCESS", "Oversees every management section: catalogue, sales, stock, deliveries and customers."},
                "CATALOG_MANAGER", new String[]{"STAFF", "CATALOG MANAGER", "Manages products, product images and categories."},
                "ORDER_PAYMENT_MANAGER", new String[]{"STAFF", "ORDER & PAYMENT MANAGER", "Manages orders, payments, refunds and the sales and financial reports."},
                "INVENTORY_MANAGER", new String[]{"STAFF", "INVENTORY MANAGER", "Manages stock levels, suppliers, purchase orders and the inventory report."},
                "DELIVERY_MANAGER", new String[]{"STAFF", "DELIVERY MANAGER", "Manages deliveries and tracks shipments to customers."},
                "CUSTOMER_RELATIONS_MANAGER", new String[]{"STAFF", "CUSTOMER RELATIONS MANAGER", "Manages customers, complaints and product reviews."});
        String[] r = info.getOrDefault(role, new String[]{"STAFF", role, "Staff account."});

        // The team member who looks after each area, shown on the banner
        Map<String, String> owners = Map.of(
                "ADMIN", "THIRANDI",
                "CATALOG_MANAGER", "AVANI",
                "ORDER_PAYMENT_MANAGER", "SHEHARA",
                "INVENTORY_MANAGER", "THARINDU",
                "DELIVERY_MANAGER", "GAGANI",
                "CUSTOMER_RELATIONS_MANAGER", "THINARA");

        model.addAttribute("role", role);
        model.addAttribute("greeting", greeting);
        model.addAttribute("staffName", name);
        model.addAttribute("staffInitial", name.substring(0, 1).toUpperCase());
        model.addAttribute("staffType", r[0]);
        model.addAttribute("staffArea", r[1]);
        model.addAttribute("staffBlurb", r[2]);
        model.addAttribute("staffOwner", owners.get(role));
        model.addAttribute("staffStatus", status);
        return "admin-dashboard"; // renders templates/admin-dashboard.html
    }
}
package com.instrumentstore.controller;

import com.instrumentstore.dao.WishlistDAO;
import com.instrumentstore.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WishlistController {

    private final WishlistDAO wishlistDAO;

    public WishlistController(WishlistDAO wishlistDAO) {
        this.wishlistDAO = wishlistDAO;
    }

    private User currentUser(HttpSession session) {
        return (User) session.getAttribute("user");
    }

    // GET /wishlist -> view saved items
    @GetMapping("/wishlist")
    public String viewWishlist(HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("items", wishlistDAO.getWishlistByUser(user.getUserId()));
        return "wishlist";
    }

    // POST /wishlist/add -> save a product
    @PostMapping("/wishlist/add")
    public String addToWishlist(@RequestParam int productId, HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        wishlistDAO.addToWishlist(user.getUserId(), productId);
        return "redirect:/products";
    }

    // POST /wishlist/remove -> remove a saved product
    @PostMapping("/wishlist/remove")
    public String removeFromWishlist(@RequestParam int wishlistId, HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        wishlistDAO.removeFromWishlist(user.getUserId(), wishlistId);
        return "redirect:/wishlist";
    }
}

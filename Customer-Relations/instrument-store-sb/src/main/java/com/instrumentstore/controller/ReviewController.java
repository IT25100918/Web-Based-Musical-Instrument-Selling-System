package com.instrumentstore.controller;

import com.instrumentstore.dao.OrderDAO;
import com.instrumentstore.dao.ReviewDAO;
import com.instrumentstore.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Customer Review Management: this is its own function, separate from
 * Customer Account & Admin Management. It has three sides:
 *
 *  - Customer self-service: write, edit, or delete a review on a product
 *    they've actually purchased. Reviews themselves are displayed on
 *    ProductCatalogShoppingCartController's product-detail page.
 *  - Community flagging: any logged-in user (not just staff) can report a
 *    review they think is abusive or spam.
 *  - Admin/Customer Relations Manager moderation: view every review
 *    (worst-flagged first) and hide/unhide one without deleting it.
 */
@Controller
public class ReviewController {

    private static final int MAX_REVIEW_COMMENT = 1000;

    private final ReviewDAO reviewDAO;
    private final OrderDAO orderDAO;

    public ReviewController(ReviewDAO reviewDAO, OrderDAO orderDAO) {
        this.reviewDAO = reviewDAO;
        this.orderDAO = orderDAO;
    }

    private User currentUser(HttpSession session) {
        return (User) session.getAttribute("user");
    }

    // ============================================================
    // Customer self-service
    // ============================================================

    // POST /products/{productId}/reviews -> submit a new review, or edit an existing one
    @PostMapping("/products/{productId}/reviews")
    public String submitReview(@PathVariable int productId,
                                @RequestParam int rating,
                                @RequestParam(required = false) String comment,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        if (!orderDAO.hasUserPurchasedProduct(user.getUserId(), productId)) {
            redirectAttributes.addFlashAttribute("reviewError", "Only customers who purchased this product can leave a review.");
            return "redirect:/products/" + productId;
        }

        if (rating < 1 || rating > 5) {
            redirectAttributes.addFlashAttribute("reviewError", "Rating must be between 1 and 5 stars.");
            return "redirect:/products/" + productId;
        }

        String cleanComment = comment == null ? null : comment.trim();
        if (cleanComment != null && cleanComment.length() > MAX_REVIEW_COMMENT) {
            redirectAttributes.addFlashAttribute("reviewError", "Your review is too long (max " + MAX_REVIEW_COMMENT + " characters).");
            return "redirect:/products/" + productId;
        }

        reviewDAO.addOrUpdateReview(productId, user.getUserId(), rating, cleanComment);
        redirectAttributes.addFlashAttribute("reviewMessage", "Thanks for your review!");
        return "redirect:/products/" + productId;
    }

    // POST /products/{productId}/reviews/delete -> remove your own review
    @PostMapping("/products/{productId}/reviews/delete")
    public String deleteReview(@PathVariable int productId,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        reviewDAO.deleteReview(productId, user.getUserId());
        redirectAttributes.addFlashAttribute("reviewMessage", "Your review has been removed.");
        return "redirect:/products/" + productId;
    }

    // ============================================================
    // Community flagging - any logged-in user, not just the author
    // ============================================================

    // POST /products/{productId}/reviews/{reviewId}/flag -> report a review as abusive/spam
    @PostMapping("/products/{productId}/reviews/{reviewId}/flag")
    public String flagReview(@PathVariable int productId,
                              @PathVariable int reviewId,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        if (reviewDAO.flagReview(reviewId, user.getUserId())) {
            redirectAttributes.addFlashAttribute("reviewMessage", "Thanks - we've flagged this review for our team to look at.");
        } else {
            redirectAttributes.addFlashAttribute("reviewError", "You have already flagged this review (or it's your own).");
        }
        return "redirect:/products/" + productId;
    }

    // ============================================================
    // Admin / Customer Relations Manager moderation
    // ============================================================

    // GET /admin/reviews -> every review, worst-flagged first
    @GetMapping("/admin/reviews")
    public String listReviewsForAdmin(Model model) {
        model.addAttribute("reviews", reviewDAO.getAllReviewsForAdmin());
        return "admin-reviews";
    }

    // POST /admin/reviews/{id}/hide -> hide a review from the public product page
    @PostMapping("/admin/reviews/{id}/hide")
    public String hideReview(@PathVariable int id) {
        reviewDAO.hideReview(id);
        return "redirect:/admin/reviews";
    }

    // POST /admin/reviews/{id}/unhide -> restore a previously hidden review
    @PostMapping("/admin/reviews/{id}/unhide")
    public String unhideReview(@PathVariable int id) {
        reviewDAO.unhideReview(id);
        return "redirect:/admin/reviews";
    }
}

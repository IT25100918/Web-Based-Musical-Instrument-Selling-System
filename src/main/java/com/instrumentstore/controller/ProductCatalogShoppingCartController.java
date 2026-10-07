package com.instrumentstore.controller;

import com.instrumentstore.dao.CartDAO;
import com.instrumentstore.dao.CategoryDAO;
import com.instrumentstore.dao.OrderDAO;
import com.instrumentstore.dao.ProductDAO;
import com.instrumentstore.dao.ProductImageDAO;
import com.instrumentstore.dao.ReviewDAO;
import com.instrumentstore.model.CartItem;
import com.instrumentstore.model.Category;
import com.instrumentstore.model.Product;
import com.instrumentstore.model.Review;
import com.instrumentstore.model.User;
import com.instrumentstore.service.ImageStorageService;
import com.instrumentstore.service.BusinessException;
import com.instrumentstore.service.OrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Product Catalog and Shopping Cart Management: this is ONE function
 * covering everything about browsing, presenting and buying products.
 * It has three sides:
 *
 *  - Customer-facing catalog: browse/search products, product detail page
 *    (including the review summary shown there - full review CRUD is its
 *    own Customer Review Management function, see ReviewController).
 *  - Admin/Catalog Manager side: create/update/delete products (with image
 *    uploads and gallery photos) and categories.
 *  - Shopping cart: add/update/remove items and checkout into an Order
 *    (Order and Payment Management then takes over from there).
 */
@Controller
public class ProductCatalogShoppingCartController {

    private final ProductDAO productDAO;
    private final CategoryDAO categoryDAO;
    private final ReviewDAO reviewDAO;
    private final OrderDAO orderDAO;
    private final ProductImageDAO productImageDAO;
    private final ImageStorageService imageStorageService;
    private final CartDAO cartDAO;
    private final OrderService orderService;

    public ProductCatalogShoppingCartController(ProductDAO productDAO, CategoryDAO categoryDAO, ReviewDAO reviewDAO,
                                                 OrderDAO orderDAO, ProductImageDAO productImageDAO,
                                                 ImageStorageService imageStorageService,
                                                 CartDAO cartDAO, OrderService orderService) {
        this.productDAO = productDAO;
        this.categoryDAO = categoryDAO;
        this.reviewDAO = reviewDAO;
        this.orderDAO = orderDAO;
        this.productImageDAO = productImageDAO;
        this.imageStorageService = imageStorageService;
        this.cartDAO = cartDAO;
        this.orderService = orderService;
    }

    // Helper: returns the logged-in User, or null if nobody's logged in
    private User currentUser(HttpSession session) {
        return (User) session.getAttribute("user");
    }

    // ============================================================
    // Customer-facing catalog: browse / search / product detail
    // ============================================================

    // GET /products                    -> show all products
    // GET /products?keyword=..         -> search by name
    // GET /products?categoryId=..      -> filter by category
    @GetMapping("/products")
    public String listProducts(@RequestParam(required = false) String keyword,
                                @RequestParam(required = false) Integer categoryId,
                                HttpSession session,
                                Model model) {

        List<Product> products;

        if ((keyword != null && !keyword.isBlank()) || categoryId != null) {
            String searchKeyword = (keyword != null) ? keyword : "";
            products = productDAO.searchProducts(searchKeyword, categoryId);
        } else {
            products = productDAO.getAllProducts();
        }

        model.addAttribute("products", products);
        model.addAttribute("categories", categoryDAO.getAllCategories());
        model.addAttribute("loggedInUser", session.getAttribute("user"));
        return "products"; // renders templates/products.html
    }

    // GET /products/{id} -> single product detail page
    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable int id, HttpSession session, Model model) {
        Product product = productDAO.getProductById(id);
        if (product == null) {
            return "redirect:/products";
        }
        model.addAttribute("product", product);
        model.addAttribute("galleryImages", productImageDAO.getByProductId(id));
        User loggedInUser = currentUser(session);
        model.addAttribute("loggedInUser", loggedInUser);

        // Customer Review Management: show existing reviews plus whether the
        // current visitor is allowed to write one themselves.
        List<Review> reviews = reviewDAO.getReviewsForProduct(id);
        model.addAttribute("reviews", reviews);
        Double averageRating = reviewDAO.getAverageRating(id);
        model.addAttribute("averageRating", averageRating);
        model.addAttribute("reviewCount", reviews.size());
        // Rounded to the nearest whole star for the summary display, so the
        // template can do a plain int <= int comparison instead of formatting math.
        model.addAttribute("ratingStars", averageRating == null ? 0 : (int) Math.round(averageRating));

        if (loggedInUser != null) {
            boolean canReview = orderDAO.hasUserPurchasedProduct(loggedInUser.getUserId(), id);
            model.addAttribute("canReview", canReview);
            model.addAttribute("myReview", reviewDAO.getUserReviewForProduct(loggedInUser.getUserId(), id));
        }

        return "product-detail";
    }

    // ============================================================
    // Admin / Catalog Manager side: products
    // ============================================================

    @GetMapping("/admin/products")
    public String adminListProducts(Model model) {
        model.addAttribute("products", productDAO.getAllProducts());
        return "admin-products";
    }

    @GetMapping("/admin/products/new")
    public String showAddProductForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categoryDAO.getAllCategories());
        model.addAttribute("galleryImages", java.util.Collections.emptyList());
        return "product-form";
    }

    @GetMapping("/admin/products/edit/{id}")
    public String showEditProductForm(@PathVariable int id, Model model) {
        Product product = productDAO.getProductById(id);
        if (product == null) {
            return "redirect:/admin/products"; // unknown id used to crash the template
        }
        model.addAttribute("product", product);
        model.addAttribute("categories", categoryDAO.getAllCategories());
        model.addAttribute("galleryImages", productImageDAO.getByProductId(id));
        return "product-form";
    }

    @PostMapping("/admin/products")
    public String saveProduct(@RequestParam(required = false) Integer productId,
                               @RequestParam String name,
                               @RequestParam(required = false) String description,
                               @RequestParam BigDecimal price,
                               @RequestParam(required = false, defaultValue = "0") int stockQuantity, // only used for NEW products
                               @RequestParam int categoryId,
                               @RequestParam(required = false) BigDecimal promoPrice,
                               @RequestParam(required = false) String existingImageUrl,
                               @RequestParam(required = false) MultipartFile imageFile,
                               @RequestParam(required = false) MultipartFile[] galleryFiles,
                               Model model) throws IOException {

        Product product = new Product();
        if (productId != null) {
            product.setProductId(productId);
        }
        product.setName(name.trim());
        product.setDescription(description);
        product.setPrice(price);
        product.setStockQuantity(stockQuantity);
        product.setCategoryId(categoryId);
        product.setImageUrl(existingImageUrl);
        // Promotional price (PBI-12): only stored when it's actually lower than the regular price
        product.setPromoPrice(promoPrice != null && promoPrice.compareTo(price) < 0 ? promoPrice : null);

        // Server-side validation (the HTML form's checks can be bypassed)
        String validationError = null;
        if (product.getName().isEmpty() || product.getName().length() > 150) {
            validationError = "Product name is required (max 150 characters).";
        } else if (price.signum() < 0 || price.compareTo(new BigDecimal("99999999.99")) > 0) {
            validationError = "Price must be between 0 and 99,999,999.99.";
        } else if (promoPrice != null && promoPrice.signum() < 0) {
            validationError = "Promotional price can't be negative.";
        } else if (stockQuantity < 0) {
            validationError = "Stock quantity can't be negative.";
        } else if (categoryDAO.getCategoryById(categoryId) == null) {
            validationError = "Please choose a valid category.";
        } else if (existingImageUrl != null && existingImageUrl.length() > 255) {
            validationError = "Image URL is too long (max 255 characters).";
        }

        // Keep the old image unless a new file was actually chosen
        if (validationError == null && imageFile != null && !imageFile.isEmpty()) {
            try {
                product.setImageUrl(imageStorageService.storeImageFile(imageFile));
            } catch (IllegalArgumentException e) {
                validationError = e.getMessage();
            }
        }
        // Check gallery files up-front too, so a bad file doesn't leave a half-saved product
        if (validationError == null && galleryFiles != null) {
            for (MultipartFile file : galleryFiles) {
                if (file != null && !file.isEmpty() && !isImage(file)) {
                    validationError = "Only JPG, PNG, GIF or WebP image files can be uploaded.";
                    break;
                }
            }
        }

        if (validationError != null) {
            return showProductFormWithError(model, product, validationError);
        }

        boolean success;
        int savedProductId;
        if (productId != null) {
            success = productDAO.updateProduct(product);
            savedProductId = productId;
        } else {
            savedProductId = productDAO.addProductReturningId(product);
            success = savedProductId > 0;
        }

        // Additional gallery photos (PBI-11): store every extra file the admin selected
        if (success && galleryFiles != null) {
            for (MultipartFile file : galleryFiles) {
                if (file != null && !file.isEmpty()) {
                    String url = imageStorageService.storeImageFile(file);
                    productImageDAO.addImage(savedProductId, url);
                }
            }
        }

        if (success) {
            return "redirect:/admin/products";
        } else {
            return showProductFormWithError(model, product, "Could not save product. Please check the values and try again.");
        }
    }

    private boolean isImage(MultipartFile file) {
        String n = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);
        String t = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        return t.startsWith("image/") && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".gif") || n.endsWith(".webp"));
    }

    // Re-shows the product form with an error, keeping the gallery of an existing product visible
    private String showProductFormWithError(Model model, Product product, String error) {
        model.addAttribute("error", error);
        model.addAttribute("product", product);
        model.addAttribute("categories", categoryDAO.getAllCategories());
        model.addAttribute("galleryImages",
                product.getProductId() != 0 ? productImageDAO.getByProductId(product.getProductId())
                                            : java.util.Collections.emptyList());
        return "product-form";
    }

    // POST /admin/products/gallery/{imageId}/delete -> remove one gallery photo
    @PostMapping("/admin/products/gallery/{imageId}/delete")
    public String deleteGalleryImage(@PathVariable int imageId, @RequestParam int productId) {
        productImageDAO.deleteImage(imageId, productId);
        return "redirect:/admin/products/edit/" + productId;
    }

    // POST (not GET): a state-changing action must not be triggerable by a plain link/image/prefetch
    @PostMapping("/admin/products/delete/{id}")
    public String deleteProduct(@PathVariable int id, RedirectAttributes redirectAttributes) {
        boolean deleted = productDAO.deleteProduct(id);
        if (!deleted) {
            redirectAttributes.addFlashAttribute("error",
                    "This product can't be deleted because it's linked to existing orders, cart items, reviews, or purchase orders. " +
                    "Consider setting its stock to 0 instead of deleting it.");
        }
        return "redirect:/admin/products";
    }

    // ============================================================
    // Admin / Catalog Manager side: categories
    // ============================================================

    @GetMapping("/admin/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryDAO.getAllCategories());
        return "admin-categories";
    }

    @GetMapping("/admin/categories/new")
    public String showAddCategoryForm(Model model) {
        model.addAttribute("category", new Category());
        return "category-form";
    }

    @GetMapping("/admin/categories/edit/{id}")
    public String showEditCategoryForm(@PathVariable int id, Model model) {
        Category category = categoryDAO.getCategoryById(id);
        if (category == null) {
            return "redirect:/admin/categories";
        }
        model.addAttribute("category", category);
        return "category-form";
    }

    @PostMapping("/admin/categories")
    public String saveCategory(@RequestParam(required = false) Integer categoryId,
                                @RequestParam String name,
                                @RequestParam(required = false) String description,
                                @RequestParam(required = false) String existingImageUrl,
                                @RequestParam(required = false) MultipartFile imageFile,
                                Model model) throws IOException {
        Category category = new Category();
        if (categoryId != null) {
            category.setCategoryId(categoryId);
        }
        category.setName(name.trim());
        category.setDescription(description);
        category.setImageUrl(existingImageUrl);

        String error = null;
        if (category.getName().isEmpty() || category.getName().length() > 100) {
            error = "Category name is required (max 100 characters).";
        } else if (description != null && description.length() > 255) {
            error = "Description is too long (max 255 characters).";
        } else if (imageFile != null && !imageFile.isEmpty()) {
            // Keep the old image unless a new file was actually chosen
            try {
                category.setImageUrl(imageStorageService.storeImageFile(imageFile));
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
            }
        }
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("category", category);
            return "category-form";
        }

        if (categoryId != null) {
            categoryDAO.updateCategory(category);
        } else {
            categoryDAO.addCategory(category);
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/admin/categories/delete/{id}")
    public String deleteCategory(@PathVariable int id, RedirectAttributes redirectAttributes) {
        if (!categoryDAO.deleteCategory(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "This category can't be deleted because products still belong to it. Reassign or delete those products first.");
        }
        return "redirect:/admin/categories";
    }

    // ============================================================
    // Shopping cart (requires login - every action redirects to
    // /login if nobody's logged in)
    // ============================================================

    // GET /cart -> view cart contents
    @GetMapping("/cart")
    public String viewCart(HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        List<CartItem> items = cartDAO.getCartItems(user.getUserId());
        model.addAttribute("items", items);
        model.addAttribute("total", cartDAO.getCartTotal(user.getUserId()));
        return "cart";
    }

    // POST /cart -> add a product (called from the "Add to Cart" button on products.html)
    @PostMapping("/cart")
    public String addToCart(@RequestParam int productId,
                             @RequestParam(defaultValue = "1") int quantity,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        // Rejects zero/negative quantities (which would otherwise create negative order totals),
        // unknown products and out-of-stock products.
        if (!cartDAO.addToCart(user.getUserId(), productId, quantity)) {
            redirectAttributes.addFlashAttribute("error", "That item couldn't be added - it may be out of stock or unavailable.");
        }
        return "redirect:/cart";
    }

    // POST /cart/update -> change quantity of one line
    @PostMapping("/cart/update")
    public String updateQuantity(@RequestParam int cartItemId,
                                  @RequestParam int quantity,
                                  HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (quantity <= 0) {
            cartDAO.removeItem(user.getUserId(), cartItemId);
        } else {
            cartDAO.updateQuantity(user.getUserId(), cartItemId, quantity);
        }
        return "redirect:/cart";
    }

    // POST /cart/remove -> remove one line entirely
    @PostMapping("/cart/remove")
    public String removeItem(@RequestParam int cartItemId, HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        cartDAO.removeItem(user.getUserId(), cartItemId);
        return "redirect:/cart";
    }

    // POST /cart/checkout -> turns the cart into an Order (stock check, stock decrement, order creation
    // and cart clearing all happen in one transaction inside OrderService)
    @PostMapping("/cart/checkout")
    public String checkout(HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            int orderId = orderService.checkout(user.getUserId());
            return "redirect:/payment/" + orderId;
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("items", cartDAO.getCartItems(user.getUserId()));
            model.addAttribute("total", cartDAO.getCartTotal(user.getUserId()));
            return "cart";
        }
    }
}

package com.instrumentstore.controller;

import com.instrumentstore.config.AccountSessionInterceptor;
import com.instrumentstore.dao.ComplaintDAO;
import com.instrumentstore.dao.EmailVerificationTokenDAO;
import com.instrumentstore.dao.OrderDAO;
import com.instrumentstore.dao.UserDAO;
import com.instrumentstore.model.EmailVerificationToken;
import com.instrumentstore.model.Order;
import com.instrumentstore.model.User;
import com.instrumentstore.service.AccountVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Customer Account & Admin Management: this is ONE function covering every
 * place a customer account gets created, authenticated, or maintained -
 * whether the customer is doing it themselves or an admin (Customer
 * Relations Manager) is doing it on their behalf. It has three sides:
 *
 *  - Customer self-service: register, login, logout, email verification,
 *    view/edit profile, change password, delete own account, and
 *    submitting/tracking complaints or return requests (PBI-04).
 *  - Admin/Customer Relations Manager side: dashboard of flagged accounts
 *    (unverified email, repeated password resets, incomplete profile info)
 *    and correcting or manually verifying them directly (PBI-05, PBI-06),
 *    plus responding to and resolving customer complaints (PBI-08).
 *
 * (Forgot-password/reset-by-email is a separate minor function - see
 * PasswordResetController. Product reviews are their own function - see
 * ReviewController.)
 */
@Controller
public class CustomerAdminManagementController {

    private static final java.util.Set<String> VALID_COMPLAINT_STATUSES = java.util.Set.of("OPEN", "IN_PROGRESS", "RESOLVED");

    private final UserDAO userDAO;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenDAO verificationTokenDAO;
    private final AccountVerificationService verificationService;
    private final ComplaintDAO complaintDAO;
    private final OrderDAO orderDAO;

    public CustomerAdminManagementController(UserDAO userDAO, PasswordEncoder passwordEncoder,
                                              EmailVerificationTokenDAO verificationTokenDAO,
                                              AccountVerificationService verificationService,
                                              ComplaintDAO complaintDAO, OrderDAO orderDAO) {
        this.userDAO = userDAO;
        this.passwordEncoder = passwordEncoder;
        this.verificationTokenDAO = verificationTokenDAO;
        this.verificationService = verificationService;
        this.complaintDAO = complaintDAO;
        this.orderDAO = orderDAO;
    }

    private User currentUser(HttpSession session) {
        return (User) session.getAttribute("user");
    }

    // ============================================================
    // Customer self-service: register / login / logout / verify
    // ============================================================

    // GET /register -> show the registration form
    @GetMapping("/register")
    public String showRegisterForm() {
        return "register"; // renders templates/register.html
    }

    // POST /register -> process the form
    @PostMapping("/register")
    public String register(@RequestParam String fullName,
                            @RequestParam String email,
                            @RequestParam String password,
                            @RequestParam(required = false) String phone,
                            @RequestParam(required = false) String address,
                            HttpServletRequest request,
                            Model model) {

        fullName = fullName.trim();
        email = email.trim();

        if (fullName.isBlank() || email.isBlank() || password.length() < 6) {
            model.addAttribute("error", "Please fill all required fields. Password must be at least 6 characters.");
            return "register";
        }
        // Column sizes in the users table - longer values would fail with a database error (HTTP 500)
        if (fullName.length() > 100 || email.length() > 100
                || (phone != null && phone.length() > 20) || (address != null && address.length() > 255)) {
            model.addAttribute("error", "One of the fields is too long (name/email max 100, phone max 20, address max 255 characters).");
            return "register";
        }

        if (userDAO.findByEmail(email) != null) {
            model.addAttribute("error", "An account with this email already exists.");
            return "register";
        }

        // Hash the password before it ever touches the database - the raw
        // password is never stored, only this one-way hash.
        String hashedPassword = passwordEncoder.encode(password);

        User newUser = new User(fullName, email, hashedPassword);
        newUser.setPhone(phone);
        newUser.setAddress(address);

        boolean success = userDAO.registerUser(newUser);

        if (success) {
            verificationService.sendVerificationEmail(userDAO.findByEmail(email), request);
            model.addAttribute("message",
                    "Registration successful! We've emailed you a verification link - please log in, then verify your address from your profile.");
            return "login";
        } else {
            model.addAttribute("error", "Registration failed. Please try again.");
            return "register";
        }
    }

    // GET /verify-email?token=... -> confirms the account tied to this one-time link
    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token, RedirectAttributes redirectAttributes) {
        EmailVerificationToken verificationToken = verificationTokenDAO.findByToken(token);

        if (verificationToken == null || verificationToken.isExpired()) {
            redirectAttributes.addFlashAttribute("error",
                    "This verification link is invalid or has expired. You can request a new one from your profile.");
            return "redirect:/login";
        }

        userDAO.markEmailVerified(verificationToken.getUserId());
        verificationTokenDAO.deleteTokensForUser(verificationToken.getUserId());

        redirectAttributes.addFlashAttribute("message", "Your email address has been verified. Thanks!");
        return "redirect:/login";
    }

    // GET /login -> show the login form
    @GetMapping("/login")
    public String showLoginForm(@RequestParam(required = false) String reason, Model model) {
        // Set by AccountSessionInterceptor when it ends a session
        if ("suspended".equals(reason)) {
            model.addAttribute("error", "This account has been suspended. Contact support for help.");
        } else if ("password".equals(reason)) {
            model.addAttribute("error", "Your password was changed, so you have been logged out. Please log in again.");
        }
        return "login";
    }

    // POST /login -> validate credentials and start a session
    @PostMapping("/login")
    public String login(@RequestParam String email,
                         @RequestParam String password,
                         HttpServletRequest request,
                         Model model) {

        User user = userDAO.findByEmail(email.trim());

        // matches() re-hashes the entered password with the same salt stored
        // in user.getPassword() and compares - the stored hash is never reversed.
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {

            // Account suspension: block the login here, right after the password check succeeds
            // and before any session is created, so a suspended account gets no session at all.
            if ("SUSPENDED".equals(user.getStatus())) {
                model.addAttribute("error", "This account has been suspended. Contact support for help.");
                return "login";
            }

            HttpSession session = request.getSession();
            // New session id on login (prevents session fixation: an id planted before login
            // must not become an authenticated session)
            request.changeSessionId();
            // Remember a fingerprint of the password so AccountSessionInterceptor can end this session
            // if the password is later changed or reset (the hash itself is not kept)
            session.setAttribute(AccountSessionInterceptor.FINGERPRINT_ATTR,
                    AccountSessionInterceptor.fingerprint(user.getPassword()));
            // Don't keep the password hash in the session
            user.setPassword(null);
            session.setAttribute("user", user);
            session.setAttribute("role", user.getRole());

            // Any staff role (the overall ADMIN, or one of the six functional
            // manager roles) lands on the admin dashboard; AdminAuthInterceptor
            // then restricts which sections of it each role can actually use.
            java.util.Set<String> staffRoles = java.util.Set.of(
                    "ADMIN", "CUSTOMER_RELATIONS_MANAGER", "CATALOG_MANAGER",
                    "ORDER_PAYMENT_MANAGER", "INVENTORY_MANAGER", "DELIVERY_MANAGER");
            if (staffRoles.contains(user.getRole())) {
                return "redirect:/admin/dashboard";
            }
            return "redirect:/products";
        } else {
            model.addAttribute("error", "Invalid email or password.");
            return "login";
        }
    }

    // POST /logout -> end the session (POST, not GET: a link or image on another site must not be able to log someone out)
    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login";
    }

    // ============================================================
    // Customer self-service: profile / password / verification / delete
    // ============================================================

    // GET /profile -> view + edit profile
    @GetMapping("/profile")
    public String viewProfile(HttpSession session, Model model) {
        User sessionUser = currentUser(session);
        if (sessionUser == null) {
            return "redirect:/login";
        }
        // Re-fetch fresh data from the DB in case it changed since login
        User user = userDAO.findById(sessionUser.getUserId());
        model.addAttribute("user", user);
        return "profile";
    }

    // POST /profile -> save profile edits
    @PostMapping("/profile")
    public String updateProfile(@RequestParam String fullName,
                                 @RequestParam(required = false) String phone,
                                 @RequestParam(required = false) String address,
                                 HttpSession session, Model model) {
        User sessionUser = currentUser(session);
        if (sessionUser == null) {
            return "redirect:/login";
        }

        User updated = userDAO.findById(sessionUser.getUserId());
        if (updated == null) { // account no longer exists
            session.invalidate();
            return "redirect:/login";
        }

        fullName = fullName.trim();
        if (fullName.isBlank() || fullName.length() > 100
                || (phone != null && phone.length() > 20) || (address != null && address.length() > 255)) {
            model.addAttribute("user", updated);
            model.addAttribute("error", "Please enter your name (max 100 characters), phone (max 20) and address (max 255).");
            return "profile";
        }

        updated.setFullName(fullName);
        updated.setPhone(phone);
        updated.setAddress(address);

        boolean success = userDAO.updateProfile(updated);

        // Keep the session copy in sync so the header/name updates immediately (without the password hash)
        updated.setPassword(null);
        session.setAttribute("user", updated);

        model.addAttribute("user", updated);
        model.addAttribute(success ? "message" : "error",
                success ? "Profile updated." : "Could not update profile.");
        return "profile";
    }

    // GET /profile/password -> show change password form
    @GetMapping("/profile/password")
    public String showChangePasswordForm(HttpSession session) {
        if (currentUser(session) == null) {
            return "redirect:/login";
        }
        return "change-password";
    }

    // POST /profile/password -> process password change
    @PostMapping("/profile/password")
    public String changePassword(@RequestParam String currentPassword,
                                  @RequestParam String newPassword,
                                  HttpSession session, Model model) {
        User sessionUser = currentUser(session);
        if (sessionUser == null) {
            return "redirect:/login";
        }

        User user = userDAO.findById(sessionUser.getUserId());

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            model.addAttribute("error", "Current password is incorrect.");
            return "change-password";
        }
        if (newPassword.length() < 6) {
            model.addAttribute("error", "New password must be at least 6 characters.");
            return "change-password";
        }

        String newHash = passwordEncoder.encode(newPassword);
        userDAO.changePassword(user.getUserId(), newHash);
        // Every OTHER open session of this account is ended by AccountSessionInterceptor (its fingerprint
        // no longer matches); this one stays logged in because we update its fingerprint here.
        session.setAttribute(AccountSessionInterceptor.FINGERPRINT_ATTR, AccountSessionInterceptor.fingerprint(newHash));
        model.addAttribute("message", "Password changed successfully.");
        return "change-password";
    }

    // POST /profile/resend-verification -> re-send the email verification link
    @PostMapping("/profile/resend-verification")
    public String resendVerification(HttpSession session, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        User sessionUser = currentUser(session);
        if (sessionUser == null) {
            return "redirect:/login";
        }

        User user = userDAO.findById(sessionUser.getUserId());
        if (user.isEmailVerified()) {
            redirectAttributes.addFlashAttribute("message", "Your email is already verified.");
        } else {
            verificationService.sendVerificationEmail(user, request);
            redirectAttributes.addFlashAttribute("message", "Verification email sent - please check your inbox.");
        }
        return "redirect:/profile";
    }

    // POST /profile/delete -> delete own account
    @PostMapping("/profile/delete")
    public String deleteAccount(HttpSession session, RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        // Staff accounts can't delete themselves (that would remove an admin / manager by accident)
        if (!"CUSTOMER".equals(user.getRole())) {
            redirectAttributes.addFlashAttribute("error", "Staff accounts can't be deleted here. Ask the store Admin.");
            return "redirect:/profile";
        }
        // Accounts that have placed orders can't be deleted (order history must be kept) -
        // tell the user instead of crashing with a database error.
        if (!userDAO.deleteAccount(user.getUserId())) {
            redirectAttributes.addFlashAttribute("error",
                    "This account can't be deleted because it has order history. Contact support if you need it removed.");
            return "redirect:/profile";
        }
        session.invalidate();
        return "redirect:/login";
    }

    // ============================================================
    // Admin / Customer Relations Manager side of the same function
    // ============================================================

    // GET /admin/customers -> dashboard of flagged customer accounts
    @GetMapping("/admin/customers")
    public String flaggedCustomers(Model model) {
        model.addAttribute("customers", userDAO.getFlaggedCustomers());
        return "admin-customers";
    }

    // POST /admin/customers/{id}/update -> correct a customer's profile/delivery info
    @PostMapping("/admin/customers/{id}/update")
    public String updateCustomer(@PathVariable("id") int userId,
                                  @RequestParam String fullName,
                                  @RequestParam(required = false) String phone,
                                  @RequestParam(required = false) String address,
                                  RedirectAttributes redirectAttributes) {
        fullName = fullName.trim();
        if (fullName.isEmpty() || fullName.length() > 100
                || (phone != null && phone.length() > 20) || (address != null && address.length() > 255)) {
            redirectAttributes.addFlashAttribute("error",
                    "Not saved: name is required (max 100), phone max 20, address max 255 characters.");
            return "redirect:/admin/customers";
        }
        User user = new User();
        user.setUserId(userId);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setAddress(address);
        userDAO.updateProfile(user);
        return "redirect:/admin/customers";
    }

    // POST /admin/customers/{id}/verify -> manually mark the email as verified
    // after confirming the customer's identity another way (e.g. phone/support)
    @PostMapping("/admin/customers/{id}/verify")
    public String verifyCustomer(@PathVariable("id") int userId) {
        userDAO.markEmailVerified(userId);
        return "redirect:/admin/customers";
    }

    // POST /admin/customers/{id}/suspend -> block this customer from logging in
    @PostMapping("/admin/customers/{id}/suspend")
    public String suspendCustomer(@PathVariable("id") int userId, RedirectAttributes redirectAttributes) {
        // Only CUSTOMER accounts: otherwise a manager could lock out the Admin (or themselves) with a hand-made POST
        User target = userDAO.findById(userId);
        if (target == null || !"CUSTOMER".equals(target.getRole())) {
            redirectAttributes.addFlashAttribute("error", "Only customer accounts can be suspended.");
            return "redirect:/admin/customers";
        }
        userDAO.updateStatus(userId, "SUSPENDED");
        return "redirect:/admin/customers";
    }

    // POST /admin/customers/{id}/unsuspend -> restore a suspended customer's ability to log in
    @PostMapping("/admin/customers/{id}/unsuspend")
    public String unsuspendCustomer(@PathVariable("id") int userId, RedirectAttributes redirectAttributes) {
        User target = userDAO.findById(userId);
        if (target == null || !"CUSTOMER".equals(target.getRole())) {
            redirectAttributes.addFlashAttribute("error", "Only customer accounts can be changed here.");
            return "redirect:/admin/customers";
        }
        userDAO.updateStatus(userId, "ACTIVE");
        return "redirect:/admin/customers";
    }

    // ============================================================
    // Customer-facing: complaints / return requests
    // ============================================================

    // GET /complaints -> my submitted complaints + a form to submit a new one
    @GetMapping("/complaints")
    public String myComplaints(HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        model.addAttribute("complaints", complaintDAO.getByUser(user.getUserId()));
        model.addAttribute("orders", orderDAO.getOrdersByUser(user.getUserId()));
        return "complaints";
    }

    // POST /complaints -> submit a new complaint, review, or return request
    @PostMapping("/complaints")
    public String submitComplaint(@RequestParam(required = false) Integer orderId,
                                   @RequestParam String subject,
                                   @RequestParam String message,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        subject = subject.trim();
        message = message.trim();
        if (subject.isEmpty() || message.isEmpty() || subject.length() > 150) {
            redirectAttributes.addFlashAttribute("error", "Please enter a subject (max 150 characters) and a message.");
            return "redirect:/complaints";
        }

        // The related order must exist AND belong to this customer - otherwise a made-up or someone
        // else's order id would either crash on the foreign key or attach a stranger's order.
        if (orderId != null) {
            Order order = orderDAO.getOrderById(orderId);
            if (order == null || order.getUserId() != user.getUserId()) {
                orderId = null;
            }
        }

        complaintDAO.submitComplaint(user.getUserId(), orderId, subject, message);
        return "redirect:/complaints";
    }

    // ============================================================
    // Admin / Customer Relations Manager side: complaints
    // ============================================================

    // GET /admin/complaints -> list all complaints, open ones first
    @GetMapping("/admin/complaints")
    public String listComplaints(Model model) {
        model.addAttribute("complaints", complaintDAO.getAll());
        return "admin-complaints";
    }

    // POST /admin/complaints/{id}/respond -> reply to and/or change the status of a complaint
    @PostMapping("/admin/complaints/{id}/respond")
    public String respondToComplaint(@PathVariable int id,
                                      @RequestParam String status,
                                      @RequestParam(required = false) String adminResponse) {
        if (VALID_COMPLAINT_STATUSES.contains(status)) { // unknown value would violate the DB CHECK constraint (HTTP 500)
            complaintDAO.respond(id, status, adminResponse);
        }
        return "redirect:/admin/complaints";
    }
}

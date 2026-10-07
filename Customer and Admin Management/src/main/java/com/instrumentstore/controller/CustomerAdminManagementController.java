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
import com.instrumentstore.patterns.singleton.AuditLog;
import com.instrumentstore.service.validation.InputValidator;
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

    private static final int MAX_COMPLAINT_TEXT = 2000; // complaint message / admin response (columns are VARCHAR(MAX), this keeps them sensible)
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

        String formatError = InputValidator.firstError(
                InputValidator.emailError(email), InputValidator.phoneError(phone), InputValidator.passwordError(password));
        if (formatError != null) {
            model.addAttribute("error", formatError);
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

    // Singleton pattern: every audit entry goes to the one shared AuditLog instance
    private static void audit(HttpSession session, String action, String details) {
        User actor = session == null ? null : (User) session.getAttribute("user");
        AuditLog.getInstance().record(actor == null ? "anonymous" : actor.getEmail(), action, details);
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
            audit(session, "LOGIN", "role " + user.getRole());

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
            AuditLog.getInstance().record(email.trim(), "LOGIN_FAILED", null);
            model.addAttribute("error", "Invalid email or password.");
            return "login";
        }
    }

    // POST /logout -> end the session (POST, not GET: a link or image on another site must not be able to log someone out)
    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            audit(session, "LOGOUT", null);
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
        String phoneError = InputValidator.phoneError(phone);
        if (phoneError != null) {
            model.addAttribute("user", updated);
            model.addAttribute("error", phoneError);
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
        String passwordError = InputValidator.passwordError(newPassword);
        if (passwordError != null) {
            model.addAttribute("error", "New password: " + passwordError);
            return "change-password";
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            model.addAttribute("error", "New password must be different from your current password.");
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
        model.addAttribute("customers", userDAO.getAllCustomers());
        return "admin-customers";
    }

    // POST /admin/customers -> Customer Relations adds a customer account (with a temporary password)
    @PostMapping("/admin/customers")
    public String addCustomer(@RequestParam String fullName,
                               @RequestParam String email,
                               @RequestParam String password,
                               @RequestParam(required = false) String phone,
                               @RequestParam(required = false) String address,
                               RedirectAttributes redirectAttributes) {
        fullName = fullName.trim();
        email = email.trim();
        if (fullName.isEmpty() || email.isEmpty() || password.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "Name and email are required, and the password must be at least 6 characters.");
            return "redirect:/admin/customers";
        }
        if (fullName.length() > 100 || email.length() > 100
                || (phone != null && phone.length() > 20) || (address != null && address.length() > 255)) {
            redirectAttributes.addFlashAttribute("error", "Not saved: name/email max 100, phone max 20, address max 255 characters.");
            return "redirect:/admin/customers";
        }
        String formatError = InputValidator.firstError(
                InputValidator.emailError(email), InputValidator.phoneError(phone), InputValidator.passwordError(password));
        if (formatError != null) {
            redirectAttributes.addFlashAttribute("error", "Not saved: " + formatError);
            return "redirect:/admin/customers";
        }
        if (userDAO.findByEmail(email) != null) {
            redirectAttributes.addFlashAttribute("error", "An account with this email already exists.");
            return "redirect:/admin/customers";
        }
        User newUser = new User(fullName, email, passwordEncoder.encode(password));
        newUser.setPhone(phone);
        newUser.setAddress(address);
        if (userDAO.registerUser(newUser)) {
            redirectAttributes.addFlashAttribute("message", "Customer added.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Could not add the customer. Please try again.");
        }
        return "redirect:/admin/customers";
    }

    // POST /admin/customers/{id}/delete -> remove a customer account (blocked when it has order history)
    @PostMapping("/admin/customers/{id}/delete")
    public String deleteCustomer(@PathVariable("id") int userId, HttpSession session, RedirectAttributes redirectAttributes) {
        User target = userDAO.findById(userId);
        if (target == null || !"CUSTOMER".equals(target.getRole())) {
            redirectAttributes.addFlashAttribute("error", "Only customer accounts can be deleted here.");
            return "redirect:/admin/customers";
        }
        if (userDAO.deleteAccount(userId)) {
            audit(session, "DELETE_CUSTOMER", target.getEmail());
            redirectAttributes.addFlashAttribute("message", "Customer deleted.");
        } else {
            redirectAttributes.addFlashAttribute("error",
                    "This customer has order history, so the account can't be deleted. Suspend it instead.");
        }
        return "redirect:/admin/customers";
    }

    // POST /admin/customers/{id}/update -> correct a customer's profile/delivery info
    @PostMapping("/admin/customers/{id}/update")
    public String updateCustomer(@PathVariable("id") int userId,
                                  @RequestParam String fullName,
                                  @RequestParam String email,
                                  @RequestParam(required = false) String phone,
                                  @RequestParam(required = false) String address,
                                  RedirectAttributes redirectAttributes) {
        fullName = fullName.trim();
        email = email.trim();
        if (fullName.isEmpty() || fullName.length() > 100 || email.isEmpty() || email.length() > 100
                || (phone != null && phone.length() > 20) || (address != null && address.length() > 255)) {
            redirectAttributes.addFlashAttribute("error",
                    "Not saved: name and email are required (max 100), phone max 20, address max 255 characters.");
            return "redirect:/admin/customers";
        }
        String formatError = InputValidator.firstError(InputValidator.emailError(email), InputValidator.phoneError(phone));
        if (formatError != null) {
            redirectAttributes.addFlashAttribute("error", "Not saved: " + formatError);
            return "redirect:/admin/customers";
        }
        User existing = userDAO.findByEmail(email);
        if (existing != null && existing.getUserId() != userId) {
            redirectAttributes.addFlashAttribute("error", "Not saved: another account already uses that email.");
            return "redirect:/admin/customers";
        }
        if (userDAO.updateCustomerDetails(userId, fullName, email, phone, address)) {
            redirectAttributes.addFlashAttribute("message", "Customer updated.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Customer not found or could not be updated.");
        }
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
    public String suspendCustomer(@PathVariable("id") int userId, HttpSession session, RedirectAttributes redirectAttributes) {
        // Only CUSTOMER accounts: otherwise a manager could lock out the Admin (or themselves) with a hand-made POST
        User target = userDAO.findById(userId);
        if (target == null || !"CUSTOMER".equals(target.getRole())) {
            redirectAttributes.addFlashAttribute("error", "Only customer accounts can be suspended.");
            return "redirect:/admin/customers";
        }
        userDAO.updateStatus(userId, "SUSPENDED");
        audit(session, "SUSPEND_CUSTOMER", target.getEmail());
        return "redirect:/admin/customers";
    }

    // POST /admin/customers/{id}/unsuspend -> restore a suspended customer's ability to log in
    @PostMapping("/admin/customers/{id}/unsuspend")
    public String unsuspendCustomer(@PathVariable("id") int userId, HttpSession session, RedirectAttributes redirectAttributes) {
        User target = userDAO.findById(userId);
        if (target == null || !"CUSTOMER".equals(target.getRole())) {
            redirectAttributes.addFlashAttribute("error", "Only customer accounts can be changed here.");
            return "redirect:/admin/customers";
        }
        userDAO.updateStatus(userId, "ACTIVE");
        audit(session, "UNSUSPEND_CUSTOMER", target.getEmail());
        return "redirect:/admin/customers";
    }

    // ============================================================
    // Customer-facing: complaints / return requests
    // ============================================================

    // GET /complaints -> my submitted complaints + a form to submit a new one
    @GetMapping("/complaints")
    public String myComplaints(@RequestParam(required = false) Integer orderId, HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        model.addAttribute("selectedOrderId", orderId); // set when coming from "Report a problem" on an order

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
        if (subject.isEmpty() || message.isEmpty() || subject.length() > 150 || message.length() > MAX_COMPLAINT_TEXT) {
            redirectAttributes.addFlashAttribute("error", "Please enter a subject (max 150 characters) and a message (max " + MAX_COMPLAINT_TEXT + " characters).");
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
        redirectAttributes.addFlashAttribute("message", "Thank you - your complaint has been submitted. We'll respond as soon as we can.");
        return "redirect:/complaints";
    }

    // ============================================================
    // Admin / Customer Relations Manager side: complaints
    // ============================================================

    // GET /admin/complaints -> list all complaints, open ones first
    @GetMapping("/admin/complaints")
    public String listComplaints(Model model) {
        model.addAttribute("complaints", complaintDAO.getAll());
        model.addAttribute("customers", userDAO.getAllCustomers());
        return "admin-complaints";
    }

    // POST /admin/complaints -> Customer Relations logs a complaint on a customer's behalf (e.g. phone call)
    @PostMapping("/admin/complaints")
    public String addComplaint(@RequestParam int userId,
                                @RequestParam(required = false) Integer orderId,
                                @RequestParam String subject,
                                @RequestParam String message,
                                RedirectAttributes redirectAttributes) {
        subject = subject.trim();
        message = message.trim();
        User customer = userDAO.findById(userId);
        if (customer == null || !"CUSTOMER".equals(customer.getRole())) {
            redirectAttributes.addFlashAttribute("error", "Please choose a customer.");
            return "redirect:/admin/complaints";
        }
        if (subject.isEmpty() || message.isEmpty() || subject.length() > 150 || message.length() > MAX_COMPLAINT_TEXT) {
            redirectAttributes.addFlashAttribute("error", "Please enter a subject (max 150 characters) and a message (max " + MAX_COMPLAINT_TEXT + " characters).");
            return "redirect:/admin/complaints";
        }
        if (orderId != null) {
            Order order = orderDAO.getOrderById(orderId);
            if (order == null || order.getUserId() != userId) orderId = null; // must be that customer's own order
        }
        complaintDAO.submitComplaint(userId, orderId, subject, message);
        redirectAttributes.addFlashAttribute("message", "Complaint added.");
        return "redirect:/admin/complaints";
    }

    // POST /admin/complaints/{id}/edit -> correct a complaint's subject / message
    @PostMapping("/admin/complaints/{id}/edit")
    public String editComplaint(@PathVariable int id,
                                 @RequestParam String subject,
                                 @RequestParam String message,
                                 RedirectAttributes redirectAttributes) {
        subject = subject.trim();
        message = message.trim();
        com.instrumentstore.model.Complaint existing = complaintDAO.getById(id);
        if (existing == null) {
            redirectAttributes.addFlashAttribute("error", "Complaint not found.");
            return "redirect:/admin/complaints";
        }
        if (subject.isEmpty() || message.isEmpty() || subject.length() > 150 || message.length() > MAX_COMPLAINT_TEXT) {
            redirectAttributes.addFlashAttribute("error", "Not saved: subject (max 150 characters) and message (max " + MAX_COMPLAINT_TEXT + " characters) are required.");
            return "redirect:/admin/complaints";
        }
        complaintDAO.updateContent(id, existing.getOrderId(), subject, message);
        redirectAttributes.addFlashAttribute("message", "Complaint updated.");
        return "redirect:/admin/complaints";
    }

    // POST /admin/complaints/{id}/delete -> remove a complaint
    @PostMapping("/admin/complaints/{id}/delete")
    public String deleteComplaint(@PathVariable int id, RedirectAttributes redirectAttributes) {
        complaintDAO.delete(id);
        redirectAttributes.addFlashAttribute("message", "Complaint deleted.");
        return "redirect:/admin/complaints";
    }

    // POST /admin/complaints/{id}/respond -> reply to and/or change the status of a complaint
    @PostMapping("/admin/complaints/{id}/respond")
    public String respondToComplaint(@PathVariable int id,
                                      @RequestParam String status,
                                      @RequestParam(required = false) String adminResponse,
                                      RedirectAttributes redirectAttributes) {
        if (!VALID_COMPLAINT_STATUSES.contains(status)) { // unknown value would violate the DB CHECK constraint (HTTP 500)
            redirectAttributes.addFlashAttribute("error", "Unknown complaint status.");
        } else if (adminResponse != null && adminResponse.length() > MAX_COMPLAINT_TEXT) {
            redirectAttributes.addFlashAttribute("error", "Response is too long (max " + MAX_COMPLAINT_TEXT + " characters).");
        } else if (complaintDAO.getById(id) == null) {
            redirectAttributes.addFlashAttribute("error", "Complaint not found.");
        } else {
            complaintDAO.respond(id, status, adminResponse);
        }
        return "redirect:/admin/complaints";
    }
}

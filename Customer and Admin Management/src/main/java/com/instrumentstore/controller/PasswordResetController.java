package com.instrumentstore.controller;

import com.instrumentstore.dao.PasswordResetTokenDAO;
import com.instrumentstore.dao.UserDAO;
import com.instrumentstore.model.PasswordResetToken;
import com.instrumentstore.model.User;
import com.instrumentstore.service.validation.InputValidator;
import com.instrumentstore.service.AccountVerificationService;
import com.instrumentstore.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Handles the Forgot Password / Password Reset minor function: a customer
 * who is logged out can request a one-time emailed link to set a new
 * password. Distinct from CustomerAdminManagementController's "change password", which
 * requires already being logged in and knowing the current password.
 */
@Controller
public class PasswordResetController {

    private final UserDAO userDAO;
    private final PasswordResetTokenDAO resetTokenDAO;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AccountVerificationService verificationService; // reused for its trusted baseUrl()

    public PasswordResetController(UserDAO userDAO, PasswordResetTokenDAO resetTokenDAO,
                                    PasswordEncoder passwordEncoder, NotificationService notificationService,
                                    AccountVerificationService verificationService) {
        this.userDAO = userDAO;
        this.resetTokenDAO = resetTokenDAO;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.verificationService = verificationService;
    }

    // GET /forgot-password -> show the "enter your email" form
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot-password";
    }

    // POST /forgot-password -> email a reset link if that address has an account
    @PostMapping("/forgot-password")
    public String requestReset(@RequestParam String email, HttpServletRequest request, Model model) {
        User user = userDAO.findByEmail(email.trim());

        if (user != null) {
            resetTokenDAO.deleteTokensForUser(user.getUserId());
            String token = UUID.randomUUID().toString();
            resetTokenDAO.createToken(user.getUserId(), token);

            String link = verificationService.baseUrl(request) + "/reset-password?token=" + token;
            String body = "Hi " + user.getFullName() + ",\n\n" +
                    "We received a request to reset your Ceylon Music House password. Open this link to choose a new one:\n" +
                    link + "\n\nThis link expires in 30 minutes. If you didn't request this, you can safely ignore this email.";
            notificationService.sendEmail(user.getEmail(), "Reset your password - Ceylon Music House", body);
        }

        // Same message whether or not the account exists, so this form can't
        // be used to probe which emails are registered.
        model.addAttribute("message", "If an account exists for that email, we've sent a password reset link.");
        return "forgot-password";
    }

    // GET /reset-password?token=... -> show the "choose a new password" form if the token is valid
    @GetMapping("/reset-password")
    public String showResetForm(@RequestParam String token, Model model) {
        PasswordResetToken resetToken = resetTokenDAO.findByToken(token);

        if (resetToken == null || !resetToken.isValid()) {
            model.addAttribute("error", "This reset link is invalid or has expired. Please request a new one.");
            return "reset-password-invalid";
        }

        model.addAttribute("token", token);
        return "reset-password";
    }

    // POST /reset-password -> set the new password
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                 @RequestParam String newPassword,
                                 Model model, RedirectAttributes redirectAttributes) {
        PasswordResetToken resetToken = resetTokenDAO.findByToken(token);

        if (resetToken == null || !resetToken.isValid()) {
            model.addAttribute("error", "This reset link is invalid or has expired. Please request a new one.");
            return "reset-password-invalid";
        }

        String passwordError = InputValidator.passwordError(newPassword);
        if (passwordError != null) {
            model.addAttribute("error", "New password: " + passwordError);
            model.addAttribute("token", token);
            return "reset-password";
        }

        userDAO.changePassword(resetToken.getUserId(), passwordEncoder.encode(newPassword));
        resetTokenDAO.markUsed(resetToken.getTokenId());

        redirectAttributes.addFlashAttribute("message", "Your password has been reset. Please log in.");
        return "redirect:/login";
    }
}

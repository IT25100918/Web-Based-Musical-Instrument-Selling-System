package com.instrumentstore.service;

import com.instrumentstore.dao.EmailVerificationTokenDAO;
import com.instrumentstore.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Builds and emails the one-time account-verification link (Session/Account
 * Verification), part of the Customer and Admin Management function. Used
 * by CustomerAdminManagementController both on registration and for the
 * "resend verification email" button.
 */
@Service
public class AccountVerificationService {

    private final EmailVerificationTokenDAO verificationTokenDAO;
    private final NotificationService notificationService;

    // Fixed, trusted base URL for emailed links (see app.base-url in application.properties)
    @Value("${app.base-url:}")
    private String configuredBaseUrl;

    public AccountVerificationService(EmailVerificationTokenDAO verificationTokenDAO,
                                       NotificationService notificationService) {
        this.verificationTokenDAO = verificationTokenDAO;
        this.notificationService = notificationService;
    }

    public void sendVerificationEmail(User user, HttpServletRequest request) {
        if (user == null || user.getEmail() == null) return;

        // Invalidate any earlier outstanding link before issuing a new one
        verificationTokenDAO.deleteTokensForUser(user.getUserId());
        String token = UUID.randomUUID().toString();
        verificationTokenDAO.createToken(user.getUserId(), token);

        String link = baseUrl(request) + "/verify-email?token=" + token;
        String body = "Hi " + user.getFullName() + ",\n\n" +
                "Please confirm your email address for your Ceylon Music House account by opening this link:\n" +
                link + "\n\nThis link expires in 24 hours.\n\nIf you didn't create this account, you can ignore this email.";

        notificationService.sendEmail(user.getEmail(), "Verify your email - Ceylon Music House", body);
    }

    /**
     * Base URL for links placed in emails. Prefers the configured app.base-url: building it from the
     * request's Host header would let anyone who can forge that header make the store email a victim
     * a link that points at the attacker's server (host-header poisoning).
     */
    public String baseUrl(HttpServletRequest request) {
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl.replaceAll("/+$", "");
        }
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        return request.getScheme() + "://" + request.getServerName() + (defaultPort ? "" : ":" + port) + request.getContextPath();
    }
}

package com.instrumentstore.config;

import com.instrumentstore.dao.UserDAO;
import com.instrumentstore.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Runs on every page request for a logged-in visitor and re-checks the account in the database:
 *
 *  - SUSPENDED or deleted accounts are logged out immediately (before this, suspension was only
 *    checked at login, so someone already logged in carried on shopping / working in the admin panel).
 *  - The role in the session is refreshed from the database, so a role change takes effect at once
 *    instead of only after the next login.
 *  - If the password was changed or reset since this session logged in, the session is ended.
 *    That is what makes "reset my password" actually kick out someone who had stolen the old one.
 *    (The session stores only a fingerprint of the password hash, never the hash itself.)
 *
 * Visitors with no session (or not logged in) are left alone - nothing is created or queried.
 */
@Component
public class AccountSessionInterceptor implements HandlerInterceptor {

    public static final String FINGERPRINT_ATTR = "pwdFingerprint";

    private final UserDAO userDAO;

    public AccountSessionInterceptor(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /** Short, non-reversible fingerprint of a stored password hash; changes whenever the password changes. */
    public static String fingerprint(String passwordHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        Object sessionUser = (session != null) ? session.getAttribute("user") : null;
        if (!(sessionUser instanceof User)) {
            return true; // not logged in
        }

        User fresh = userDAO.findById(((User) sessionUser).getUserId());
        if (fresh == null) {
            return endSession(session, request, response, null);
        }
        if ("SUSPENDED".equals(fresh.getStatus())) {
            return endSession(session, request, response, "suspended");
        }

        String currentFingerprint = fingerprint(fresh.getPassword());
        Object storedFingerprint = session.getAttribute(FINGERPRINT_ATTR);
        if (storedFingerprint == null) {
            session.setAttribute(FINGERPRINT_ATTR, currentFingerprint); // session from before this check existed
        } else if (!currentFingerprint.equals(storedFingerprint)) {
            return endSession(session, request, response, "password");
        }

        // Keep the session copy current (never keep the password hash in it)
        fresh.setPassword(null);
        session.setAttribute("user", fresh);
        session.setAttribute("role", fresh.getRole());
        return true;
    }

    private boolean endSession(HttpSession session, HttpServletRequest request, HttpServletResponse response,
                               String reason) throws Exception {
        session.invalidate();
        String target = request.getContextPath() + "/login" + (reason != null ? "?reason=" + reason : "");
        response.sendRedirect(target);
        return false;
    }
}

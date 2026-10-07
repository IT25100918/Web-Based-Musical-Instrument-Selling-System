package com.instrumentstore.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes the logged-in session role available to every admin template as
 * "role", without having to add it to every Admin*Controller method
 * individually. Used by the admin nav bar and admin-dashboard.html to show
 * only the sections each of the six manager roles (or the overall ADMIN)
 * is actually allowed to open - matching what AdminAuthInterceptor enforces
 * server-side.
 */
@ControllerAdvice(basePackages = "com.instrumentstore.controller")
public class AdminModelAttributes {

    @ModelAttribute("role")
    public String role(HttpServletRequest request) {
        // getSession(false): a plain HttpSession parameter would force-create a session for every visitor
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object role = session.getAttribute("role");
        return role != null ? role.toString() : null;
    }
}

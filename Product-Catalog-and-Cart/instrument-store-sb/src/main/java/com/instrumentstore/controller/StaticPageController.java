package com.instrumentstore.controller;

import com.instrumentstore.model.User;
import com.instrumentstore.service.NotificationService;
import com.instrumentstore.service.validation.InputValidator;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Minor Functions: Terms & Conditions / Privacy Policy pages, and the
 * Contact / Support page. These don't belong to any one of the six major
 * functions, but are referenced throughout the site (e.g. footer links).
 */
@Controller
public class StaticPageController {

    private final NotificationService notificationService;

    @Value("${app.contact-email}")
    private String contactEmail;

    public StaticPageController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/terms")
    public String terms(HttpSession session, Model model) {
        model.addAttribute("loggedInUser", session.getAttribute("user"));
        return "terms";
    }

    @GetMapping("/privacy")
    public String privacy(HttpSession session, Model model) {
        model.addAttribute("loggedInUser", session.getAttribute("user"));
        return "privacy";
    }

    @GetMapping("/contact")
    public String showContactForm(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        model.addAttribute("loggedInUser", user);
        if (user != null) {
            model.addAttribute("prefillName", user.getFullName());
            model.addAttribute("prefillEmail", user.getEmail());
        }
        return "contact";
    }

    // POST /contact -> forward the message to the store's support inbox
    @PostMapping("/contact")
    public String submitContactForm(@RequestParam String name,
                                     @RequestParam String email,
                                     @RequestParam String subject,
                                     @RequestParam String message,
                                     HttpSession session,
                                     Model model) {
        model.addAttribute("loggedInUser", session.getAttribute("user"));

        // Server-side validation (the form's required/maxlength can be bypassed)
        name = name.trim();
        email = email.trim();
        subject = subject.trim();
        message = message.trim();
        String validationError = InputValidator.firstError(
                InputValidator.requiredTextError("Name", name, 100),
                InputValidator.emailError(email),
                InputValidator.requiredTextError("Subject", subject, 150),
                InputValidator.requiredTextError("Message", message, 2000));
        if (validationError != null) {
            model.addAttribute("error", validationError);
            model.addAttribute("prefillName", name);
            model.addAttribute("prefillEmail", email);
            model.addAttribute("prefillSubject", subject);
            model.addAttribute("prefillMessage", message);
            return "contact";
        }

        String body = "New contact form submission from " + name + " (" + email + ")\n\n" + message;
        boolean sent = notificationService.sendEmail(contactEmail, "Contact form: " + subject, body);

        if (sent) {
            model.addAttribute("message", "Thanks for reaching out - our team will get back to you shortly.");
        } else {
            // Previously this always said "thanks" even when the mail never left the server
            model.addAttribute("error", notificationService.isEnabled()
                    ? "Sorry, we couldn't send your message right now. Please try again later."
                    : "Email is not switched on for this demo, so your message was not sent.");
            model.addAttribute("prefillName", name);
            model.addAttribute("prefillEmail", email);
        }
        return "contact";
    }
}

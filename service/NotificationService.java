package com.instrumentstore.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends real emails for order and delivery status changes (Delivery
 * Management's "Send Notifications" sub-function).
 *
 * If SMTP isn't configured (see application.properties), sending fails
 * gracefully - the error is logged, not thrown, so the rest of the app
 * (status updates, order flow) keeps working even without email set up.
 */
@Service
public class NotificationService {

    private final JavaMailSender mailSender;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** @return true if the mail was handed to the SMTP server, false if sending failed */
    public boolean sendEmail(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            // Strip CR/LF so user-supplied text can never inject extra mail headers
            message.setSubject(subject == null ? "" : subject.replaceAll("[\\r\\n]+", " "));
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            // Don't let a missing/broken email config break order or delivery updates
            System.err.println("Could not send notification email to " + toEmail + ": " + e.getMessage());
            return false;
        }
    }
}

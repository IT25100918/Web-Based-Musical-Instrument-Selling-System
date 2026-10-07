package com.instrumentstore.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends real emails for order and delivery status changes (Delivery
 * Management's "Send Notifications" sub-function).
 *
 * Email is switched OFF by default (app.mail.enabled=false in application.properties) because
 * SMTP isn't set up yet. While it is off nothing is sent and no SMTP login is attempted; a single
 * short line is logged instead (including any verification/reset link, so you can still test
 * those flows by copying the link from the console). To turn it on, fill in the mail settings in
 * secrets.properties and set app.mail.enabled=true.
 *
 * If sending is enabled but fails, the error is logged, not thrown, so the rest of the app
 * (status updates, order flow) keeps working.
 */
@Service
public class NotificationService {

    private final JavaMailSender mailSender;
    private final boolean enabled;

    public NotificationService(JavaMailSender mailSender,
                               @Value("${app.mail.enabled:false}") boolean enabled) {
        this.mailSender = mailSender;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** @return true if the mail was handed to the SMTP server, false if sending failed */
    public boolean sendEmail(String toEmail, String subject, String body) {
        if (!enabled) {
            System.out.println("[email disabled] Not sending \"" + subject + "\" to " + toEmail
                    + (body != null && body.contains("http") ? "\n" + body : ""));
            return false;
        }
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

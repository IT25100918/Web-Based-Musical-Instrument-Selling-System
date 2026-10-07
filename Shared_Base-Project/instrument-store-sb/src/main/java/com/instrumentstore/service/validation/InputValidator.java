package com.instrumentstore.service.validation;

import java.util.regex.Pattern;

/**
 * One place for the input rules that several functions share (accounts, customer relations,
 * suppliers). Each method returns a user-facing error message, or {@code null} when the value is fine,
 * so a controller can do:
 *
 *   String error = InputValidator.firstError(InputValidator.emailError(email), InputValidator.phoneError(phone));
 *   if (error != null) { ...show it... }
 *
 * The HTML forms have matching maxlength / pattern attributes, but those can be bypassed with a
 * hand-made request - these checks are the ones that actually protect the database.
 */
public final class InputValidator {

    public static final int PASSWORD_MIN = 6;
    /** BCrypt only reads the first 72 bytes, so a longer password would silently be cut short. */
    public static final int PASSWORD_MAX = 72;
    public static final int EMAIL_MAX = 100;   // users.email / suppliers.contact_email
    public static final int PHONE_MAX = 20;    // users.phone / suppliers.phone

    // local-part@domain.tld - deliberately simple (no quoted strings / IP literals)
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    // optional leading +, then digits with spaces, dashes and brackets allowed in between
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()-]+$");

    private InputValidator() {
    }

    /** Required email: not blank, at most 100 characters, looks like name@domain.tld. */
    public static String emailError(String email) {
        if (email == null || email.isBlank() || email.length() > EMAIL_MAX || !EMAIL.matcher(email.trim()).matches()) {
            return "Please enter a valid email address (e.g. name@example.com, max " + EMAIL_MAX + " characters).";
        }
        return null;
    }

    /** Optional phone: blank is fine; otherwise 7-15 digits, at most 20 characters, only digits + ( ) - and spaces. */
    public static String phoneError(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String p = phone.trim();
        long digits = p.chars().filter(Character::isDigit).count();
        if (p.length() > PHONE_MAX || !PHONE.matcher(p).matches() || digits < 7 || digits > 15) {
            return "Please enter a valid phone number (7-15 digits, e.g. +94 77 123 4567, max " + PHONE_MAX + " characters).";
        }
        return null;
    }

    /** Password length 6-72. */
    public static String passwordError(String password) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            return "Password must be between " + PASSWORD_MIN + " and " + PASSWORD_MAX + " characters.";
        }
        return null;
    }

    /** Required text no longer than {@code max} (after trimming). */
    public static String requiredTextError(String label, String value, int max) {
        if (value == null || value.isBlank()) {
            return label + " is required.";
        }
        return value.trim().length() > max ? label + " is too long (max " + max + " characters)." : null;
    }

    /** Returns the first non-null message, or null if every check passed. */
    public static String firstError(String... errors) {
        for (String e : errors) {
            if (e != null) {
                return e;
            }
        }
        return null;
    }
}

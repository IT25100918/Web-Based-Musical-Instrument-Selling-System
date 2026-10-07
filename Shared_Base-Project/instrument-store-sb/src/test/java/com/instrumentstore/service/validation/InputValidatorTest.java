package com.instrumentstore.service.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InputValidatorTest {

    @Test
    void acceptsNormalEmailsAndRejectsMalformedOnes() {
        assertNull(InputValidator.emailError("kamal.perera+shop@example.co.lk"));
        for (String bad : new String[]{null, "", "   ", "plainaddress", "a@b", "a@@b.com", "a b@c.com", "@c.com", "a@c..com", "a@c.c"}) {
            assertNotNull(InputValidator.emailError(bad), String.valueOf(bad));
        }
        assertNotNull(InputValidator.emailError("a".repeat(95) + "@x.com")); // over 100 characters
    }

    @Test
    void phoneIsOptionalButMustLookLikeAPhoneNumberWhenGiven() {
        assertNull(InputValidator.phoneError(null));
        assertNull(InputValidator.phoneError("  "));
        assertNull(InputValidator.phoneError("0771234567"));
        assertNull(InputValidator.phoneError("+94 77 123 4567"));
        assertNull(InputValidator.phoneError("(011) 234-5678"));
        for (String bad : new String[]{"abc", "12345", "077-ABC-1234", "+", "1".repeat(21), "07712345678901234"}) {
            assertNotNull(InputValidator.phoneError(bad), bad);
        }
    }

    @Test
    void passwordMustBeSixToSeventyTwoCharacters() {
        assertNotNull(InputValidator.passwordError(null));
        assertNotNull(InputValidator.passwordError("12345"));
        assertNull(InputValidator.passwordError("123456"));
        assertNull(InputValidator.passwordError("x".repeat(72)));
        assertNotNull(InputValidator.passwordError("x".repeat(73)));
    }

    @Test
    void requiredTextChecksBlankAndLength() {
        assertEquals("Subject is required.", InputValidator.requiredTextError("Subject", "  ", 150));
        assertNull(InputValidator.requiredTextError("Subject", "Hello", 150));
        assertNotNull(InputValidator.requiredTextError("Subject", "x".repeat(151), 150));
    }

    @Test
    void firstErrorReturnsTheFirstFailure() {
        assertNull(InputValidator.firstError(null, null));
        assertEquals("b", InputValidator.firstError(null, "b", "c"));
    }
}

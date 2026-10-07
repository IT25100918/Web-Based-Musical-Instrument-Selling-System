package com.instrumentstore.config;

import com.instrumentstore.dao.UserDAO;
import com.instrumentstore.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSessionInterceptorTest {

    @Mock UserDAO userDAO;

    AccountSessionInterceptor interceptor;
    MockHttpServletRequest request;
    MockHttpServletResponse response;
    MockHttpSession session;

    @BeforeEach
    void setUp() {
        interceptor = new AccountSessionInterceptor(userDAO);
        session = new MockHttpSession();
        request = new MockHttpServletRequest();
        request.setSession(session);
        response = new MockHttpServletResponse();
    }

    private User dbUser(String status, String role, String passwordHash) {
        User u = new User();
        u.setUserId(1);
        u.setStatus(status);
        u.setRole(role);
        u.setPassword(passwordHash);
        return u;
    }

    private void loggedIn(String passwordHash) {
        User sessionUser = new User();
        sessionUser.setUserId(1);
        session.setAttribute("user", sessionUser);
        session.setAttribute(AccountSessionInterceptor.FINGERPRINT_ATTR, AccountSessionInterceptor.fingerprint(passwordHash));
    }

    @Test
    void visitorsWhoAreNotLoggedInPassThroughWithoutADatabaseCall() throws Exception {
        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), response, new Object()));
    }

    @Test
    void activeUserPassesAndRoleIsRefreshedFromTheDatabase() throws Exception {
        loggedIn("hash-1");
        when(userDAO.findById(1)).thenReturn(dbUser("ACTIVE", "CATALOG_MANAGER", "hash-1"));
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals("CATALOG_MANAGER", session.getAttribute("role"));
    }

    @Test
    void suspendedUserIsLoggedOutImmediately() throws Exception {
        loggedIn("hash-1");
        when(userDAO.findById(1)).thenReturn(dbUser("SUSPENDED", "CUSTOMER", "hash-1"));
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(session.isInvalid());
        assertEquals("/login?reason=suspended", response.getRedirectedUrl());
    }

    @Test
    void deletedUserIsLoggedOut() throws Exception {
        loggedIn("hash-1");
        when(userDAO.findById(1)).thenReturn(null);
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(session.isInvalid());
    }

    @Test
    void passwordChangedElsewhereEndsTheOldSession() throws Exception {
        loggedIn("old-hash");
        when(userDAO.findById(1)).thenReturn(dbUser("ACTIVE", "CUSTOMER", "new-hash"));
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(session.isInvalid());
        assertEquals("/login?reason=password", response.getRedirectedUrl());
    }

    @Test
    void fingerprintChangesWhenThePasswordHashChanges() {
        assertNotEquals(AccountSessionInterceptor.fingerprint("a"), AccountSessionInterceptor.fingerprint("b"));
        assertEquals(AccountSessionInterceptor.fingerprint("a"), AccountSessionInterceptor.fingerprint("a"));
    }
}

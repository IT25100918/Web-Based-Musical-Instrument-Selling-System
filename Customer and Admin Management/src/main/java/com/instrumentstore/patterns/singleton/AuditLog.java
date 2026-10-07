package com.instrumentstore.patterns.singleton;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Singleton pattern (hand-written, exactly as in the lecture): one shared audit trail for
 * Customer Account &amp; Admin Management - logins, logouts, suspensions and deletions.
 *
 *  Step 1: private constructor   - nobody else can write "new AuditLog()"
 *  Step 2: private static field  - holds the single instance
 *  Step 3: public static getInstance() - the global access point (synchronized, so two
 *          requests arriving together can never create two instances - the lecture's
 *          "thread safety" point)
 *
 * Keeps the most recent {@value #MAX_ENTRIES} entries in memory and prints each one to the console.
 */
public final class AuditLog {

    private static final int MAX_ENTRIES = 200;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static AuditLog instance;

    private final Deque<String> entries = new ArrayDeque<>();

    private AuditLog() {
    }

    public static synchronized AuditLog getInstance() {
        if (instance == null) {
            instance = new AuditLog();
        }
        return instance;
    }
    //Every controller must write to the same log, otherwise
    // there would be several separate logs and you could never see the full history.
    // The Singleton pattern guarantees that there is exactly one AuditLog object for the
    // whole application.
    /** @param actor who did it (an email, or "anonymous"); @param action e.g. LOGIN, SUSPEND_CUSTOMER; @param details free text */
    public void record(String actor, String action, String details) {
        // Strip line breaks so user-supplied text (e.g. a typed email) can't forge extra log lines
        String line = LocalDateTime.now().format(TIME) + " | " + clean(actor) + " | " + clean(action)
                + (details == null || details.isBlank() ? "" : " | " + clean(details));
        synchronized (this) {
            entries.addFirst(line);
            while (entries.size() > MAX_ENTRIES) {
                entries.removeLast();
            }
        }
        System.out.println("[AUDIT] " + line);
    }

    /** Newest first. */
    public synchronized List<String> recent() {
        return new ArrayList<>(entries);
    }

    private static String clean(String s) {
        return s == null ? "-" : s.replaceAll("[\\r\\n]+", " ");
    }
}

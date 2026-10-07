package com.instrumentstore.model;

import java.sql.Timestamp;

/**
 * Represents a row in the `email_verification_tokens` table. A one-time
 * link mailed to a new user so they can confirm they own the email
 * address they registered with (Session/Account Verification).
 */
public class EmailVerificationToken {
    private int tokenId;
    private int userId;
    private String token;
    private Timestamp expiresAt;
    private Timestamp createdAt;

    public int getTokenId() { return tokenId; }
    public void setTokenId(int tokenId) { this.tokenId = tokenId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Timestamp getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Timestamp expiresAt) { this.expiresAt = expiresAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean isExpired() {
        return expiresAt == null || expiresAt.before(new Timestamp(System.currentTimeMillis()));
    }
}

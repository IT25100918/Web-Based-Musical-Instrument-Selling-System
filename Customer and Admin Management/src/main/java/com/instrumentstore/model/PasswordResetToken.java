package com.instrumentstore.model;

import java.sql.Timestamp;

/**
 * Represents a row in the `password_reset_tokens` table. A one-time,
 * expiring, single-use link mailed to a user who requested a password
 * reset while logged out (Forgot Password).
 */
public class PasswordResetToken {
    private int tokenId;
    private int userId;
    private String token;
    private Timestamp expiresAt;
    private boolean used;
    private Timestamp createdAt;

    public int getTokenId() { return tokenId; }
    public void setTokenId(int tokenId) { this.tokenId = tokenId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Timestamp getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Timestamp expiresAt) { this.expiresAt = expiresAt; }

    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean isValid() {
        return !used && expiresAt != null && expiresAt.after(new Timestamp(System.currentTimeMillis()));
    }
}

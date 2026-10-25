package com.example.woshield;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Data model for a temporary, bounded location-sharing session.
 * Tracks session lifecycle, expiration, and authorized recipient lists.
 */
public class SharingSession implements Serializable {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_REVOKED = "revoked";
    public static final String STATUS_COMPLETED = "completed";

    private String sessionId;
    private String ownerUid;
    private String title;
    private List<String> authorizedRecipientUids;
    private List<String> recipientNames;
    private long createdAt;
    private long expiresAt;
    private long revokedAt;
    private String status;

    public SharingSession() {
        this.status = STATUS_ACTIVE;
        this.authorizedRecipientUids = new ArrayList<>();
        this.recipientNames = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }

    public SharingSession(String sessionId, String ownerUid, String title,
                          List<String> authorizedRecipientUids, List<String> recipientNames,
                          long expiresAt) {
        this.sessionId = sessionId;
        this.ownerUid = ownerUid;
        this.title = title != null ? title : "Safety Sharing Session";
        this.authorizedRecipientUids = authorizedRecipientUids != null ? authorizedRecipientUids : new ArrayList<>();
        this.recipientNames = recipientNames != null ? recipientNames : new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = expiresAt;
        this.revokedAt = 0;
        this.status = STATUS_ACTIVE;
    }

    public boolean isExpired() {
        if (!STATUS_ACTIVE.equals(status)) return true;
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status) && !isExpired();
    }

    public long getRemainingMinutes() {
        if (!isActive() || expiresAt == 0) return 0;
        long diff = expiresAt - System.currentTimeMillis();
        return Math.max(0, diff / (60 * 1000L));
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<String> getAuthorizedRecipientUids() { return authorizedRecipientUids; }
    public void setAuthorizedRecipientUids(List<String> authorizedRecipientUids) { this.authorizedRecipientUids = authorizedRecipientUids; }

    public List<String> getRecipientNames() { return recipientNames; }
    public void setRecipientNames(List<String> recipientNames) { this.recipientNames = recipientNames; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public long getRevokedAt() { return revokedAt; }
    public void setRevokedAt(long revokedAt) { this.revokedAt = revokedAt; }

    public String getStatus() {
        if (STATUS_ACTIVE.equals(status) && isExpired()) {
            return STATUS_EXPIRED;
        }
        return status;
    }
    public void setStatus(String status) { this.status = status; }
}

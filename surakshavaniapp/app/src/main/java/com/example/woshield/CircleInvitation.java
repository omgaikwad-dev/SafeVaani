package com.example.woshield;

import java.io.Serializable;

/**
 * Invitation model representing consent-based requests to join a user's Trusted Safety Circle.
 */
public class CircleInvitation implements Serializable {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_EXPIRED = "expired";

    private String invitationId;
    private String senderUid;
    private String senderName;
    private String senderEmail;
    private String recipientEmail;
    private String recipientPhone;
    private String recipientUid;
    private String status;
    private CirclePermissions permissions;
    private long createdAt;
    private long expiresAt;
    private long respondedAt;

    public CircleInvitation() {
        this.status = STATUS_PENDING;
        this.permissions = new CirclePermissions();
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = System.currentTimeMillis() + (7L * 24L * 60L * 60L * 1000L); // 7 days expiration
    }

    public CircleInvitation(String invitationId, String senderUid, String senderName,
                            String senderEmail, String recipientEmail, String recipientPhone,
                            CirclePermissions permissions) {
        this.invitationId = invitationId;
        this.senderUid = senderUid;
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.recipientEmail = recipientEmail != null ? recipientEmail.toLowerCase().trim() : "";
        this.recipientPhone = recipientPhone != null ? recipientPhone.trim() : "";
        this.status = STATUS_PENDING;
        this.permissions = permissions != null ? permissions : new CirclePermissions();
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = System.currentTimeMillis() + (7L * 24L * 60L * 60L * 1000L);
    }

    public boolean isExpired() {
        return STATUS_PENDING.equals(status) && System.currentTimeMillis() > expiresAt;
    }

    // Getters and Setters
    public String getInvitationId() { return invitationId; }
    public void setInvitationId(String invitationId) { this.invitationId = invitationId; }

    public String getSenderUid() { return senderUid; }
    public void setSenderUid(String senderUid) { this.senderUid = senderUid; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public String getRecipientUid() { return recipientUid; }
    public void setRecipientUid(String recipientUid) { this.recipientUid = recipientUid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public CirclePermissions getPermissions() { return permissions; }
    public void setPermissions(CirclePermissions permissions) { this.permissions = permissions; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public long getRespondedAt() { return respondedAt; }
    public void setRespondedAt(long respondedAt) { this.respondedAt = respondedAt; }
}

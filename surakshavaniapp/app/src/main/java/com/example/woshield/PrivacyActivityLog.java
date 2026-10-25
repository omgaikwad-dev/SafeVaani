package com.example.woshield;

import java.io.Serializable;

/**
 * Audit record for user privacy actions, permission modifications,
 * sharing lifecycles, and data retention operations.
 * Purposely omits precise GPS coordinates, audio files, and auth tokens.
 */
public class PrivacyActivityLog implements Serializable {

    public static final String TYPE_SHARING_STARTED = "sharing_started";
    public static final String TYPE_SHARING_REVOKED = "sharing_revoked";
    public static final String TYPE_SHARING_EXPIRED = "sharing_expired";
    public static final String TYPE_PERMISSION_CHANGED = "permission_changed";
    public static final String TYPE_MEMBER_INVITED = "member_invited";
    public static final String TYPE_MEMBER_REVOKED = "member_revoked";
    public static final String TYPE_VOICE_SHIELD_TOGGLED = "voice_shield_toggled";
    public static final String TYPE_EVIDENCE_RECORDED = "evidence_recorded";
    public static final String TYPE_DATA_PURGED = "data_purged";

    public static final String SEVERITY_INFO = "info";
    public static final String SEVERITY_WARNING = "warning";
    public static final String SEVERITY_SECURITY = "security";

    private String logId;
    private String ownerUid;
    private String eventType;
    private String title;
    private String description;
    private long timestamp;
    private String severity;

    public PrivacyActivityLog() {
        this.timestamp = System.currentTimeMillis();
        this.severity = SEVERITY_INFO;
    }

    public PrivacyActivityLog(String logId, String ownerUid, String eventType,
                              String title, String description, String severity) {
        this.logId = logId;
        this.ownerUid = ownerUid;
        this.eventType = eventType;
        this.title = title;
        this.description = description;
        this.timestamp = System.currentTimeMillis();
        this.severity = severity != null ? severity : SEVERITY_INFO;
    }

    // Getters and Setters
    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }

    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
}

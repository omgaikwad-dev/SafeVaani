package com.example.woshield;

import java.io.Serializable;

/**
 * Model representing a persistent safety event/alert visible in the Family Dashboard.
 * Supports lifecycle states: created -> delivered -> acknowledged -> resolved.
 */
public class FamilyAlert implements Serializable {

    public static final String TYPE_SOS = "sos";
    public static final String TYPE_ASSISTANCE = "assistance";
    public static final String TYPE_MISSED_CHECKIN = "missed_checkin";
    public static final String TYPE_ROUTINE_ARRIVAL = "routine_arrival";

    public static final String STATUS_CREATED = "created";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_ACKNOWLEDGED = "acknowledged";
    public static final String STATUS_RESOLVED = "resolved";

    private String alertId;
    private String wardUid;
    private String wardName;
    private String type;
    private String status;
    private String message;
    private double latitude;
    private double longitude;
    private String audioUrl;
    private long createdAt;
    private long acknowledgedAt;
    private String acknowledgedBy;
    private long resolvedAt;

    public FamilyAlert() {
        this.status = STATUS_DELIVERED;
        this.createdAt = System.currentTimeMillis();
    }

    public FamilyAlert(String alertId, String wardUid, String wardName, String type,
                       String message, double latitude, double longitude, String audioUrl) {
        this.alertId = alertId;
        this.wardUid = wardUid;
        this.wardName = wardName;
        this.type = type;
        this.status = STATUS_DELIVERED;
        this.message = message;
        this.latitude = latitude;
        this.longitude = longitude;
        this.audioUrl = audioUrl != null ? audioUrl : "";
        this.createdAt = System.currentTimeMillis();
        this.acknowledgedAt = 0;
        this.acknowledgedBy = "";
        this.resolvedAt = 0;
    }

    public boolean isEmergency() {
        return TYPE_SOS.equalsIgnoreCase(type);
    }

    public boolean isAcknowledged() {
        return STATUS_ACKNOWLEDGED.equalsIgnoreCase(status) || STATUS_RESOLVED.equalsIgnoreCase(status);
    }

    // Getters and Setters
    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public String getWardUid() { return wardUid; }
    public void setWardUid(String wardUid) { this.wardUid = wardUid; }

    public String getWardName() { return wardName; }
    public void setWardName(String wardName) { this.wardName = wardName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(long acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }

    public long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(long resolvedAt) { this.resolvedAt = resolvedAt; }
}

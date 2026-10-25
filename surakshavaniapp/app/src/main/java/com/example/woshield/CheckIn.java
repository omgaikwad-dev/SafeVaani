package com.example.woshield;

import java.io.Serializable;

/**
 * Data model for Woshield Smart Check-In.
 * Implements strict state-transition validation to prevent duplicate responses
 * or race conditions between timeout workers and user submissions.
 */
public class CheckIn implements Serializable {

    // Status Constants
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_CONFIRMED_SAFE = "confirmed_safe";
    public static final String STATUS_ASSISTANCE_REQUESTED = "assistance_requested";
    public static final String STATUS_SOS_TRIGGERED = "sos_triggered";
    public static final String STATUS_MISSED = "missed";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_EXPIRED = "expired";

    // Escalation Policy Constants
    public static final String POLICY_NOTIFY_CONTACTS = "notify_contacts";
    public static final String POLICY_SILENT_LOG = "silent_log";

    // Escalation Status Constants
    public static final String ESCALATION_NONE = "none";
    public static final String ESCALATION_NOTIFIED_CONTACTS = "notified_contacts";
    public static final String ESCALATION_SOS_TRIGGERED = "sos_triggered";
    public static final String ESCALATION_FAILED = "failed";

    private String checkInId;
    private String ownerUid;
    private String journeyId;
    private String routineId;
    private String routineTitle;
    private String reason;
    private String status;
    private long createdAt;
    private long deadlineAt;
    private long respondedAt;
    private String response;
    private String escalationPolicy;
    private String escalationStatus;
    private double latitude;
    private double longitude;
    private String locationAddress;
    private boolean locationAuthorized;
    private long updatedAt;

    // Required default constructor for Firebase DataSnapshot.getValue(CheckIn.class)
    public CheckIn() {
        this.status = STATUS_PENDING;
        this.escalationPolicy = POLICY_NOTIFY_CONTACTS;
        this.escalationStatus = ESCALATION_NONE;
        this.response = "none";
        this.locationAuthorized = true;
    }

    public CheckIn(String checkInId, String ownerUid, String routineId, String routineTitle,
                   String journeyId, String reason, long deadlineAt) {
        this.checkInId = checkInId;
        this.ownerUid = ownerUid;
        this.routineId = routineId;
        this.routineTitle = routineTitle;
        this.journeyId = journeyId;
        this.reason = reason;
        this.status = STATUS_PENDING;
        this.createdAt = System.currentTimeMillis();
        this.deadlineAt = deadlineAt;
        this.respondedAt = 0;
        this.response = "none";
        this.escalationPolicy = POLICY_NOTIFY_CONTACTS;
        this.escalationStatus = ESCALATION_NONE;
        this.latitude = 0.0;
        this.longitude = 0.0;
        this.locationAddress = "";
        this.locationAuthorized = true;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Strictly verifies whether transition to candidate status is permitted.
     * Transitions are ONLY permissible from STATUS_PENDING.
     * Terminal states cannot transition to any other state.
     */
    public boolean canTransitionTo(String newStatus) {
        if (newStatus == null) return false;
        if (!STATUS_PENDING.equals(this.status)) {
            return false; // Already resolved/terminal
        }
        return STATUS_CONFIRMED_SAFE.equals(newStatus)
                || STATUS_ASSISTANCE_REQUESTED.equals(newStatus)
                || STATUS_SOS_TRIGGERED.equals(newStatus)
                || STATUS_MISSED.equals(newStatus)
                || STATUS_CANCELLED.equals(newStatus)
                || STATUS_EXPIRED.equals(newStatus);
    }

    public boolean isTerminal() {
        return !STATUS_PENDING.equals(this.status);
    }

    public boolean isExpired() {
        return STATUS_PENDING.equals(this.status) && System.currentTimeMillis() > deadlineAt;
    }

    public long getRemainingMillis() {
        if (!STATUS_PENDING.equals(this.status)) return 0;
        long diff = deadlineAt - System.currentTimeMillis();
        return Math.max(0, diff);
    }

    // Getters and Setters
    public String getCheckInId() { return checkInId; }
    public void setCheckInId(String checkInId) { this.checkInId = checkInId; }

    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }

    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }

    public String getRoutineId() { return routineId; }
    public void setRoutineId(String routineId) { this.routineId = routineId; }

    public String getRoutineTitle() { return routineTitle; }
    public void setRoutineTitle(String routineTitle) { this.routineTitle = routineTitle; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(long deadlineAt) { this.deadlineAt = deadlineAt; }

    public long getRespondedAt() { return respondedAt; }
    public void setRespondedAt(long respondedAt) { this.respondedAt = respondedAt; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public String getEscalationPolicy() { return escalationPolicy; }
    public void setEscalationPolicy(String escalationPolicy) { this.escalationPolicy = escalationPolicy; }

    public String getEscalationStatus() { return escalationStatus; }
    public void setEscalationStatus(String escalationStatus) { this.escalationStatus = escalationStatus; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getLocationAddress() { return locationAddress; }
    public void setLocationAddress(String locationAddress) { this.locationAddress = locationAddress; }

    public boolean isLocationAuthorized() { return locationAuthorized; }
    public void setLocationAuthorized(boolean locationAuthorized) { this.locationAuthorized = locationAuthorized; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}

package com.example.woshield;

import java.io.Serializable;

/**
 * Model representing a member of the Trusted Safety Circle with real-time status,
 * authorized location updates, and stale-data safety indicators.
 */
public class FamilyMember implements Serializable {

    public static final String STATUS_SAFE = "safe";
    public static final String STATUS_PENDING_CHECKIN = "pending_checkin";
    public static final String STATUS_ASSISTANCE_NEEDED = "assistance_needed";
    public static final String STATUS_SOS_ACTIVE = "sos_active";
    public static final String STATUS_STALE = "stale";
    public static final String STATUS_OFFLINE = "offline";
    public static final String STATUS_UNKNOWN = "unknown";

    private static final long STALE_LOCATION_THRESHOLD_MS = 15 * 60 * 1000L; // 15 minutes

    private String uid;
    private String displayName;
    private String email;
    private String phone;
    private String relationship;
    private String safetyStatus;
    private String statusDetails;
    private double latitude;
    private double longitude;
    private String locationAddress;
    private long locationTimestamp;
    private boolean isSharingActive;
    private String activeRoutineTitle;
    private String expectedArrival;
    private long joinedAt;
    private CirclePermissions permissions;

    public FamilyMember() {
        this.safetyStatus = STATUS_UNKNOWN;
        this.statusDetails = "No recent status";
        this.permissions = new CirclePermissions();
        this.joinedAt = System.currentTimeMillis();
    }

    public FamilyMember(String uid, String displayName, String email, String phone, CirclePermissions permissions) {
        this.uid = uid;
        this.displayName = (displayName != null && !displayName.trim().isEmpty()) ? displayName : (email != null ? email.split("@")[0] : "Family Member");
        this.email = email;
        this.phone = phone;
        this.relationship = "Family Member";
        this.safetyStatus = STATUS_SAFE;
        this.statusDetails = "Protected on Woshield Network";
        this.permissions = permissions != null ? permissions : new CirclePermissions();
        this.joinedAt = System.currentTimeMillis();
    }

    public boolean isLocationStale() {
        if (!isSharingActive || locationTimestamp == 0) return true;
        return (System.currentTimeMillis() - locationTimestamp) > STALE_LOCATION_THRESHOLD_MS;
    }

    public long getLocationAgeMinutes() {
        if (locationTimestamp == 0) return 0;
        return Math.max(0, (System.currentTimeMillis() - locationTimestamp) / (60 * 1000L));
    }

    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }

    public String getSafetyStatus() { return safetyStatus; }
    public void setSafetyStatus(String safetyStatus) { this.safetyStatus = safetyStatus; }

    public String getStatusDetails() { return statusDetails; }
    public void setStatusDetails(String statusDetails) { this.statusDetails = statusDetails; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getLocationAddress() { return locationAddress; }
    public void setLocationAddress(String locationAddress) { this.locationAddress = locationAddress; }

    public long getLocationTimestamp() { return locationTimestamp; }
    public void setLocationTimestamp(long locationTimestamp) { this.locationTimestamp = locationTimestamp; }

    public boolean isSharingActive() { return isSharingActive; }
    public void setSharingActive(boolean sharingActive) { isSharingActive = sharingActive; }

    public String getActiveRoutineTitle() { return activeRoutineTitle; }
    public void setActiveRoutineTitle(String activeRoutineTitle) { this.activeRoutineTitle = activeRoutineTitle; }

    public String getExpectedArrival() { return expectedArrival; }
    public void setExpectedArrival(String expectedArrival) { this.expectedArrival = expectedArrival; }

    public long getJoinedAt() { return joinedAt; }
    public void setJoinedAt(long joinedAt) { this.joinedAt = joinedAt; }

    public CirclePermissions getPermissions() { return permissions; }
    public void setPermissions(CirclePermissions permissions) { this.permissions = permissions; }
}

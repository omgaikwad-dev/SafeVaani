package com.example.woshield;

import java.io.Serializable;

/**
 * Granular consent permissions granted by a user to a specific trusted circle member.
 * Enforced on both server/database rules and client observation layer.
 */
public class CirclePermissions implements Serializable {
    private boolean canViewStatus;
    private boolean canViewLocation;
    private boolean canViewJourney;
    private boolean canReceiveSos;
    private boolean canReceiveAssistance;
    private boolean canReceiveMissedCheckIns;
    private boolean canReceiveRoutineUpdates;
    private long expiresAt; // 0 indicates no expiration (until explicitly revoked)

    public CirclePermissions() {
        this.canViewStatus = true;
        this.canViewLocation = true;
        this.canViewJourney = true;
        this.canReceiveSos = true;
        this.canReceiveAssistance = true;
        this.canReceiveMissedCheckIns = true;
        this.canReceiveRoutineUpdates = true;
        this.expiresAt = 0;
    }

    public CirclePermissions(boolean canViewStatus, boolean canViewLocation, boolean canViewJourney,
                             boolean canReceiveSos, boolean canReceiveAssistance,
                             boolean canReceiveMissedCheckIns, boolean canReceiveRoutineUpdates,
                             long expiresAt) {
        this.canViewStatus = canViewStatus;
        this.canViewLocation = canViewLocation;
        this.canViewJourney = canViewJourney;
        this.canReceiveSos = canReceiveSos;
        this.canReceiveAssistance = canReceiveAssistance;
        this.canReceiveMissedCheckIns = canReceiveMissedCheckIns;
        this.canReceiveRoutineUpdates = canReceiveRoutineUpdates;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    // Getters and Setters
    public boolean isCanViewStatus() { return canViewStatus && !isExpired(); }
    public void setCanViewStatus(boolean canViewStatus) { this.canViewStatus = canViewStatus; }

    public boolean isCanViewLocation() { return canViewLocation && !isExpired(); }
    public void setCanViewLocation(boolean canViewLocation) { this.canViewLocation = canViewLocation; }

    public boolean isCanViewJourney() { return canViewJourney && !isExpired(); }
    public void setCanViewJourney(boolean canViewJourney) { this.canViewJourney = canViewJourney; }

    public boolean isCanReceiveSos() { return canReceiveSos && !isExpired(); }
    public void setCanReceiveSos(boolean canReceiveSos) { this.canReceiveSos = canReceiveSos; }

    public boolean isCanReceiveAssistance() { return canReceiveAssistance && !isExpired(); }
    public void setCanReceiveAssistance(boolean canReceiveAssistance) { this.canReceiveAssistance = canReceiveAssistance; }

    public boolean isCanReceiveMissedCheckIns() { return canReceiveMissedCheckIns && !isExpired(); }
    public void setCanReceiveMissedCheckIns(boolean canReceiveMissedCheckIns) { this.canReceiveMissedCheckIns = canReceiveMissedCheckIns; }

    public boolean isCanReceiveRoutineUpdates() { return canReceiveRoutineUpdates && !isExpired(); }
    public void setCanReceiveRoutineUpdates(boolean canReceiveRoutineUpdates) { this.canReceiveRoutineUpdates = canReceiveRoutineUpdates; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
}

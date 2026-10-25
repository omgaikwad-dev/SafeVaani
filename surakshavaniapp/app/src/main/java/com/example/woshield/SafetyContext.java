package com.example.woshield;

import java.io.Serializable;

/**
 * Encapsulates the runtime context evaluated by the Safety Intelligence Engine.
 * Represents real-time movement, routine status, check-in state, and emergency indicators.
 * Guarantees that unavailable information is represented as unavailable.
 */
public class SafetyContext implements Serializable {

    private String ownerUid;

    // Routine / Journey State
    private String routineId;
    private String routineTitle;
    private String destination;
    private long expectedArrivalMillis;
    private long journeyStartMillis;
    private boolean hasArrivedAtDestination;
    private boolean isNearSafeStop;

    // Location & Accuracy Sample
    private boolean locationAvailable;
    private double latitude;
    private double longitude;
    private float accuracyMeters;
    private long locationTimestamp;

    // Route Deviation & Movement Signals
    private double distanceFromRouteMeters;
    private int sustainedDeviationSamples;
    private boolean userConfirmedRouteChange;

    // Smart Check-In & Emergency State
    private boolean explicitSosActive;
    private boolean explicitAssistanceActive;
    private boolean hasPendingCheckIn;
    private boolean missedCheckIn;

    public SafetyContext() {
        this.locationAvailable = false;
        this.sustainedDeviationSamples = 0;
        this.userConfirmedRouteChange = false;
        this.hasArrivedAtDestination = false;
        this.isNearSafeStop = false;
        this.explicitSosActive = false;
        this.explicitAssistanceActive = false;
        this.hasPendingCheckIn = false;
        this.missedCheckIn = false;
    }

    public boolean isLocationAvailable() {
        return locationAvailable && locationTimestamp > 0;
    }

    public boolean isLocationStale(long maxAgeMs, long nowMs) {
        if (!isLocationAvailable()) return true;
        return (nowMs - locationTimestamp) > maxAgeMs;
    }

    public boolean isLocationAccurate(float maxAllowedAccuracyMeters) {
        if (!isLocationAvailable()) return false;
        return accuracyMeters > 0 && accuracyMeters <= maxAllowedAccuracyMeters;
    }

    public boolean isDelayExceeded(long gracePeriodMs, long nowMs) {
        if (expectedArrivalMillis <= 0 || hasArrivedAtDestination) return false;
        return nowMs > (expectedArrivalMillis + gracePeriodMs);
    }

    public int getDelayMinutes(long nowMs) {
        if (expectedArrivalMillis <= 0 || nowMs <= expectedArrivalMillis) return 0;
        return (int) ((nowMs - expectedArrivalMillis) / (60 * 1000L));
    }

    // Getters and Setters
    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }

    public String getRoutineId() { return routineId; }
    public void setRoutineId(String routineId) { this.routineId = routineId; }

    public String getRoutineTitle() { return routineTitle; }
    public void setRoutineTitle(String routineTitle) { this.routineTitle = routineTitle; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public long getExpectedArrivalMillis() { return expectedArrivalMillis; }
    public void setExpectedArrivalMillis(long expectedArrivalMillis) { this.expectedArrivalMillis = expectedArrivalMillis; }

    public long getJourneyStartMillis() { return journeyStartMillis; }
    public void setJourneyStartMillis(long journeyStartMillis) { this.journeyStartMillis = journeyStartMillis; }

    public boolean isHasArrivedAtDestination() { return hasArrivedAtDestination; }
    public void setHasArrivedAtDestination(boolean hasArrivedAtDestination) { this.hasArrivedAtDestination = hasArrivedAtDestination; }

    public boolean isNearSafeStop() { return isNearSafeStop; }
    public void setNearSafeStop(boolean nearSafeStop) { isNearSafeStop = nearSafeStop; }

    public void setLocationAvailable(boolean locationAvailable) { this.locationAvailable = locationAvailable; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public float getAccuracyMeters() { return accuracyMeters; }
    public void setAccuracyMeters(float accuracyMeters) { this.accuracyMeters = accuracyMeters; }

    public long getLocationTimestamp() { return locationTimestamp; }
    public void setLocationTimestamp(long locationTimestamp) { this.locationTimestamp = locationTimestamp; }

    public double getDistanceFromRouteMeters() { return distanceFromRouteMeters; }
    public void setDistanceFromRouteMeters(double distanceFromRouteMeters) { this.distanceFromRouteMeters = distanceFromRouteMeters; }

    public int getSustainedDeviationSamples() { return sustainedDeviationSamples; }
    public void setSustainedDeviationSamples(int sustainedDeviationSamples) { this.sustainedDeviationSamples = sustainedDeviationSamples; }

    public boolean isUserConfirmedRouteChange() { return userConfirmedRouteChange; }
    public void setUserConfirmedRouteChange(boolean userConfirmedRouteChange) { this.userConfirmedRouteChange = userConfirmedRouteChange; }

    public boolean isExplicitSosActive() { return explicitSosActive; }
    public void setExplicitSosActive(boolean explicitSosActive) { this.explicitSosActive = explicitSosActive; }

    public boolean isExplicitAssistanceActive() { return explicitAssistanceActive; }
    public void setExplicitAssistanceActive(boolean explicitAssistanceActive) { this.explicitAssistanceActive = explicitAssistanceActive; }

    public boolean isHasPendingCheckIn() { return hasPendingCheckIn; }
    public void setHasPendingCheckIn(boolean hasPendingCheckIn) { this.hasPendingCheckIn = hasPendingCheckIn; }

    public boolean isMissedCheckIn() { return missedCheckIn; }
    public void setMissedCheckIn(boolean missedCheckIn) { this.missedCheckIn = missedCheckIn; }
}

package com.example.woshield;

import java.io.Serializable;

/**
 * Deterministic and transparent configuration for Safety Intelligence rules.
 * Governs noise filtering, grace periods, and deduplication thresholds.
 */
public class SafetyRuleConfig implements Serializable {

    // Default Threshold Values
    public static final float DEFAULT_MAX_GPS_ACCURACY_METERS = 65.0f; // Reject noisy samples > 65m
    public static final long DEFAULT_STALE_LOCATION_MS = 5 * 60 * 1000L; // 5 minutes
    public static final double DEFAULT_DEVIATION_METERS = 250.0; // 250 meters from planned corridor
    public static final int DEFAULT_SUSTAINED_SAMPLES = 3; // Require 3 consecutive samples to confirm deviation
    public static final int DEFAULT_DELAY_GRACE_MINUTES = 10; // 10 minutes grace past expected arrival
    public static final long DEFAULT_DEDUPLICATION_WINDOW_MS = 15 * 60 * 1000L; // 15 minutes between alerts
    public static final int DEFAULT_AUTO_CHECKIN_DEADLINE_MINUTES = 5; // 5 minutes to respond to check-in

    private float maxAllowedGpsAccuracyMeters;
    private long staleLocationThresholdMs;
    private double deviationDistanceThresholdMeters;
    private int sustainedDeviationSamplesRequired;
    private int delayGracePeriodMinutes;
    private long deduplicationWindowMs;
    private int autoCheckInDeadlineMinutes;

    public SafetyRuleConfig() {
        this.maxAllowedGpsAccuracyMeters = DEFAULT_MAX_GPS_ACCURACY_METERS;
        this.staleLocationThresholdMs = DEFAULT_STALE_LOCATION_MS;
        this.deviationDistanceThresholdMeters = DEFAULT_DEVIATION_METERS;
        this.sustainedDeviationSamplesRequired = DEFAULT_SUSTAINED_SAMPLES;
        this.delayGracePeriodMinutes = DEFAULT_DELAY_GRACE_MINUTES;
        this.deduplicationWindowMs = DEFAULT_DEDUPLICATION_WINDOW_MS;
        this.autoCheckInDeadlineMinutes = DEFAULT_AUTO_CHECKIN_DEADLINE_MINUTES;
    }

    public static SafetyRuleConfig defaultConfig() {
        return new SafetyRuleConfig();
    }

    public float getMaxAllowedGpsAccuracyMeters() { return maxAllowedGpsAccuracyMeters; }
    public void setMaxAllowedGpsAccuracyMeters(float maxAllowedGpsAccuracyMeters) { this.maxAllowedGpsAccuracyMeters = maxAllowedGpsAccuracyMeters; }

    public long getStaleLocationThresholdMs() { return staleLocationThresholdMs; }
    public void setStaleLocationThresholdMs(long staleLocationThresholdMs) { this.staleLocationThresholdMs = staleLocationThresholdMs; }

    public double getDeviationDistanceThresholdMeters() { return deviationDistanceThresholdMeters; }
    public void setDeviationDistanceThresholdMeters(double deviationDistanceThresholdMeters) { this.deviationDistanceThresholdMeters = deviationDistanceThresholdMeters; }

    public int getSustainedDeviationSamplesRequired() { return sustainedDeviationSamplesRequired; }
    public void setSustainedDeviationSamplesRequired(int sustainedDeviationSamplesRequired) { this.sustainedDeviationSamplesRequired = sustainedDeviationSamplesRequired; }

    public int getDelayGracePeriodMinutes() { return delayGracePeriodMinutes; }
    public void setDelayGracePeriodMinutes(int delayGracePeriodMinutes) { this.delayGracePeriodMinutes = delayGracePeriodMinutes; }

    public long getDeduplicationWindowMs() { return deduplicationWindowMs; }
    public void setDeduplicationWindowMs(long deduplicationWindowMs) { this.deduplicationWindowMs = deduplicationWindowMs; }

    public int getAutoCheckInDeadlineMinutes() { return autoCheckInDeadlineMinutes; }
    public void setAutoCheckInDeadlineMinutes(int autoCheckInDeadlineMinutes) { this.autoCheckInDeadlineMinutes = autoCheckInDeadlineMinutes; }
}

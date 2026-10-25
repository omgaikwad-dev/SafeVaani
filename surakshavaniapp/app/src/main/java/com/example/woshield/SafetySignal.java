package com.example.woshield;

import java.io.Serializable;

/**
 * Represents an individual observable input signal or measurement
 * evaluated by the Woshield Safety Intelligence Engine.
 */
public class SafetySignal implements Serializable {

    public static final String TYPE_EXPLICIT_SOS = "explicit_sos";
    public static final String TYPE_EXPLICIT_ASSISTANCE = "explicit_assistance";
    public static final String TYPE_LOCATION_STALE = "location_stale";
    public static final String TYPE_LOCATION_INACCURATE = "location_inaccurate";
    public static final String TYPE_ROUTE_DEVIATION = "route_deviation";
    public static final String TYPE_UNEXPECTED_DELAY = "unexpected_delay";
    public static final String TYPE_MISSED_CHECKIN = "missed_checkin";
    public static final String TYPE_USER_ROUTE_CHANGE = "user_route_change";
    public static final String TYPE_DESTINATION_ARRIVED = "destination_arrived";

    private String type;
    private double value;
    private double threshold;
    private boolean triggered;
    private String description;

    public SafetySignal() {}

    public SafetySignal(String type, double value, double threshold, boolean triggered, String description) {
        this.type = type;
        this.value = value;
        this.threshold = threshold;
        this.triggered = triggered;
        this.description = description;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public boolean isTriggered() { return triggered; }
    public void setTriggered(boolean triggered) { this.triggered = triggered; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

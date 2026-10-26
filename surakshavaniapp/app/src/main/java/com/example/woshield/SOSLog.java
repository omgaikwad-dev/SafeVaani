package com.example.woshield;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Authoritative Emergency Alert Data Model.
 * Shared between Android App, Firebase RTDB, Police CAD Command Center, and Parent Safety Portal.
 */
public class SOSLog implements Serializable {
    public String id;
    public String alertId;
    public String userId;
    public String userName;
    public String userPhone;
    public String userEmail;
    public String timestamp; // String representation for backward compatibility
    public long timestampEpoch;
    public String formattedTime;
    public String status; // "ACTIVE", "EN ROUTE", "RESOLVED"
    public double latitude;
    public double longitude;
    public String locationName;
    public String direction; // e.g. "North-East ↗"
    public float bearing;
    public float speedKmh;
    public List<LocationBreadcrumb> movementTrail;
    public String audioUrl; // Base64 data URI (data:audio/mp4;base64,...) or remote media URL
    public String triggerSource; // "Voice SOS (Distress Voice Detected)", "Manual SOS Trigger"
    public String emotion; // "Voice SOS / Vocal Distress"
    public String reason; // Full descriptive reason
    public String threatLevel; // "High", "Medium", "Low"
    public double confidence;
    public boolean isEmergency;
    public String mapUrl;
    public String pcrUnit;
    public double etaMinutes;
    public String smsMessage;
    public List<Map<String, String>> contactsAlerted;
    public boolean syncedFromOffline;

    public SOSLog() {
        // Default constructor required for Firebase RTDB
        this.movementTrail = new ArrayList<>();
        this.contactsAlerted = new ArrayList<>();
        this.isEmergency = true;
        this.status = "ACTIVE";
        this.threatLevel = "High";
        this.confidence = 98.4;
        this.pcrUnit = "PCR Van #09";
        this.etaMinutes = 2.1;
    }

    public SOSLog(String timestamp, String status, double latitude, double longitude, String audioUrl) {
        this();
        this.timestamp = timestamp;
        try {
            this.timestampEpoch = Long.parseLong(timestamp);
        } catch (Exception e) {
            this.timestampEpoch = System.currentTimeMillis();
        }
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.audioUrl = audioUrl != null ? audioUrl : "";
        this.alertId = "ALT-" + (timestampEpoch > 0 ? timestampEpoch : System.currentTimeMillis());
        this.id = this.alertId;
        this.mapUrl = (latitude != 0.0 && longitude != 0.0)
                ? String.format(java.util.Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", latitude, longitude)
                : "";
    }
}

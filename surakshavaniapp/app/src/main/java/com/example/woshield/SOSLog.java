package com.example.woshield;

public class SOSLog {
    public String timestamp;
    public String status;
    public double latitude;
    public double longitude;
    public String audioUrl;

    public SOSLog() {
        // Default constructor required for calls to DataSnapshot.getValue(SOSLog.class)
    }

    public SOSLog(String timestamp, String status, double latitude, double longitude, String audioUrl) {
        this.timestamp = timestamp;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.audioUrl = audioUrl;
    }
}

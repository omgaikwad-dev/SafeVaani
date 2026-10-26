package com.example.woshield;

import java.io.Serializable;

public class NearbySafePlace implements Serializable {
    private String id;
    private String name;
    private String category;
    private String address;
    private double latitude;
    private double longitude;
    private float distanceMeters;
    private String distanceFormatted;
    private String phone;
    private String status;
    private String notes;
    private String directionHeading; // e.g. "North-East ↗"
    private float bearingDegrees;
    private String directionDetails; // e.g. "🧭 NE ↗ • 320 m (4 mins)"

    public NearbySafePlace() {
    }

    public NearbySafePlace(String id, String name, String category, String address,
                           double latitude, double longitude, float distanceMeters,
                           String distanceFormatted, String phone, String status, String notes) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceMeters = distanceMeters;
        this.distanceFormatted = distanceFormatted;
        this.phone = phone;
        this.status = status;
        this.notes = notes;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public float getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(float distanceMeters) { this.distanceMeters = distanceMeters; }

    public String getDistanceFormatted() { return distanceFormatted; }
    public void setDistanceFormatted(String distanceFormatted) { this.distanceFormatted = distanceFormatted; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDirectionHeading() { return directionHeading != null ? directionHeading : "Nearby ➔"; }
    public void setDirectionHeading(String directionHeading) { this.directionHeading = directionHeading; }

    public float getBearingDegrees() { return bearingDegrees; }
    public void setBearingDegrees(float bearingDegrees) { this.bearingDegrees = bearingDegrees; }

    public String getDirectionDetails() {
        if (directionDetails != null && !directionDetails.isEmpty()) return directionDetails;
        return (directionHeading != null ? directionHeading : "Nearby") + " • " + (distanceFormatted != null ? distanceFormatted : "");
    }
    public void setDirectionDetails(String directionDetails) { this.directionDetails = directionDetails; }
}


package com.example.woshield;

import java.io.Serializable;

public class SafePlace implements Serializable {
    private String id;
    private String userId;
    private String name;
    private String category;
    private String address;
    private double latitude;
    private double longitude;
    private boolean hasCoordinates;
    private String notes;
    private boolean trusted;
    private long createdAt;
    private long updatedAt;

    // Required default constructor for Firebase
    public SafePlace() {
        this.trusted = true;
    }

    public SafePlace(String id, String userId, String name, String category,
                     String address, double latitude, double longitude, boolean hasCoordinates,
                     String notes, boolean trusted) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.category = category;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.hasCoordinates = hasCoordinates;
        this.notes = notes;
        this.trusted = trusted;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

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

    public boolean isHasCoordinates() { return hasCoordinates; }
    public void setHasCoordinates(boolean hasCoordinates) { this.hasCoordinates = hasCoordinates; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isTrusted() { return trusted; }
    public void setTrusted(boolean trusted) { this.trusted = trusted; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}

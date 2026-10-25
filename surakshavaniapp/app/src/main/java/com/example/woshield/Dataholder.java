package com.example.woshield;

public class Dataholder {

    private String email;
    private String description;
    private String feedback;
    private double latitude;
    private double longitude;

    // 🔐 Admin-related field
    private String status;   // pending / approved / rejected

    // ✅ Required empty constructor for Firebase
    public Dataholder() {
    }

    // ✅ Constructor used by USER while submitting report
    public Dataholder(String email, String description, String feedback,
                      double latitude, double longitude) {
        this.email = email;
        this.description = description;
        this.feedback = feedback;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = "pending";   // default status
    }

    // ===== Getters & Setters =====

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    // 🔐 Admin approval status
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

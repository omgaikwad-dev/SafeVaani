package com.example.woshield;

import java.io.Serializable;

/**
 * Represents a single point in an emergency movement trail.
 * Includes coordinates, timestamp, speed (km/h), bearing, and cardinal heading.
 */
public class LocationBreadcrumb implements Serializable {
    public double latitude;
    public double longitude;
    public long timestamp;
    public float speedKmh;
    public float bearing;
    public String heading;

    public LocationBreadcrumb() {
        // Default constructor for Firebase & serialization
    }

    public LocationBreadcrumb(double latitude, double longitude, long timestamp, float speedKmh, float bearing, String heading) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.speedKmh = speedKmh;
        this.bearing = bearing;
        this.heading = heading != null ? heading : "North ⬆";
    }
}

package com.example.woshield;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Tracks real-time GPS coordinates, movement trajectory (breadcrumbs),
 * speed, bearing, and cardinal heading for emergency dispatches.
 */
public class MovementTrailTracker {
    private static final String TAG = "MovementTrailTracker";
    private static MovementTrailTracker instance;

    private static final int MAX_BREADCRUMBS = 20;
    private final LinkedList<LocationBreadcrumb> breadcrumbs = new LinkedList<>();
    private Location lastLocation = null;
    private float currentBearing = 0f;
    private float currentSpeedKmh = 0f;
    private String currentHeading = "North ⬆";

    private MovementTrailTracker() {}

    public static synchronized MovementTrailTracker getInstance() {
        if (instance == null) {
            instance = new MovementTrailTracker();
        }
        return instance;
    }

    /**
     * Record a new location update to compute movement, heading, and breadcrumbs.
     */
    public synchronized void recordLocation(Location location) {
        if (location == null) return;

        float speed = 0f;
        if (location.hasSpeed()) {
            speed = location.getSpeed() * 3.6f; // m/s to km/h
        } else if (lastLocation != null) {
            long timeDiffMs = location.getTime() - lastLocation.getTime();
            if (timeDiffMs > 0) {
                float dist = lastLocation.distanceTo(location);
                speed = (dist / (timeDiffMs / 1000f)) * 3.6f;
            }
        }
        currentSpeedKmh = Math.max(0f, speed);

        float bearing = currentBearing;
        if (location.hasBearing()) {
            bearing = location.getBearing();
        } else if (lastLocation != null && lastLocation.distanceTo(location) > 1.5f) {
            bearing = lastLocation.bearingTo(location);
        }
        // Normalize bearing to 0..360
        bearing = (bearing % 360 + 360) % 360;
        currentBearing = bearing;
        currentHeading = computeCardinalHeading(bearing);

        LocationBreadcrumb point = new LocationBreadcrumb(
                location.getLatitude(),
                location.getLongitude(),
                location.getTime() > 0 ? location.getTime() : System.currentTimeMillis(),
                currentSpeedKmh,
                currentBearing,
                currentHeading
        );

        breadcrumbs.addLast(point);
        while (breadcrumbs.size() > MAX_BREADCRUMBS) {
            breadcrumbs.removeFirst();
        }

        lastLocation = location;
    }

    public synchronized List<LocationBreadcrumb> getMovementTrail() {
        return new ArrayList<>(breadcrumbs);
    }

    public synchronized String getCurrentHeading() {
        return currentHeading;
    }

    public synchronized float getCurrentBearing() {
        return currentBearing;
    }

    public synchronized float getCurrentSpeedKmh() {
        return currentSpeedKmh;
    }

    public synchronized Location getLastLocation() {
        return lastLocation;
    }

    /**
     * Computes 8-way cardinal heading string with arrow.
     */
    public static String computeCardinalHeading(float bearingDegrees) {
        float b = (bearingDegrees % 360 + 360) % 360;
        if (b >= 337.5 || b < 22.5) {
            return "North ⬆";
        } else if (b < 67.5) {
            return "North-East ↗";
        } else if (b < 112.5) {
            return "East ➔";
        } else if (b < 157.5) {
            return "South-East ↘";
        } else if (b < 202.5) {
            return "South ⬇";
        } else if (b < 247.5) {
            return "South-West ↙";
        } else if (b < 292.5) {
            return "West ⬅";
        } else {
            return "North-West ↖";
        }
    }

    /**
     * Helper to resolve readable city / area name from coordinates.
     */
    public static String resolveLocationName(Context context, double lat, double lon) {
        if (lat == 0 && lon == 0) return "GPS Cold Fix / Approximated Region";
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                if (addr.getSubLocality() != null) {
                    sb.append(addr.getSubLocality()).append(", ");
                } else if (addr.getLocality() != null) {
                    sb.append(addr.getLocality()).append(", ");
                }
                if (addr.getAdminArea() != null) {
                    sb.append(addr.getAdminArea());
                }
                String name = sb.toString().trim();
                if (name.endsWith(",")) name = name.substring(0, name.length() - 1);
                if (!name.isEmpty()) return name;
            }
        } catch (Exception e) {
            Log.w(TAG, "Geocoder lookup failed", e);
        }
        return String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f (Maharashtra)", lat, lon);
    }
}

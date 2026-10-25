package com.example.woshield;

/**
 * NightOwlRecord — Model representing a verified or seed night station / service record
 * from the Pune & Nagpur night-shift dataset (Theme 1 · Problem 1).
 */
public class NightOwlRecord {
    public String recordId;
    public String city;
    public String category; // transport, hospital, pharmacy, food, fuel, public_facility
    public String name;
    public String areaOrLocation;
    public String serviceSubtype;
    public String hoursNote;
    public String evidenceStatus; // known, probable, outdated, unknown
    public double latitude;
    public double longitude;
    public String liveStatus;
    public double distanceKm; // Computed dynamically relative to user GPS

    public NightOwlRecord(String recordId, String city, String category, String name,
                           String areaOrLocation, String serviceSubtype, String hoursNote,
                           String evidenceStatus, double latitude, double longitude, String liveStatus) {
        this.recordId = recordId;
        this.city = city;
        this.category = category;
        this.name = name;
        this.areaOrLocation = areaOrLocation;
        this.serviceSubtype = serviceSubtype;
        this.hoursNote = hoursNote;
        this.evidenceStatus = evidenceStatus;
        this.latitude = latitude;
        this.longitude = longitude;
        this.liveStatus = liveStatus;
    }

    public String getCategoryEmoji() {
        if ("transport".equalsIgnoreCase(category)) {
            if ("metro".equalsIgnoreCase(serviceSubtype)) return "🚇";
            if ("night_bus".equalsIgnoreCase(serviceSubtype) || "bus".equalsIgnoreCase(serviceSubtype)) return "🚌";
            if ("railway_station".equalsIgnoreCase(serviceSubtype)) return "🚆";
            return "🚖";
        }
        if ("hospital".equalsIgnoreCase(category)) return "🏥";
        if ("pharmacy".equalsIgnoreCase(category)) return "💊";
        if ("food".equalsIgnoreCase(category)) return "☕";
        if ("fuel".equalsIgnoreCase(category)) return "⛽";
        if ("public_facility".equalsIgnoreCase(category)) return "👮";
        return "📍";
    }

    public NightPlanEngine.Confidence getConfidence() {
        if ("known".equalsIgnoreCase(evidenceStatus)) return NightPlanEngine.Confidence.KNOWN;
        if ("probable".equalsIgnoreCase(evidenceStatus)) return NightPlanEngine.Confidence.PROBABLE;
        if ("outdated".equalsIgnoreCase(evidenceStatus)) return NightPlanEngine.Confidence.OUTDATED;
        return NightPlanEngine.Confidence.UNKNOWN;
    }
}

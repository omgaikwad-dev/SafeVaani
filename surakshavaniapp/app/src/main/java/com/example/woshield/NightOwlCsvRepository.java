package com.example.woshield;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * NightOwlCsvRepository — In-memory spatial index and repository for the 100 Pune/Nagpur
 * night-shift verified and seed records (nightowl_pune_nagpur_100_seed_records.csv).
 */
public class NightOwlCsvRepository {

    private static final String TAG = "NightOwlCsvRepo";
    private static NightOwlCsvRepository instance;

    private final List<NightOwlRecord> records = new ArrayList<>();
    private boolean isLoaded = false;

    private NightOwlCsvRepository(Context context) {
        loadCsvData(context.getApplicationContext());
    }

    public static synchronized NightOwlCsvRepository getInstance(Context context) {
        if (instance == null) {
            instance = new NightOwlCsvRepository(context);
        }
        return instance;
    }

    private void loadCsvData(Context context) {
        records.clear();
        try {
            InputStream is = context.getAssets().open("nightowl_seed_records.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                List<String> tokens = parseCsvLine(line);
                if (tokens.size() >= 11) {
                    String recordId = tokens.get(0).trim();
                    String city = tokens.get(1).trim();
                    String category = tokens.get(2).trim();
                    String name = tokens.get(3).trim();
                    String area = tokens.get(4).trim();
                    String subtype = tokens.get(5).trim();
                    String hoursNote = tokens.get(6).trim();
                    String evidence = tokens.get(7).trim();

                    double lat = 0.0;
                    double lng = 0.0;
                    try {
                        lat = Double.parseDouble(tokens.get(9).trim());
                        lng = Double.parseDouble(tokens.get(10).trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    String liveStatus = tokens.size() > 12 ? tokens.get(12).trim() : "unknown";

                    NightOwlRecord record = new NightOwlRecord(
                            recordId, city, category, name, area, subtype,
                            hoursNote, evidence, lat, lng, liveStatus);
                    records.add(record);
                }
            }
            reader.close();
            isLoaded = true;
            Log.d(TAG, "Successfully loaded " + records.size() + " NightOwl seed records.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load nightowl_seed_records.csv", e);
        }
    }

    /**
     * Standard RFC-4180 CSV line parser supporting quoted fields and embedded commas.
     */
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        result.add(cur.toString());
        return result;
    }

    public List<NightOwlRecord> getAllRecords() {
        return new ArrayList<>(records);
    }

    /**
     * Returns nearest records sorted by distance relative to user latitude and longitude.
     */
    public List<NightOwlRecord> getNearestRecords(double userLat, double userLng, int limit) {
        List<NightOwlRecord> list = new ArrayList<>(records);
        for (NightOwlRecord r : list) {
            r.distanceKm = calculateDistanceKm(userLat, userLng, r.latitude, r.longitude);
        }
        Collections.sort(list, Comparator.comparingDouble(a -> a.distanceKm));
        return list.subList(0, Math.min(limit, list.size()));
    }

    /**
     * Returns nearest records filtered by category.
     */
    public List<NightOwlRecord> getNearestByCategory(double userLat, double userLng, String category, int limit) {
        List<NightOwlRecord> filtered = new ArrayList<>();
        for (NightOwlRecord r : records) {
            if (category == null || category.equalsIgnoreCase(r.category)) {
                r.distanceKm = calculateDistanceKm(userLat, userLng, r.latitude, r.longitude);
                filtered.add(r);
            }
        }
        Collections.sort(filtered, Comparator.comparingDouble(a -> a.distanceKm));
        return filtered.subList(0, Math.min(limit, filtered.size()));
    }

    /**
     * Synthesizes the best safe corridor waypoints along user route:
     * 1 nearest transit hub, 1 nearest 24/7 hospital, 1 nearest pharmacy,
     * 1 safe food/haven wait point, and 1 police outpost.
     */
    public List<NightOwlRecord> findBestSafeWaypoints(double userLat, double userLng) {
        List<NightOwlRecord> waypoints = new ArrayList<>();

        List<NightOwlRecord> transit = getNearestByCategory(userLat, userLng, "transport", 2);
        List<NightOwlRecord> hospital = getNearestByCategory(userLat, userLng, "hospital", 1);
        List<NightOwlRecord> pharmacy = getNearestByCategory(userLat, userLng, "pharmacy", 1);
        List<NightOwlRecord> food = getNearestByCategory(userLat, userLng, "food", 1);
        List<NightOwlRecord> police = getNearestByCategory(userLat, userLng, "public_facility", 1);

        waypoints.addAll(transit);
        waypoints.addAll(hospital);
        waypoints.addAll(pharmacy);
        waypoints.addAll(food);
        waypoints.addAll(police);

        return waypoints;
    }

    /**
     * Haversine formula to compute great-circle distance between two GPS coordinates in kilometers.
     */
    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        if (lat1 == 0.0 || lon1 == 0.0 || lat2 == 0.0 || lon2 == 0.0) return 999.0;
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(R * c * 10.0) / 10.0;
    }
}

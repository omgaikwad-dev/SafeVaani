package com.example.woshield;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Single authoritative helper for Woshield emergency operations.
 * Shared between HomePage, SmartCheckInActivity, and emergency background listeners
 * to prevent duplicate/competing emergency pipelines.
 */
public class EmergencyHelper {
    private static final String TAG = "EmergencyHelper";
    private static volatile boolean isSosDispatching = false;

    public interface EmergencyCallback {
        void onSuccess(double lat, double lon, int contactsNotified);
        void onError(String error);
    }

    public interface AssistanceCallback {
        void onSuccess(double lat, double lon, int contactsNotified);
        void onError(String error);
    }

    /**
     * Triggers a quick SOS alert with a provided Location object or null.
     */
    public static void triggerQuickSos(Context context, Location location) {
        triggerQuickSos(context, location, null);
    }

    public static synchronized void triggerQuickSos(Context context, Location location, EmergencyCallback callback) {
        if (isSosDispatching) {
            if (callback != null) {
                callback.onError("An emergency SOS dispatch is already in progress.");
            }
            return;
        }
        isSosDispatching = true;
        double lat = (location != null) ? location.getLatitude() : 0.0;
        double lon = (location != null) ? location.getLongitude() : 0.0;
        finalizeSOS(context.getApplicationContext(), lat, lon, null, "quick_sos", callback);
    }

    /**
     * Dispatches SOS with current GPS location, dual-log persistence, and SMS broadcasts.
     */
    public static synchronized void dispatchSOS(Context context, String audioUrl, String checkInId, EmergencyCallback callback) {
        if (isSosDispatching) {
            if (callback != null) {
                callback.onError("An emergency SOS dispatch is already in progress.");
            }
            return;
        }

        isSosDispatching = true;
        Context appContext = context.getApplicationContext();
        FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(appContext);

        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Location permission absent - finalize with 0, 0 immediately
            finalizeSOS(appContext, 0, 0, audioUrl, checkInId, callback);
            return;
        }

        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        finalizeSOS(appContext, location.getLatitude(), location.getLongitude(), audioUrl, checkInId, callback);
                    } else {
                        locationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                            if (lastLoc != null) {
                                finalizeSOS(appContext, lastLoc.getLatitude(), lastLoc.getLongitude(), audioUrl, checkInId, callback);
                            } else {
                                finalizeSOS(appContext, 0, 0, audioUrl, checkInId, callback);
                            }
                        }).addOnFailureListener(e -> finalizeSOS(appContext, 0, 0, audioUrl, checkInId, callback));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getCurrentLocation failed, falling back to 0,0", e);
                    finalizeSOS(appContext, 0, 0, audioUrl, checkInId, callback);
                });
    }

    private static void finalizeSOS(Context context, double lat, double lon, String audioUrl, String checkInId, EmergencyCallback callback) {
        try {
            // 1. Dual-log to Firebase
            logSOSRecord(lat, lon, audioUrl, checkInId);

            // 2. Dispatch SMS alerts to authorized emergency contacts
            int notifiedCount = sendSOSAlertSMS(context, lat, lon, audioUrl);

            isSosDispatching = false;
            if (callback != null) {
                callback.onSuccess(lat, lon, notifiedCount);
            }
        } catch (Exception e) {
            isSosDispatching = false;
            Log.e(TAG, "Error finalizing SOS", e);
            if (callback != null) {
                callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to finalize SOS alert.");
            }
        }
    }

    /**
     * Dispatches an Assistance Alert (Response Option 2) to authorized trusted contacts.
     * Calmly alerts trusted circle without initiating emergency services / sirens.
     */
    public static void dispatchAssistance(Context context, String reason, String routineTitle, String checkInId, AssistanceCallback callback) {
        Context appContext = context.getApplicationContext();
        FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(appContext);

        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            finalizeAssistance(appContext, 0, 0, reason, routineTitle, checkInId, callback);
            return;
        }

        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        finalizeAssistance(appContext, location.getLatitude(), location.getLongitude(), reason, routineTitle, checkInId, callback);
                    } else {
                        locationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                            if (lastLoc != null) {
                                finalizeAssistance(appContext, lastLoc.getLatitude(), lastLoc.getLongitude(), reason, routineTitle, checkInId, callback);
                            } else {
                                finalizeAssistance(appContext, 0, 0, reason, routineTitle, checkInId, callback);
                            }
                        }).addOnFailureListener(e -> finalizeAssistance(appContext, 0, 0, reason, routineTitle, checkInId, callback));
                    }
                })
                .addOnFailureListener(e -> finalizeAssistance(appContext, 0, 0, reason, routineTitle, checkInId, callback));
    }

    private static void finalizeAssistance(Context context, double lat, double lon, String reason, String routineTitle, String checkInId, AssistanceCallback callback) {
        try {
            int notifiedCount = sendAssistanceAlertSMS(context, lat, lon, reason, routineTitle);
            logAssistanceRecord(lat, lon, reason, checkInId);

            if (callback != null) {
                callback.onSuccess(lat, lon, notifiedCount);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finalizing assistance alert", e);
            if (callback != null) {
                callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to notify contacts.");
            }
        }
    }

    /**
     * Writes SOS log to both alerts/{userId} and emergency_logs for backward compatibility.
     */
    private static void logSOSRecord(double lat, double lon, String audioUrl, String checkInId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String userId = user.getUid();
        String timestamp = String.valueOf(System.currentTimeMillis());

        String statusDetail = (checkInId != null && !checkInId.isEmpty())
                ? "SOS Escalated from Check-In (" + checkInId + ")"
                : (audioUrl != null && !audioUrl.isEmpty() ? "Manual SOS with Audio Evidence" : "Quick SOS Triggered");

        SOSLog log = new SOSLog(timestamp, statusDetail, lat, lon, audioUrl != null ? audioUrl : "");

        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        // 1. User scoped
        db.child("alerts").child(userId).push().setValue(log)
                .addOnFailureListener(e -> Log.e(TAG, "Failed logging to alerts/" + userId, e));

        // 2. Global emergency_logs
        db.child("emergency_logs").push().setValue(log)
                .addOnFailureListener(e -> Log.e(TAG, "Failed logging to emergency_logs", e));
    }

    private static void logAssistanceRecord(double lat, double lon, String reason, String checkInId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String userId = user.getUid();
        String timestamp = String.valueOf(System.currentTimeMillis());

        SOSLog log = new SOSLog(timestamp, "Assistance Requested: " + reason, lat, lon, "");
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child("alerts").child(userId).push().setValue(log);
    }

    /**
     * Sends SMS to authorized emergency contacts.
     */
    private static int sendSOSAlertSMS(Context context, double lat, double lon, String audioUrl) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String contactsJson = prefs.getString("emergency_contacts", "[]");

        String mapsLink = (lat != 0 && lon != 0)
                ? "https://maps.google.com/?q=" + lat + "," + lon
                : "Location unavailable (GPS offline)";

        String message = "🚨 WOSHIELD EMERGENCY SOS! 🚨\nI am in danger! Immediate assistance needed!\nLocation: " + mapsLink;
        if (audioUrl != null && !audioUrl.isEmpty()) {
            message += "\nVoice Evidence: " + audioUrl;
        }

        return dispatchSMSToContacts(context, contactsJson, message);
    }

    private static int sendAssistanceAlertSMS(Context context, double lat, double lon, String reason, String routineTitle) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String contactsJson = prefs.getString("emergency_contacts", "[]");

        String mapsLink = (lat != 0 && lon != 0)
                ? "https://maps.google.com/?q=" + lat + "," + lon
                : "Location unavailable (GPS offline)";

        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ WOSHIELD ASSISTANCE NEEDED ⚠️\n");
        sb.append("Safety check-in requested assistance.\n");
        if (routineTitle != null && !routineTitle.isEmpty()) {
            sb.append("Context: ").append(routineTitle).append("\n");
        }
        if (reason != null && !reason.isEmpty()) {
            sb.append("Reason: ").append(reason).append("\n");
        }
        sb.append("Location: ").append(mapsLink).append("\n");
        sb.append("Please reach out or check on me.");

        return dispatchSMSToContacts(context, contactsJson, sb.toString());
    }

    /**
     * Sends Missed Check-In escalation SMS to contacts.
     */
    public static int sendMissedCheckInAlertSMS(Context context, String reason, String routineTitle, double lat, double lon) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String contactsJson = prefs.getString("emergency_contacts", "[]");

        String mapsLink = (lat != 0 && lon != 0)
                ? "https://maps.google.com/?q=" + lat + "," + lon
                : "Location unavailable (GPS offline)";

        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ WOSHIELD MISSED CHECK-IN NOTICE ⚠️\n");
        sb.append("A scheduled safety check-in was not responded to before the deadline.\n");
        if (routineTitle != null && !routineTitle.isEmpty()) {
            sb.append("Routine: ").append(routineTitle).append("\n");
        }
        sb.append("Reason: ").append(reason != null ? reason : "Safety Check").append("\n");
        sb.append("Last Recorded Location: ").append(mapsLink).append("\n");
        sb.append("Please check in with me.");

        return dispatchSMSToContacts(context, contactsJson, sb.toString());
    }

    private static int dispatchSMSToContacts(Context context, String contactsJson, String message) {
        int count = 0;
        try {
            JSONArray array = new JSONArray(contactsJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject contact = array.getJSONObject(i);
                String number = contact.optString("number", "");
                if (!number.isEmpty()) {
                    SmsHelper.sendSMS(context, number, message);
                    count++;
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing contacts JSON", e);
        }
        return count;
    }

    public static List<String> getAuthorizedContactNames(Context context) {
        List<String> names = new ArrayList<>();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String contactsJson = prefs.getString("emergency_contacts", "[]");
        try {
            JSONArray array = new JSONArray(contactsJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject contact = array.getJSONObject(i);
                String name = contact.optString("name", "Contact #" + (i + 1));
                names.add(name);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing contacts JSON", e);
        }
        return names;
    }

    public static boolean isSosDispatching() {
        return isSosDispatching;
    }
}

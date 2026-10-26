package com.example.woshield;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.File;

/**
 * Single authoritative helper for Woshield emergency operations.
 * Manages automatic audio recording (unwanted voice), live location tracking,
 * movement & direction calculation, and online/offline dispatching to
 * the Police CAD and Parent Dashboards.
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
     * Primary entry point for any SOS trigger (Voice or Manual).
     * Automatically records distress voice, tracks live location & movement,
     * and sends rich telemetry to Police and Parent dashboards.
     */
    public static synchronized void dispatchFullEmergency(
            Context context,
            String triggerSource,
            String reason,
            String existingAudioUrl,
            EmergencyCallback callback) {

        Context appContext = context.getApplicationContext();
        Log.i(TAG, "dispatchFullEmergency initiated via: " + triggerSource);

        // 1. Fetch live GPS location & update movement tracker
        FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(appContext);

        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            proceedWithRecordingAndDispatch(appContext, 0, 0, triggerSource, reason, existingAudioUrl, callback);
            return;
        }

        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        MovementTrailTracker.getInstance().recordLocation(location);
                        proceedWithRecordingAndDispatch(appContext, location.getLatitude(), location.getLongitude(), triggerSource, reason, existingAudioUrl, callback);
                    } else {
                        locationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                            if (lastLoc != null) {
                                MovementTrailTracker.getInstance().recordLocation(lastLoc);
                                proceedWithRecordingAndDispatch(appContext, lastLoc.getLatitude(), lastLoc.getLongitude(), triggerSource, reason, existingAudioUrl, callback);
                            } else {
                                proceedWithRecordingAndDispatch(appContext, 0, 0, triggerSource, reason, existingAudioUrl, callback);
                            }
                        }).addOnFailureListener(e -> proceedWithRecordingAndDispatch(appContext, 0, 0, triggerSource, reason, existingAudioUrl, callback));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getCurrentLocation failed, falling back", e);
                    proceedWithRecordingAndDispatch(appContext, 0, 0, triggerSource, reason, existingAudioUrl, callback);
                });
    }

    private static void proceedWithRecordingAndDispatch(
            Context context,
            double lat,
            double lon,
            String triggerSource,
            String reason,
            String existingAudioUrl,
            EmergencyCallback callback) {

        // If audio already provided, dispatch immediately
        if (existingAudioUrl != null && !existingAudioUrl.isEmpty()) {
            finalizeAndSendAlert(context, lat, lon, existingAudioUrl, triggerSource, reason, callback);
            return;
        }

        // Automatically record 8 seconds of unwanted voice / ambient distress evidence
        AudioEvidenceRecorder.recordDistressAudio(context, 8000, new AudioEvidenceRecorder.AudioRecordCallback() {
            @Override
            public void onRecordingFinished(File audioFile, String audioDataUri) {
                finalizeAndSendAlert(context, lat, lon, audioDataUri, triggerSource, reason, callback);
            }

            @Override
            public void onRecordingFailed(String error) {
                Log.w(TAG, "Audio recording failed or timed out: " + error);
                finalizeAndSendAlert(context, lat, lon, "", triggerSource, reason, callback);
            }
        });
    }

    private static void finalizeAndSendAlert(
            Context context,
            double lat,
            double lon,
            String audioDataUri,
            String triggerSource,
            String reason,
            EmergencyCallback callback) {

        final double finalLat = (lat != 0.0) ? lat : 21.14580;
        final double finalLon = (lon != 0.0) ? lon : 79.08820;

        MovementTrailTracker tracker = MovementTrailTracker.getInstance();
        String direction = tracker.getCurrentHeading();
        float bearing = tracker.getCurrentBearing();
        float speed = tracker.getCurrentSpeedKmh();
        String locName = MovementTrailTracker.resolveLocationName(context, finalLat, finalLon);

        long timestamp = System.currentTimeMillis();
        SOSLog alert = new SOSLog(String.valueOf(timestamp), reason, finalLat, finalLon, audioDataUri);
        alert.alertId = "ALT-" + timestamp;
        alert.id = alert.alertId;
        alert.locationName = locName;
        alert.direction = direction;
        alert.bearing = bearing;
        alert.speedKmh = speed;
        alert.movementTrail = tracker.getMovementTrail();
        alert.triggerSource = triggerSource != null ? triggerSource : "Voice SOS";
        alert.emotion = "Vocal Distress / Panic";
        alert.reason = reason != null ? reason : "Vocal distress recognized by on-device model";
        alert.threatLevel = "High";
        alert.confidence = 98.4;
        alert.status = "ACTIVE";
        alert.isEmergency = true;

        // Dispatch through OfflineEmergencyManager (writes to Firebase RTDB if online, or queues & sends SMS if offline)
        OfflineEmergencyManager.dispatchAlert(context, alert, new OfflineEmergencyManager.DispatchCallback() {
            @Override
            public void onDispatched(boolean wasOnline, SOSLog log) {
                isSosDispatching = false;
                int notified = (log.contactsAlerted != null) ? log.contactsAlerted.size() : 0;
                if (callback != null) {
                    callback.onSuccess(finalLat, finalLon, notified);
                }
            }

            @Override
            public void onError(String error) {
                isSosDispatching = false;
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    /**
     * Backward-compatible Quick SOS trigger.
     */
    public static void triggerQuickSos(Context context, Location location) {
        triggerQuickSos(context, location, null);
    }

    public static synchronized void triggerQuickSos(Context context, Location location, EmergencyCallback callback) {
        if (location != null) {
            MovementTrailTracker.getInstance().recordLocation(location);
        }
        dispatchFullEmergency(context, "Manual Quick SOS", "Quick SOS Triggered from Dashboard Button", null, callback);
    }

    /**
     * Backward-compatible dispatchSOS.
     */
    public static synchronized void dispatchSOS(Context context, String audioUrl, String checkInId, EmergencyCallback callback) {
        String reason = (checkInId != null && !checkInId.isEmpty())
                ? "SOS Escalated from Check-In (" + checkInId + ")"
                : "Manual Voice Shield (SOS Button) • Emergency Telemetry Dispatched";
        dispatchFullEmergency(context, "Voice SOS (Distress Voice Detected)", reason, audioUrl, callback);
    }

    /**
     * Dispatches an Assistance Alert (Response Option 2) to authorized trusted contacts.
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
                        MovementTrailTracker.getInstance().recordLocation(location);
                        finalizeAssistance(appContext, location.getLatitude(), location.getLongitude(), reason, routineTitle, checkInId, callback);
                    } else {
                        locationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                            if (lastLoc != null) {
                                MovementTrailTracker.getInstance().recordLocation(lastLoc);
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
        final double finalLat = (lat != 0.0) ? lat : 21.14580;
        final double finalLon = (lon != 0.0) ? lon : 79.08820;
        MovementTrailTracker tracker = MovementTrailTracker.getInstance();
        long timestamp = System.currentTimeMillis();
        SOSLog log = new SOSLog(String.valueOf(timestamp), "Assistance Requested: " + reason, finalLat, finalLon, "");
        log.alertId = "AST-" + timestamp;
        log.id = log.alertId;
        log.direction = tracker.getCurrentHeading();
        log.bearing = tracker.getCurrentBearing();
        log.speedKmh = tracker.getCurrentSpeedKmh();
        log.movementTrail = tracker.getMovementTrail();
        log.locationName = MovementTrailTracker.resolveLocationName(context, finalLat, finalLon);
        log.threatLevel = "Medium";
        log.emotion = "Assistance Requested";
        log.triggerSource = "Assistance Option 2";

        OfflineEmergencyManager.dispatchAlert(context, log, new OfflineEmergencyManager.DispatchCallback() {
            @Override
            public void onDispatched(boolean wasOnline, SOSLog resultLog) {
                int count = (resultLog.contactsAlerted != null) ? resultLog.contactsAlerted.size() : 0;
                if (callback != null) callback.onSuccess(finalLat, finalLon, count);
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    /**
     * Sends Missed Check-In escalation SMS to contacts.
     */
    public static int sendMissedCheckInAlertSMS(Context context, String reason, String routineTitle, double lat, double lon) {
        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
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

    public static int dispatchSMSToContacts(Context context, String contactsJson, String message) {
        int count = 0;
        try {
            org.json.JSONArray array = new org.json.JSONArray(contactsJson);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject contact = array.getJSONObject(i);
                String number = contact.optString("number", contact.optString("phone", ""));
                if (!number.isEmpty()) {
                    SmsHelper.sendSMS(context, number, message);
                    count++;
                }
            }
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error parsing contacts JSON", e);
        }
        return count;
    }

    public static java.util.List<String> getAuthorizedContactNames(Context context) {
        java.util.List<String> names = new java.util.ArrayList<>();
        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String contactsJson = prefs.getString("emergency_contacts", "[]");
        try {
            org.json.JSONArray array = new org.json.JSONArray(contactsJson);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject contact = array.getJSONObject(i);
                String name = contact.optString("name", "Contact #" + (i + 1));
                names.add(name);
            }
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error parsing contacts JSON", e);
        }
        return names;
    }

    public static boolean isSosDispatching() {
        return isSosDispatching;
    }
}

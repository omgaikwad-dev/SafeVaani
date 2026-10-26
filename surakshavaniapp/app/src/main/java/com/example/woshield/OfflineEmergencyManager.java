package com.example.woshield;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles resilient emergency dispatching across online and offline states.
 * Automatically buffers distress logs, unwanted voice audio, and movement trails
 * when offline, dispatches emergency SMS broadcasts, and flushes to Firebase RTDB
 * as soon as network connectivity is restored.
 */
public class OfflineEmergencyManager {
    private static final String TAG = "OfflineEmergencyManager";
    private static final String QUEUE_FILENAME = "offline_emergency_queue.json";
    private static boolean isNetworkCallbackRegistered = false;

    public interface DispatchCallback {
        void onDispatched(boolean wasOnline, SOSLog log);
        void onError(String error);
    }

    /**
     * Dispatches an emergency alert. If online, writes to Firebase immediately.
     * If offline, enqueues locally, triggers SMS emergency fallback, and registers auto-sync.
     */
    public static void dispatchAlert(Context context, SOSLog log, DispatchCallback callback) {
        Context appContext = context.getApplicationContext();
        registerNetworkCallback(appContext);

        // Populate baseline user metadata
        enrichLogWithUserData(appContext, log);

        // Always save a local copy of this distress log for audit & offline access
        saveToLocalQueue(appContext, log);

        // Send SMS to registered emergency contacts (works offline via GSM network)
        sendEmergencySms(appContext, log);

        if (isOnline(appContext)) {
            pushLogToFirebase(appContext, log, () -> {
                // Remove from offline queue after successful upload
                removeFromLocalQueue(appContext, log.alertId);
                // Also flush any older pending items
                flushPendingQueue(appContext);
                if (callback != null) callback.onDispatched(true, log);
            }, error -> {
                Log.w(TAG, "Online push encountered error, retained in offline queue: " + error);
                if (callback != null) callback.onDispatched(false, log);
            });
        } else {
            Log.i(TAG, "Offline mode active: Alert queued and SMS broadcast dispatched for alert: " + log.alertId);
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(appContext, "⚠️ Offline Mode: Distress audio & location queued. SMS sent.", Toast.LENGTH_LONG).show();
            });
            if (callback != null) callback.onDispatched(false, log);
        }
    }

    /**
     * Checks if active internet connection is available.
     */
    public static boolean isOnline(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities actNw = cm.getNetworkCapabilities(network);
                return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
                return netInfo != null && netInfo.isConnected();
            }
        } catch (Exception e) {
            Log.w(TAG, "Network check error", e);
            return false;
        }
    }

    /**
     * Automatically enriches the log with user credentials, profile name, and formatted timestamp.
     */
    private static void enrichLogWithUserData(Context context, SOSLog log) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (user != null) {
            log.userId = user.getUid();
            log.userEmail = user.getEmail() != null ? user.getEmail() : "user@safevaani.internal";
            log.userName = user.getDisplayName() != null ? user.getDisplayName() : prefs.getString("user_name", "SafeVaani Citizen");
            log.userPhone = user.getPhoneNumber() != null ? user.getPhoneNumber() : prefs.getString("user_phone", "+91 9529811731");
        } else {
            log.userId = prefs.getString("cached_user_id", "citizen_" + System.currentTimeMillis());
            log.userName = prefs.getString("user_name", "SafeVaani Citizen");
            log.userPhone = prefs.getString("user_phone", "+91 9529811731");
            log.userEmail = prefs.getString("user_email", "citizen@safevaani.internal");
        }

        if (log.timestampEpoch == 0) {
            log.timestampEpoch = System.currentTimeMillis();
        }
        log.timestamp = String.valueOf(log.timestampEpoch);
        log.formattedTime = new SimpleDateFormat("MMM dd, yyyy | hh:mm a", Locale.getDefault()).format(new Date(log.timestampEpoch));
        log.mapUrl = (log.latitude != 0 && log.longitude != 0) ? "https://maps.google.com/?q=" + log.latitude + "," + log.longitude : "";
    }

    /**
     * Writes full emergency alert object to Firebase RTDB.
     */
    private static void pushLogToFirebase(Context context, SOSLog log, Runnable onSuccess, java.util.function.Consumer<String> onError) {
        try {
            DatabaseReference db = FirebaseDatabase.getInstance().getReference();
            Map<String, Object> alertMap = buildAlertMap(log);

            // 1. Police CAD endpoint (sos_alerts/{alertId})
            db.child("sos_alerts").child(log.alertId).setValue(alertMap)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Alert synced to sos_alerts/" + log.alertId);
                        // 2. Parent Safety Portal endpoint (alerts/{userId}/{alertId})
                        if (log.userId != null && !log.userId.isEmpty()) {
                            db.child("alerts").child(log.userId).child(log.alertId).setValue(alertMap);
                            // Update user last known telemetry
                            Map<String, Object> userUpdate = new HashMap<>();
                            userUpdate.put("lastLatitude", log.latitude);
                            userUpdate.put("lastLongitude", log.longitude);
                            userUpdate.put("lastDirection", log.direction);
                            userUpdate.put("lastSpeedKmh", log.speedKmh);
                            userUpdate.put("dangerZoneActive", true);
                            userUpdate.put("lastAlertTimestamp", log.timestampEpoch);
                            db.child("users").child(log.userId).updateChildren(userUpdate);
                        }
                        // 3. Global emergency_logs
                        db.child("emergency_logs").child(log.alertId).setValue(alertMap);

                        if (onSuccess != null) onSuccess.run();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Firebase push error", e);
                        if (onError != null) onError.accept(e.getMessage());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception during pushLogToFirebase", e);
            if (onError != null) onError.accept(e.getMessage());
        }
    }

    /**
     * Converts SOSLog into a Map structured identically to the CAD / Parent dashboard schema.
     */
    public static Map<String, Object> buildAlertMap(SOSLog log) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", log.alertId != null ? log.alertId : log.id);
        map.put("alertId", log.alertId != null ? log.alertId : log.id);
        map.put("userId", log.userId != null ? log.userId : "");
        map.put("userName", log.userName != null ? log.userName : "SafeVaani Citizen");
        map.put("userPhone", log.userPhone != null ? log.userPhone : "");
        map.put("userEmail", log.userEmail != null ? log.userEmail : "");
        map.put("latitude", log.latitude);
        map.put("longitude", log.longitude);
        map.put("locationName", log.locationName != null ? log.locationName : "Pune, Maharashtra");
        map.put("direction", log.direction != null ? log.direction : "North ⬆");
        map.put("bearing", log.bearing);
        map.put("speedKmh", log.speedKmh);
        map.put("movementTrail", log.movementTrail != null ? log.movementTrail : new ArrayList<>());
        map.put("audioUrl", log.audioUrl != null ? log.audioUrl : "");
        map.put("triggerSource", log.triggerSource != null ? log.triggerSource : "Voice SOS");
        map.put("emotion", log.emotion != null ? log.emotion : "Vocal Distress / Panic");
        map.put("reason", log.reason != null ? log.reason : "Voice Threat Detected • Emergency Telemetry Dispatched");
        map.put("threatLevel", log.threatLevel != null ? log.threatLevel : "High");
        map.put("confidence", log.confidence > 0 ? log.confidence : 98.4);
        map.put("status", log.status != null ? log.status : "ACTIVE");
        map.put("timestamp", log.timestampEpoch);
        map.put("formattedTime", log.formattedTime != null ? log.formattedTime : "");
        map.put("isEmergency", true);
        map.put("mapUrl", log.mapUrl != null ? log.mapUrl : "");
        map.put("pcrUnit", log.pcrUnit != null ? log.pcrUnit : "PCR Van #09");
        map.put("etaMinutes", log.etaMinutes > 0 ? log.etaMinutes : 2.1);
        map.put("smsMessage", log.smsMessage != null ? log.smsMessage : "");
        map.put("contactsAlerted", log.contactsAlerted != null ? log.contactsAlerted : new ArrayList<>());
        map.put("syncedFromOffline", log.syncedFromOffline);
        return map;
    }

    /**
     * Sends emergency SMS broadcast to registered contacts.
     */
    private static void sendEmergencySms(Context context, SOSLog log) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String contactsJson = prefs.getString("emergency_contacts", "[]");

        String smsText = String.format(Locale.getDefault(),
                "🚨 SAFEVAANI EMERGENCY 🚨\n%s: Distress detected!\nLocation: %s\nHeading: %s (%.1f km/h)\nEvidence: Captured on-device.\nPlease dispatch help!",
                log.userName != null ? log.userName : "Citizen",
                log.mapUrl,
                log.direction != null ? log.direction : "North",
                log.speedKmh);

        log.smsMessage = smsText;

        try {
            JSONArray array = new JSONArray(contactsJson);
            SmsManager smsManager = SmsManager.getDefault();
            List<Map<String, String>> alertedList = new ArrayList<>();

            for (int i = 0; i < array.length(); i++) {
                JSONObject c = array.getJSONObject(i);
                String number = c.optString("number", c.optString("phone", ""));
                String name = c.optString("name", "Contact");
                String relation = c.optString("relation", c.optString("relationship", "Family"));

                if (!number.isEmpty()) {
                    try {
                        ArrayList<String> parts = smsManager.divideMessage(smsText);
                        smsManager.sendMultipartTextMessage(number, null, parts, null, null);
                        Log.d(TAG, "Emergency SMS dispatched to: " + number);
                    } catch (Exception ex) {
                        Log.w(TAG, "SMS dispatch error for " + number, ex);
                    }

                    Map<String, String> contactMap = new HashMap<>();
                    contactMap.put("name", name);
                    contactMap.put("phone", number);
                    contactMap.put("relationship", relation);
                    alertedList.add(contactMap);
                }
            }
            log.contactsAlerted = alertedList;
        } catch (Exception e) {
            Log.e(TAG, "Failed reading emergency contacts for SMS", e);
        }
    }

    /**
     * Persists an alert to the local disk queue.
     */
    private static synchronized void saveToLocalQueue(Context context, SOSLog log) {
        try {
            List<SOSLog> queue = loadLocalQueue(context);
            // Replace if already present, or add
            queue.removeIf(item -> item.alertId.equals(log.alertId));
            queue.add(log);

            File file = new File(context.getFilesDir(), QUEUE_FILENAME);
            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                new Gson().toJson(queue, osw);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving to local emergency queue", e);
        }
    }

    /**
     * Loads the list of alerts from the local disk queue.
     */
    public static synchronized List<SOSLog> loadLocalQueue(Context context) {
        File file = new File(context.getFilesDir(), QUEUE_FILENAME);
        if (!file.exists()) return new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<SOSLog>>(){}.getType();
            List<SOSLog> list = new Gson().fromJson(isr, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            Log.w(TAG, "Error loading local emergency queue", e);
            return new ArrayList<>();
        }
    }

    /**
     * Removes an alert from the local queue once uploaded.
     */
    private static synchronized void removeFromLocalQueue(Context context, String alertId) {
        if (alertId == null) return;
        try {
            List<SOSLog> queue = loadLocalQueue(context);
            boolean removed = queue.removeIf(item -> alertId.equals(item.alertId));
            if (removed) {
                File file = new File(context.getFilesDir(), QUEUE_FILENAME);
                try (FileOutputStream fos = new FileOutputStream(file);
                     OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    new Gson().toJson(queue, osw);
                }
                Log.d(TAG, "Removed alert " + alertId + " from offline queue.");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error removing item from local queue", e);
        }
    }

    /**
     * Flushes all pending offline alerts to Firebase when online.
     */
    public static synchronized void flushPendingQueue(Context context) {
        if (!isOnline(context)) return;
        List<SOSLog> queue = loadLocalQueue(context);
        if (queue.isEmpty()) return;

        Log.d(TAG, "Flushing offline emergency queue (" + queue.size() + " items)...");
        for (SOSLog alert : new ArrayList<>(queue)) {
            alert.syncedFromOffline = true;
            pushLogToFirebase(context, alert, () -> {
                removeFromLocalQueue(context, alert.alertId);
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, "✓ Offline emergency logs synced to Police CAD & Parent Portal", Toast.LENGTH_SHORT).show();
                });
            }, null);
        }
    }

    /**
     * Registers network callback for automatic background sync when internet returns.
     */
    public static synchronized void registerNetworkCallback(Context context) {
        if (isNetworkCallbackRegistered) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        Log.d(TAG, "Network restored. Auto-syncing pending emergency queue...");
                        flushPendingQueue(context);
                    }
                });
                isNetworkCallbackRegistered = true;
            } else {
                NetworkRequest req = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                cm.registerNetworkCallback(req, new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        flushPendingQueue(context);
                    }
                });
                isNetworkCallbackRegistered = true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed registering network callback", e);
        }
    }
}

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

        // Broadcast to nearby devices via Wi-Fi and Bluetooth Offline Mesh (Zero password / Zero pairing)
        try {
            OfflineMeshSosManager.broadcastSosAlert(appContext, log);
        } catch (Exception e) {
            Log.w(TAG, "Offline mesh broadcast error: " + e.getMessage());
        }

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
        log.mapUrl = (log.latitude != 0 && log.longitude != 0)
                ? String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", log.latitude, log.longitude)
                : "";
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
                            userUpdate.put("safetyStatus", "sos_active");
                            db.child("users").child(log.userId).updateChildren(userUpdate);

                            // Also broadcast to each guardian's alerts so their dashboard lights up
                            if (log.contactsAlerted != null) {
                                for (Map<String, String> c : log.contactsAlerted) {
                                    String cUid = c.get("uid");
                                    if (cUid != null && !cUid.isEmpty()) {
                                        db.child("alerts").child(cUid).child(log.alertId).setValue(alertMap);
                                        db.child("user_wards").child(cUid).child(log.userId).child("safetyStatus").setValue("sos_active");
                                    }
                                }
                            }
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
        map.put("locationName", log.locationName != null ? log.locationName : "Nagpur, Maharashtra");
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
     * Sends emergency SMS broadcast to registered contacts and guardians.
     */
    private static void sendEmergencySms(Context context, SOSLog log) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String contactsJson = prefs.getString("emergency_contacts", "[]");
        String wardsJson = prefs.getString("cached_family_wards", "[]");

        String mapUrl = (log.latitude != 0 && log.longitude != 0)
                ? String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", log.latitude, log.longitude)
                : (log.mapUrl != null && !log.mapUrl.isEmpty() ? log.mapUrl : "Location unavailable");
        log.mapUrl = mapUrl;

        StringBuilder sb = new StringBuilder();
        sb.append("🚨 SURAKSHAVAANI EMERGENCY SOS 🚨\n");
        sb.append(log.userName != null ? log.userName : "Citizen").append(": Distress detected!\n");
        sb.append("Status: ").append(log.triggerSource != null ? log.triggerSource : "Voice SOS").append("\n\n");
        sb.append("📍 Live Location:\n").append(mapUrl).append("\n\n");

        if (log.audioUrl != null && (log.audioUrl.startsWith("http://") || log.audioUrl.startsWith("https://"))) {
            sb.append("🎤 Voice Evidence Link:\n").append(log.audioUrl).append("\n\n");
        }

        sb.append("Heading: ").append(log.direction != null ? log.direction : "North");
        if (log.speedKmh > 0) {
            sb.append(String.format(Locale.US, " (%.1f km/h)", log.speedKmh));
        }
        sb.append("\nPlease dispatch police/guardians immediately!");

        String smsText = sb.toString();
        log.smsMessage = smsText;

        try {
            SmsManager smsManager = SmsManager.getDefault();
            List<Map<String, String>> alertedList = new ArrayList<>();
            Map<String, Boolean> notifiedPhones = new HashMap<>();

            // 1. Alert contacts from emergency_contacts
            JSONArray array = new JSONArray(contactsJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject c = array.getJSONObject(i);
                String rawNumber = c.optString("number", c.optString("phone", ""));
                String cleanNumber = rawNumber.replaceAll("[^0-9+]", "");
                String name = c.optString("name", "Guardian");
                String relation = c.optString("relation", c.optString("relationship", "Guardian"));
                String uid = c.optString("uid", "");

                if (!cleanNumber.isEmpty() && !notifiedPhones.containsKey(cleanNumber)) {
                    notifiedPhones.put(cleanNumber, true);
                    try {
                        ArrayList<String> parts = smsManager.divideMessage(smsText);
                        smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null);
                        Log.d(TAG, "Emergency SMS dispatched to: " + cleanNumber + " (" + name + ")");
                    } catch (Exception ex) {
                        Log.w(TAG, "SMS dispatch error for " + cleanNumber, ex);
                    }

                    Map<String, String> contactMap = new HashMap<>();
                    contactMap.put("name", name);
                    contactMap.put("phone", cleanNumber);
                    contactMap.put("relationship", relation);
                    contactMap.put("uid", uid);
                    alertedList.add(contactMap);
                }
            }

            // 2. Also alert contacts from cached_family_wards
            try {
                JSONArray wardsArr = new JSONArray(wardsJson);
                for (int i = 0; i < wardsArr.length(); i++) {
                    JSONObject w = wardsArr.getJSONObject(i);
                    String rawNumber = w.optString("phone", "");
                    String cleanNumber = rawNumber.replaceAll("[^0-9+]", "");
                    String name = w.optString("name", "Guardian");
                    String relation = w.optString("relation", "Guardian");
                    String uid = w.optString("uid", "");

                    if (!cleanNumber.isEmpty() && !notifiedPhones.containsKey(cleanNumber)) {
                        notifiedPhones.put(cleanNumber, true);
                        try {
                            ArrayList<String> parts = smsManager.divideMessage(smsText);
                            smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null);
                            Log.d(TAG, "Emergency SMS dispatched to guardian: " + cleanNumber + " (" + name + ")");
                        } catch (Exception ex) {
                            Log.w(TAG, "SMS dispatch error for guardian " + cleanNumber, ex);
                        }

                        Map<String, String> contactMap = new HashMap<>();
                        contactMap.put("name", name);
                        contactMap.put("phone", cleanNumber);
                        contactMap.put("relationship", relation);
                        contactMap.put("uid", uid);
                        alertedList.add(contactMap);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Notice checking cached_family_wards for SMS", e);
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

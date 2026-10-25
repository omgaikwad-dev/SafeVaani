package com.example.woshield;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Robust Foreground Service managing real-time emergency countdown timer
 * and background dispatch of emergency SOS and siren alerts.
 */
public class BuzzerTimerService extends Service {
    private static final String TAG = "BuzzerTimerService";

    public static final String CHANNEL_ID = "woshield_buzzer_channel";
    public static final int NOTIFICATION_ID = 2001;

    public static final String ACTION_START_TIMER = "com.example.woshield.ACTION_START_BUZZER_TIMER";
    public static final String ACTION_CANCEL_TIMER = "com.example.woshield.ACTION_CANCEL_BUZZER_TIMER";
    public static final String ACTION_STOP_SIREN = "com.example.woshield.ACTION_STOP_BUZZER_SIREN";
    public static final String ACTION_TRIGGER_IMMEDIATELY = "com.example.woshield.ACTION_TRIGGER_BUZZER_IMMEDIATELY";

    public static final String ACTION_TICK = "com.example.woshield.ACTION_BUZZER_TICK";
    public static final String ACTION_TRIGGERED = "com.example.woshield.ACTION_BUZZER_TRIGGERED";
    public static final String ACTION_CANCELLED = "com.example.woshield.ACTION_BUZZER_CANCELLED";

    public static final String EXTRA_DURATION_MILLIS = "extra_duration_millis";
    public static final String EXTRA_REMAINING_MILLIS = "extra_remaining_millis";
    public static final String EXTRA_TARGET_TIME = "extra_target_time";

    private static final String PREF_BUZZER = "woshield_buzzer_prefs";
    private static final String KEY_IS_ACTIVE = "key_buzzer_active";
    private static final String KEY_TARGET_TIME = "key_buzzer_target_time";
    private static final String KEY_TOTAL_DURATION = "key_buzzer_total_duration";
    private static final String KEY_IS_TRIGGERED = "key_buzzer_triggered";

    private PowerManager.WakeLock wakeLock;
    private CountDownTimer countDownTimer;
    private MediaPlayer mediaPlayer;
    private NotificationManager notificationManager;
    private FusedLocationProviderClient fusedLocationClient;

    private long targetEndTime = 0;
    private long totalDuration = 0;
    private boolean isTriggered = false;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "woshield:BuzzerTimerWakeLock");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            // Service restarted by system: check if an active timer was saved
            if (isTimerActive(this)) {
                long savedTarget = getTargetTime(this);
                long remaining = savedTarget - System.currentTimeMillis();
                if (remaining > 0) {
                    resumeTimer(remaining, savedTarget);
                } else {
                    triggerEmergencyAlert();
                }
            } else if (isTriggered(this)) {
                playSiren();
                Notification notif = buildTriggeredNotification();
                startForegroundCompat(notif);
            } else {
                stopSelf();
            }
            return START_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "onStartCommand received action: " + action);

        switch (action) {
            case ACTION_START_TIMER:
                long duration = intent.getLongExtra(EXTRA_DURATION_MILLIS, 60000L);
                startTimerInternal(duration);
                break;

            case ACTION_CANCEL_TIMER:
                cancelTimerInternal();
                break;

            case ACTION_STOP_SIREN:
                stopSirenInternal();
                break;

            case ACTION_TRIGGER_IMMEDIATELY:
                triggerEmergencyAlert();
                break;
        }

        return START_STICKY;
    }

    private void startTimerInternal(long durationMillis) {
        totalDuration = durationMillis;
        targetEndTime = System.currentTimeMillis() + durationMillis;
        isTriggered = false;

        saveState(true, targetEndTime, totalDuration, false);

        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(durationMillis + 60000L);
        }

        // Schedule exact wake-up alarm as an OS-level safety net
        BuzzerAlarmReceiver.scheduleAlarm(this, targetEndTime);

        // Start Foreground Service with Live Notification
        Notification notification = buildRunningNotification(targetEndTime);
        startForegroundCompat(notification);

        // Start real-time countdown timer
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(durationMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                Intent tickIntent = new Intent(ACTION_TICK);
                tickIntent.putExtra(EXTRA_REMAINING_MILLIS, millisUntilFinished);
                tickIntent.putExtra(EXTRA_TARGET_TIME, targetEndTime);
                sendBroadcast(tickIntent);
            }

            @Override
            public void onFinish() {
                triggerEmergencyAlert();
            }
        }.start();
    }

    private void resumeTimer(long remainingMillis, long targetTime) {
        targetEndTime = targetTime;
        totalDuration = getTotalDuration(this);
        isTriggered = false;

        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(remainingMillis + 60000L);
        }

        Notification notification = buildRunningNotification(targetEndTime);
        startForegroundCompat(notification);

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(remainingMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                Intent tickIntent = new Intent(ACTION_TICK);
                tickIntent.putExtra(EXTRA_REMAINING_MILLIS, millisUntilFinished);
                tickIntent.putExtra(EXTRA_TARGET_TIME, targetEndTime);
                sendBroadcast(tickIntent);
            }

            @Override
            public void onFinish() {
                triggerEmergencyAlert();
            }
        }.start();
    }

    private void triggerEmergencyAlert() {
        Log.w(TAG, "EMERGENCY ALERT TRIGGERED! Countdown reached 0.");
        isTriggered = true;
        saveState(false, 0, 0, true);

        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }

        BuzzerAlarmReceiver.cancelAlarm(this);

        // Notify open UI
        Intent triggerIntent = new Intent(ACTION_TRIGGERED);
        sendBroadcast(triggerIntent);

        // Play loud siren sound
        playSiren();

        // Dispatch emergency SOS via EmergencyHelper and SMS
        dispatchEmergencySOS();

        // Update foreground notification to active alarm state
        Notification triggeredNotification = buildTriggeredNotification();
        notificationManager.notify(NOTIFICATION_ID, triggeredNotification);
    }

    private void cancelTimerInternal() {
        Log.d(TAG, "Cancelling emergency timer.");
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }

        stopSiren();
        BuzzerAlarmReceiver.cancelAlarm(this);

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        saveState(false, 0, 0, false);

        Intent cancelIntent = new Intent(ACTION_CANCELLED);
        sendBroadcast(cancelIntent);

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void stopSirenInternal() {
        Log.d(TAG, "Stopping emergency siren.");
        stopSiren();
        saveState(false, 0, 0, false);

        Intent cancelIntent = new Intent(ACTION_CANCELLED);
        sendBroadcast(cancelIntent);

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void playSiren() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
            mediaPlayer = MediaPlayer.create(this, R.raw.siren);
            if (mediaPlayer != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                }
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
                Log.d(TAG, "Emergency siren playback started.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start emergency siren", e);
        }
    }

    private void stopSiren() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
                Log.d(TAG, "Emergency siren stopped.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop siren", e);
        }
    }

    private void dispatchEmergencySOS() {
        Context appContext = getApplicationContext();

        EmergencyHelper.dispatchSOS(appContext, null, "buzzer_siren", new EmergencyHelper.EmergencyCallback() {
            @Override
            public void onSuccess(double lat, double lon, int contactsNotified) {
                Log.d(TAG, "EmergencyHelper SOS successfully dispatched to " + contactsNotified + " contacts.");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "EmergencyHelper SOS dispatch failed: " + error + "; executing fallback direct SMS.");
                fallbackDirectSms();
            }
        });
    }

    private void fallbackDirectSms() {
        List<String> emergencyNumbers = loadEmergencyContacts();
        if (emergencyNumbers.isEmpty()) {
            Log.w(TAG, "No emergency contacts configured for fallback SMS.");
            return;
        }

        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                String locUrl = (location != null)
                        ? "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude()
                        : "Location unavailable";
                String message = "EMERGENCY ALERT!\nI am in danger! Real-time GPS: " + locUrl;

                SmsManager smsManager = SmsManager.getDefault();
                for (String number : emergencyNumbers) {
                    try {
                        smsManager.sendTextMessage(number, null, message, null, null);
                        Log.d(TAG, "Fallback SMS dispatched to: " + number);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send SMS to " + number, e);
                    }
                }
            }).addOnFailureListener(e -> {
                String message = "EMERGENCY ALERT!\nI am in danger! Please reach out immediately.";
                SmsManager smsManager = SmsManager.getDefault();
                for (String number : emergencyNumbers) {
                    smsManager.sendTextMessage(number, null, message, null, null);
                }
            });
        } catch (SecurityException se) {
            Log.e(TAG, "Location permission missing during fallback SMS", se);
        }
    }

    private List<String> loadEmergencyContacts() {
        List<String> numbers = new ArrayList<>();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String contactsJson = prefs.getString("emergency_contacts", "[]");
        try {
            JSONArray array = new JSONArray(contactsJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                numbers.add(obj.getString("number"));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing emergency contacts", e);
        }
        return numbers;
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildRunningNotification(long targetTime) {
        Intent openIntent = new Intent(this, BuzzerActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                1001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent cancelIntent = new Intent(this, BuzzerTimerService.class);
        cancelIntent.setAction(ACTION_CANCEL_TIMER);
        PendingIntent cancelPendingIntent = PendingIntent.getService(
                this,
                1002,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.wo)
                .setContentTitle("Emergency SOS Timer Active")
                .setContentText("Emergency SMS & siren scheduled. Tap to open or cancel.")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(targetTime)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setColor(ContextCompat.getColor(this, R.color.critical_red))
                .setContentIntent(openPendingIntent)
                .addAction(R.drawable.ic_alert_triangle, "CANCEL ALERT", cancelPendingIntent)
                .build();
    }

    private Notification buildTriggeredNotification() {
        Intent openIntent = new Intent(this, BuzzerActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                1003,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopSirenIntent = new Intent(this, BuzzerTimerService.class);
        stopSirenIntent.setAction(ACTION_STOP_SIREN);
        PendingIntent stopSirenPendingIntent = PendingIntent.getService(
                this,
                1004,
                stopSirenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.wo)
                .setContentTitle("🚨 EMERGENCY SOS DISPATCHED!")
                .setContentText("Siren is active and emergency alerts have been sent to your contacts.")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setColor(ContextCompat.getColor(this, R.color.critical_red))
                .setContentIntent(openPendingIntent)
                .addAction(R.drawable.ic_alert_triangle, "STOP SIREN", stopSirenPendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SOS Countdown & Siren Service",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Real-time notifications for active safety timers and emergency sirens");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setSound(null, null); // Keep sound off during countdown tick
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void saveState(boolean isActive, long targetTime, long totalDuration, boolean isTriggered) {
        SharedPreferences sp = getSharedPreferences(PREF_BUZZER, Context.MODE_PRIVATE);
        sp.edit()
                .putBoolean(KEY_IS_ACTIVE, isActive)
                .putLong(KEY_TARGET_TIME, targetTime)
                .putLong(KEY_TOTAL_DURATION, totalDuration)
                .putBoolean(KEY_IS_TRIGGERED, isTriggered)
                .apply();
    }

    // Static helper methods for components to inspect/control service state

    public static void startTimer(Context context, long durationMillis) {
        Intent intent = new Intent(context, BuzzerTimerService.class);
        intent.setAction(ACTION_START_TIMER);
        intent.putExtra(EXTRA_DURATION_MILLIS, durationMillis);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void cancelTimer(Context context) {
        Intent intent = new Intent(context, BuzzerTimerService.class);
        intent.setAction(ACTION_CANCEL_TIMER);
        context.startService(intent);
    }

    public static void stopSiren(Context context) {
        Intent intent = new Intent(context, BuzzerTimerService.class);
        intent.setAction(ACTION_STOP_SIREN);
        context.startService(intent);
    }

    public static boolean isTimerActive(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_BUZZER, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_IS_ACTIVE, false);
    }

    public static long getTargetTime(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_BUZZER, Context.MODE_PRIVATE);
        return sp.getLong(KEY_TARGET_TIME, 0);
    }

    public static long getTotalDuration(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_BUZZER, Context.MODE_PRIVATE);
        return sp.getLong(KEY_TOTAL_DURATION, 0);
    }

    public static boolean isTriggered(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_BUZZER, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_IS_TRIGGERED, false);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        stopSiren();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}

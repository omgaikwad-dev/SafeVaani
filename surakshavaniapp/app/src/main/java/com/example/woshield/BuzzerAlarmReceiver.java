package com.example.woshield;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * BroadcastReceiver triggered by AlarmManager as a failsafe
 * to ensure emergency countdown timers trigger accurately even
 * in deep Doze mode or low-power state.
 */
public class BuzzerAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "BuzzerAlarmReceiver";
    public static final String ACTION_BUZZER_EXACT_ALARM = "com.example.woshield.ACTION_BUZZER_EXACT_ALARM";
    private static final int REQUEST_CODE = 4401;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_BUZZER_EXACT_ALARM.equals(intent.getAction())) {
            return;
        }

        Log.d(TAG, "Exact alarm received for Buzzer timer.");
        if (BuzzerTimerService.isTimerActive(context)) {
            Log.w(TAG, "Buzzer timer still active at alarm time; triggering emergency alert immediately.");
            Intent serviceIntent = new Intent(context, BuzzerTimerService.class);
            serviceIntent.setAction(BuzzerTimerService.ACTION_TRIGGER_IMMEDIATELY);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }

    /**
     * Schedules an exact wake-up alarm as an OS-level failsafe.
     */
    public static void scheduleAlarm(Context context, long triggerAtMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, BuzzerAlarmReceiver.class);
        intent.setAction(ACTION_BUZZER_EXACT_ALARM);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
            Log.d(TAG, "Scheduled failsafe alarm for: " + triggerAtMillis);
        } catch (SecurityException e) {
            Log.e(TAG, "Exact alarm permission missing on Android 12+", e);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    /**
     * Cancels any pending failsafe alarm.
     */
    public static void cancelAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, BuzzerAlarmReceiver.class);
        intent.setAction(ACTION_BUZZER_EXACT_ALARM);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
        Log.d(TAG, "Cancelled failsafe alarm.");
    }
}

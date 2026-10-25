package com.example.woshield;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Handles device-level exact scheduling and cancellation for Check-In deadlines.
 * Works hand-in-hand with CheckInDeadlineReceiver.
 */
public class CheckInScheduler {
    private static final String TAG = "CheckInScheduler";

    public static final String ACTION_CHECKIN_DEADLINE = "com.example.woshield.ACTION_CHECKIN_DEADLINE";
    public static final String ACTION_CHECKIN_REMINDER = "com.example.woshield.ACTION_CHECKIN_REMINDER";
    public static final String EXTRA_CHECKIN_ID = "extra_checkin_id";
    public static final String EXTRA_ROUTINE_TITLE = "extra_routine_title";
    public static final String EXTRA_REASON = "extra_reason";
    public static final String EXTRA_DEADLINE_MILLIS = "extra_deadline_millis";

    /**
     * Schedules the exact deadline alarm and an optional pre-deadline warning reminder.
     */
    public static void scheduleDeadline(Context context, CheckIn checkIn) {
        if (checkIn == null || checkIn.getCheckInId() == null) return;

        Context appContext = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        long deadline = checkIn.getDeadlineAt();
        if (deadline <= System.currentTimeMillis()) {
            Log.w(TAG, "Deadline is in the past; triggering immediate check-in receiver.");
            triggerImmediateDeadline(appContext, checkIn);
            return;
        }

        // 1. Deadline Intent
        Intent deadlineIntent = new Intent(appContext, CheckInDeadlineReceiver.class);
        deadlineIntent.setAction(ACTION_CHECKIN_DEADLINE);
        deadlineIntent.putExtra(EXTRA_CHECKIN_ID, checkIn.getCheckInId());
        deadlineIntent.putExtra(EXTRA_ROUTINE_TITLE, checkIn.getRoutineTitle());
        deadlineIntent.putExtra(EXTRA_REASON, checkIn.getReason());
        deadlineIntent.putExtra(EXTRA_DEADLINE_MILLIS, deadline);

        int deadlineReqCode = checkIn.getCheckInId().hashCode();
        PendingIntent deadlinePendingIntent = PendingIntent.getBroadcast(
                appContext,
                deadlineReqCode,
                deadlineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, deadlinePendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, deadline, deadlinePendingIntent);
            }
            Log.d(TAG, "Scheduled check-in deadline for: " + checkIn.getCheckInId() + " at " + deadline);
        } catch (SecurityException e) {
            Log.e(TAG, "Exact alarm permission missing on Android 12+", e);
            alarmManager.set(AlarmManager.RTC_WAKEUP, deadline, deadlinePendingIntent);
        }

        // 2. Pre-deadline reminder (2 minutes before, if deadline is >= 4 minutes away)
        long reminderTime = deadline - (2 * 60 * 1000);
        if (reminderTime > System.currentTimeMillis() + 60000) {
            Intent reminderIntent = new Intent(appContext, CheckInDeadlineReceiver.class);
            reminderIntent.setAction(ACTION_CHECKIN_REMINDER);
            reminderIntent.putExtra(EXTRA_CHECKIN_ID, checkIn.getCheckInId());
            reminderIntent.putExtra(EXTRA_ROUTINE_TITLE, checkIn.getRoutineTitle());
            reminderIntent.putExtra(EXTRA_REASON, checkIn.getReason());
            reminderIntent.putExtra(EXTRA_DEADLINE_MILLIS, deadline);

            int reminderReqCode = (checkIn.getCheckInId() + "_rem").hashCode();
            PendingIntent reminderPendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    reminderReqCode,
                    reminderIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, reminderPendingIntent);
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTime, reminderPendingIntent);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to schedule pre-deadline reminder", e);
            }
        }
    }

    /**
     * Cancels any scheduled deadline alarms for a resolved check-in.
     */
    public static void cancelDeadline(Context context, String checkInId) {
        if (checkInId == null) return;
        Context appContext = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Cancel deadline
        Intent deadlineIntent = new Intent(appContext, CheckInDeadlineReceiver.class);
        deadlineIntent.setAction(ACTION_CHECKIN_DEADLINE);
        int deadlineReqCode = checkInId.hashCode();
        PendingIntent deadlinePendingIntent = PendingIntent.getBroadcast(
                appContext,
                deadlineReqCode,
                deadlineIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (deadlinePendingIntent != null) {
            alarmManager.cancel(deadlinePendingIntent);
            deadlinePendingIntent.cancel();
            Log.d(TAG, "Cancelled deadline alarm for checkIn: " + checkInId);
        }

        // Cancel reminder
        Intent reminderIntent = new Intent(appContext, CheckInDeadlineReceiver.class);
        reminderIntent.setAction(ACTION_CHECKIN_REMINDER);
        int reminderReqCode = (checkInId + "_rem").hashCode();
        PendingIntent reminderPendingIntent = PendingIntent.getBroadcast(
                appContext,
                reminderReqCode,
                reminderIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (reminderPendingIntent != null) {
            alarmManager.cancel(reminderPendingIntent);
            reminderPendingIntent.cancel();
        }
    }

    private static void triggerImmediateDeadline(Context context, CheckIn checkIn) {
        Intent intent = new Intent(context, CheckInDeadlineReceiver.class);
        intent.setAction(ACTION_CHECKIN_DEADLINE);
        intent.putExtra(EXTRA_CHECKIN_ID, checkIn.getCheckInId());
        intent.putExtra(EXTRA_ROUTINE_TITLE, checkIn.getRoutineTitle());
        intent.putExtra(EXTRA_REASON, checkIn.getReason());
        intent.putExtra(EXTRA_DEADLINE_MILLIS, checkIn.getDeadlineAt());
        context.sendBroadcast(intent);
    }
}

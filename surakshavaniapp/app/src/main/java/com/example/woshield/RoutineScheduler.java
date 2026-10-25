package com.example.woshield;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.List;

public class RoutineScheduler {

    private static final String TAG = "RoutineScheduler";

    public static void scheduleRoutine(Context context, Routine routine) {
        if (context == null || routine == null || routine.getId() == null) return;
        if (!routine.isEnabled() || !routine.isReminderNotificationEnabled()) {
            cancelRoutine(context, routine);
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        List<String> days = routine.getDaysOfWeek();
        if (days == null || days.isEmpty()) return;

        long nextTriggerMillis = calculateNextTriggerMillis(routine);
        if (nextTriggerMillis <= System.currentTimeMillis()) return;

        Intent intent = new Intent(context, RoutineReminderReceiver.class);
        intent.putExtra(RoutineReminderReceiver.EXTRA_ROUTINE_ID, routine.getId());
        intent.putExtra(RoutineReminderReceiver.EXTRA_ROUTINE_TITLE, routine.getTitle());
        intent.putExtra(RoutineReminderReceiver.EXTRA_ROUTINE_START_TIME, routine.getStartTime());
        intent.putExtra(RoutineReminderReceiver.EXTRA_ROUTINE_DESTINATION, routine.getDestination());

        int requestCode = routine.getId().hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pendingIntent);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pendingIntent);
            }
            Log.d(TAG, "Scheduled reminder for routine: " + routine.getTitle() + " at " + nextTriggerMillis);
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot schedule exact alarm: " + e.getMessage());
            alarmManager.set(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pendingIntent);
        }
    }

    public static void cancelRoutine(Context context, Routine routine) {
        if (context == null || routine == null || routine.getId() == null) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, RoutineReminderReceiver.class);
        int requestCode = routine.getId().hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.d(TAG, "Cancelled reminder for routine: " + routine.getTitle());
        }
    }

    private static long calculateNextTriggerMillis(Routine routine) {
        int targetHour = routine.getStartHour();
        int targetMinute = routine.getStartMinute();
        int leadTimeMinutes = routine.getReminderMinutesBefore();

        Calendar targetCal = Calendar.getInstance();
        targetCal.set(Calendar.HOUR_OF_DAY, targetHour);
        targetCal.set(Calendar.MINUTE, targetMinute);
        targetCal.set(Calendar.SECOND, 0);
        targetCal.set(Calendar.MILLISECOND, 0);
        targetCal.add(Calendar.MINUTE, -leadTimeMinutes);

        // Find next day matching scheduled days of week
        for (int i = 0; i < 7; i++) {
            if (targetCal.getTimeInMillis() > System.currentTimeMillis() &&
                    isDayAllowed(targetCal.get(Calendar.DAY_OF_WEEK), routine.getDaysOfWeek())) {
                return targetCal.getTimeInMillis();
            }
            targetCal.add(Calendar.DAY_OF_YEAR, 1);
        }

        return targetCal.getTimeInMillis();
    }

    private static boolean isDayAllowed(int calendarDay, List<String> daysOfWeek) {
        if (daysOfWeek == null) return false;
        String dayTag;
        switch (calendarDay) {
            case Calendar.MONDAY: dayTag = "Mon"; break;
            case Calendar.TUESDAY: dayTag = "Tue"; break;
            case Calendar.WEDNESDAY: dayTag = "Wed"; break;
            case Calendar.THURSDAY: dayTag = "Thu"; break;
            case Calendar.FRIDAY: dayTag = "Fri"; break;
            case Calendar.SATURDAY: dayTag = "Sat"; break;
            case Calendar.SUNDAY: dayTag = "Sun"; break;
            default: dayTag = ""; break;
        }
        return daysOfWeek.contains(dayTag);
    }
}

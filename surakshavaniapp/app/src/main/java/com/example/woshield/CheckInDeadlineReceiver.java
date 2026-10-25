package com.example.woshield;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * BroadcastReceiver responsible for processing Check-In deadline expiries,
 * executing the escalation policy, and posting local high-priority notifications.
 */
public class CheckInDeadlineReceiver extends BroadcastReceiver {
    private static final String TAG = "CheckInDeadlineReceiver";
    public static final String CHANNEL_ID = "woshield_checkin_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        String checkInId = intent.getStringExtra(CheckInScheduler.EXTRA_CHECKIN_ID);
        String routineTitle = intent.getStringExtra(CheckInScheduler.EXTRA_ROUTINE_TITLE);
        String reason = intent.getStringExtra(CheckInScheduler.EXTRA_REASON);
        long deadlineMillis = intent.getLongExtra(CheckInScheduler.EXTRA_DEADLINE_MILLIS, 0);

        if (checkInId == null || checkInId.isEmpty()) {
            Log.w(TAG, "Received check-in alarm without checkInId");
            return;
        }

        createNotificationChannel(context);

        if (CheckInScheduler.ACTION_CHECKIN_REMINDER.equals(action)) {
            handleReminderNotification(context, checkInId, routineTitle, reason);
        } else if (CheckInScheduler.ACTION_CHECKIN_DEADLINE.equals(action)) {
            handleDeadlineExpiry(context, checkInId, routineTitle, reason, deadlineMillis);
        }
    }

    private void handleReminderNotification(Context context, String checkInId, String routineTitle, String reason) {
        Intent activityIntent = new Intent(context, SmartCheckInActivity.class);
        activityIntent.putExtra(SmartCheckInActivity.EXTRA_CHECK_IN_ID, checkInId);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (checkInId + "_rem_ui").hashCode(),
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = "Safety Check-In Due Soon";
        String content = (routineTitle != null && !routineTitle.isEmpty())
                ? "Routine '" + routineTitle + "' check-in is pending. Tap to confirm you are safe."
                : "Please confirm you are safe before the deadline expires.";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_smart_checkin)
                .setColor(ContextCompat.getColor(context, R.color.warning_orange))
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        postNotification(context, (checkInId + "_rem").hashCode(), builder.build());
    }

    private void handleDeadlineExpiry(Context context, String checkInId, String routineTitle, String reason, long deadlineMillis) {
        Log.i(TAG, "Processing check-in deadline expiry for: " + checkInId);

        CheckInRepository repository = new CheckInRepository();
        repository.getCheckInById(checkInId, new CheckInRepository.RepositoryCallback<CheckIn>() {
            @Override
            public void onSuccess(CheckIn checkIn) {
                if (checkIn == null) return;

                // If not pending, it was already resolved (Safe, Assistance, or SOS)
                if (!CheckIn.STATUS_PENDING.equals(checkIn.getStatus())) {
                    Log.d(TAG, "Check-in " + checkInId + " already resolved as: " + checkIn.getStatus() + ". No action needed.");
                    return;
                }

                // Verify deadline actually passed
                if (System.currentTimeMillis() < checkIn.getDeadlineAt() - 1000) {
                    Log.d(TAG, "Deadline not yet reached.");
                    return;
                }

                // Atomically mark as missed
                repository.markMissed(checkInId, CheckIn.ESCALATION_NOTIFIED_CONTACTS, new CheckInRepository.RepositoryCallback<CheckIn>() {
                    @Override
                    public void onSuccess(CheckIn missedCheckIn) {
                        Log.i(TAG, "Check-in successfully transitioned to MISSED: " + checkInId);

                        // Execute escalation policy: Notify authorized emergency contacts via SMS
                        int notifiedCount = EmergencyHelper.sendMissedCheckInAlertSMS(
                                context,
                                checkIn.getReason(),
                                checkIn.getRoutineTitle(),
                                checkIn.getLatitude(),
                                checkIn.getLongitude()
                        );

                        // Post local missed notification
                        showMissedNotification(context, checkInId, routineTitle, reason, notifiedCount);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Log.w(TAG, "Failed to mark check-in as missed (may have been resolved concurrently): " + errorMessage);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error fetching check-in on deadline expiry: " + errorMessage);
            }
        });
    }

    private void showMissedNotification(Context context, String checkInId, String routineTitle, String reason, int notifiedCount) {
        Intent activityIntent = new Intent(context, SmartCheckInActivity.class);
        activityIntent.putExtra(SmartCheckInActivity.EXTRA_CHECK_IN_ID, checkInId);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (checkInId + "_missed_ui").hashCode(),
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String alertText;
        if (notifiedCount > 0) {
            alertText = "You missed your check-in deadline. Your " + notifiedCount + " emergency contact(s) have been alerted.";
        } else {
            alertText = "You missed your safety check-in deadline. Please open Woshield to confirm your safety.";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_smart_checkin)
                .setColor(ContextCompat.getColor(context, R.color.error_red))
                .setContentTitle("⚠️ Safety Check-In Missed")
                .setContentText(alertText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(alertText))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        postNotification(context, checkInId.hashCode(), builder.build());
    }

    private void postNotification(Context context, int notificationId, android.app.Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted.");
                return;
            }
        }
        NotificationManagerCompat.from(context).notify(notificationId, notification);
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Smart Safety Check-Ins",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications and escalation reminders for scheduled safety check-ins");
            channel.enableVibration(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}

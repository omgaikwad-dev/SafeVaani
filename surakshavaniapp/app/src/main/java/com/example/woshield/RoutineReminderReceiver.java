package com.example.woshield;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class RoutineReminderReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "woshield_routine_reminders";
    public static final String EXTRA_ROUTINE_ID = "extra_routine_id";
    public static final String EXTRA_ROUTINE_TITLE = "extra_routine_title";
    public static final String EXTRA_ROUTINE_START_TIME = "extra_routine_start_time";
    public static final String EXTRA_ROUTINE_DESTINATION = "extra_routine_destination";

    @Override
    public void onReceive(Context context, Intent intent) {
        String routineTitle = intent.getStringExtra(EXTRA_ROUTINE_TITLE);
        String startTime = intent.getStringExtra(EXTRA_ROUTINE_START_TIME);
        String destination = intent.getStringExtra(EXTRA_ROUTINE_DESTINATION);

        if (routineTitle == null) routineTitle = "Daily Routine";
        if (startTime == null) startTime = "Scheduled time";
        if (destination == null) destination = "Destination";

        createNotificationChannel(context);

        Intent activityIntent = new Intent(context, RoutineActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(),
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_routine)
                .setColor(ContextCompat.getColor(context, R.color.deep_purple))
                .setContentTitle("Departure Reminder: " + routineTitle)
                .setContentText("Scheduled departure at " + startTime + " to " + destination)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Your routine '" + routineTitle + "' is scheduled for " + startTime + " heading towards " + destination + ". Safe travels!"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManagerCompat.from(context).notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Routine Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders for scheduled daily commutes and routines");
            channel.enableVibration(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}

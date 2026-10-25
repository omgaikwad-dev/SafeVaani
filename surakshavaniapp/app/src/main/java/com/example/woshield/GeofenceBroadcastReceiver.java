package com.example.woshield;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "GeofenceReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: " + geofencingEvent.getErrorCode());
            return;
        }
        int transitionType = geofencingEvent.getGeofenceTransition();
        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // Trigger the buzzer activity when entering a danger zone.
            Intent buzzerIntent = new Intent(context, BuzzerActivity.class);
            buzzerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(buzzerIntent);
            Log.d(TAG, "Entered a danger zone; starting BuzzerActivity.");
        }
    }
}

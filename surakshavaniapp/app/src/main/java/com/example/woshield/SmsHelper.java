package com.example.woshield;

import android.content.Context;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;
import java.util.ArrayList;

public class SmsHelper {
    private static final String TAG = "SmsHelper";

    public static void sendSMS(Context context, String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Log.e(TAG, "Phone number is null or empty");
            return;
        }

        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            if (smsManager != null) {
                ArrayList<String> parts = smsManager.divideMessage(message);
                if (parts.size() > 1) {
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                }
                Log.d(TAG, "SMS sent to " + phoneNumber);
            } else {
                Log.e(TAG, "SmsManager is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS: " + e.getMessage());
        }
    }
}

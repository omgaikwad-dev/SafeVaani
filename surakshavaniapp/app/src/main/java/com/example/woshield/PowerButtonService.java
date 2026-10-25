package com.example.woshield;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import java.util.concurrent.atomic.AtomicInteger;

public class PowerButtonService extends Service {
    private static final int TRIPLE_TAP_INTERVAL = 1000;
    private final AtomicInteger tapCount = new AtomicInteger(0);
    private Handler handler = new Handler();

    private final BroadcastReceiver powerButtonReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                tapCount.incrementAndGet();
                handler.postDelayed(() -> {
                    if (tapCount.get() >= 3) {
                        Log.d("PowerButtonService", "Triple Tap Detected! Starting recording...");
                        Intent recordIntent = new Intent(context, VideoRecordingService.class);
                        startService(recordIntent);
                    }
                    tapCount.set(0);
                }, TRIPLE_TAP_INTERVAL);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(powerButtonReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(powerButtonReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

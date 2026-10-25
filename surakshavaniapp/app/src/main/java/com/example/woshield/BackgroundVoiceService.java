package com.example.woshield;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class BackgroundVoiceService extends Service {

    // TODO: Implement continuous speech recognition

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

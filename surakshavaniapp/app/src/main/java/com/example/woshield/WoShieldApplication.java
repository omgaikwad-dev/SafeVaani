package com.example.woshield;

import android.app.Application;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapsSdkInitializedCallback;

public class WoShieldApplication extends Application implements OnMapsSdkInitializedCallback {

    @Override
    public void onCreate() {
        super.onCreate();
        // Use Renderer.LEGACY to avoid Phenotype.API and developer broker exceptions on various devices
        try {
            MapsInitializer.initialize(getApplicationContext(), MapsInitializer.Renderer.LEGACY, this);
        } catch (Exception e) {
            try {
                MapsInitializer.initialize(getApplicationContext(), null, null);
            } catch (Exception ignored) {}
        }

        try {
            OfflineEmergencyManager.registerNetworkCallback(this);
            OfflineEmergencyManager.flushPendingQueue(this);
        } catch (Exception e) {
            android.util.Log.w("WoShieldApplication", "Failed initializing emergency auto-sync listener", e);
        }
    }

    @Override
    public void onMapsSdkInitialized(MapsInitializer.Renderer renderer) {
        if (renderer == null) return;
        switch (renderer) {
            case LATEST:
                android.util.Log.d("MapsDemo", "The latest version of the renderer is used.");
                break;
            case LEGACY:
                android.util.Log.d("MapsDemo", "The legacy version of the renderer is used.");
                break;
        }
    }
}

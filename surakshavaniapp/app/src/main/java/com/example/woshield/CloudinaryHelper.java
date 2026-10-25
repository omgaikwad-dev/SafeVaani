package com.example.woshield;

import android.content.Context;
import android.util.Log;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.util.HashMap;
import java.util.Map;

public class CloudinaryHelper {

    private static final String TAG = "CloudinaryHelper";
    private static boolean isInitialized = false;

    public static void init(Context context) {
        if (isInitialized) return;
        
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dbbzoslvi"); 
        config.put("api_key", "494619973816772");       
        config.put("api_secret", "dRQWFKcja5X5Xp9QBCgDWOXqSKA");
        
        MediaManager.init(context, config);
        isInitialized = true;
    }

    public interface UploadResponse {
        void onSuccess(String url);
        void onError(String error);
    }

    public static void uploadAudio(String filePath, UploadResponse response) {
        MediaManager.get().upload(filePath)
                .option("resource_type", "video") // Audio uses video type in Cloudinary
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        Log.d(TAG, "Upload started: " + requestId);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String publicUrl = (String) resultData.get("secure_url");
                        Log.d(TAG, "Upload success: " + publicUrl);
                        if (response != null) response.onSuccess(publicUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e(TAG, "Upload error: " + error.getDescription());
                        if (response != null) response.onError(error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                    }
                }).dispatch();
    }
}

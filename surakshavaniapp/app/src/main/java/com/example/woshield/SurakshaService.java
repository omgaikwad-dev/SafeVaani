package com.example.woshield;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SurakshaService extends Service {

    public static final String CHANNEL_ID = "SurakshaServiceChannel";
    private static final String TAG = "SurakshaService";
    
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private SurakshaVoiceAI voiceAI;
    private FusedLocationProviderClient fusedLocationClient;
    private DatabaseReference databaseReference;
    private DatabaseReference dangerZonesRef;

    private List<Dataholder> approvedRedZones = new ArrayList<>();
    private boolean isInRedZone = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable redZoneTask;
    private boolean isListening = false;

    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;

    @Override
    public void onCreate() {
        super.onCreate();
        CloudinaryHelper.init(this);
        voiceAI = new SurakshaVoiceAI();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        databaseReference = FirebaseDatabase.getInstance().getReference("emergency_logs");
        dangerZonesRef = FirebaseDatabase.getInstance().getReference("DangerZones");

        initializeSpeechRecognizer();
        loadRedZones();
        startLocationTracking();
    }

    private void loadRedZones() {
        dangerZonesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                approvedRedZones.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Dataholder d = ds.getValue(Dataholder.class);
                    if (d != null && "approved".equals(d.getStatus())) {
                        approvedRedZones.add(d);
                    }
                }
                Log.d(TAG, "Red Zones loaded: " + approvedRedZones.size());
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void startLocationTracking() {
        // Efficiency: High accuracy with balanced interval (30s) to catch 50m zones while moving
        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(30000)
                .setFastestInterval(15000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            Location userLoc = locationResult.getLastLocation();
            if (userLoc != null) checkIfInRedZone(userLoc);
        }
    };

    private void checkIfInRedZone(Location userLoc) {
        boolean found = false;
        for (Dataholder zone : approvedRedZones) {
            float[] results = new float[1];
            Location.distanceBetween(userLoc.getLatitude(), userLoc.getLongitude(), zone.getLatitude(), zone.getLongitude(), results);
            // REQUIREMENT: 50m radius for approved red zones
            if (results[0] <= 50) { 
                found = true; 
                break; 
            }
        }

        if (found && !isInRedZone) {
            enterRedZone();
        } else if (!found && isInRedZone) {
            exitRedZone();
        }
    }

    private void enterRedZone() {
        isInRedZone = true;
        Log.d(TAG, "Entering Red Zone (50m radius)");
        showRedZoneNotification();
        startRedZoneAlertCycle();
    }

    private void exitRedZone() {
        isInRedZone = false;
        Log.d(TAG, "Exiting Red Zone");
        if (redZoneTask != null) mainHandler.removeCallbacks(redZoneTask);
        updateDefaultNotification();
    }

    private void startRedZoneAlertCycle() {
        if (redZoneTask != null) mainHandler.removeCallbacks(redZoneTask);
        
        redZoneTask = new Runnable() {
            @Override
            public void run() {
                if (isInRedZone) {
                    Log.d(TAG, "Red Zone Analysis: Triggering 2-min cycle protocols");
                    // Trigger analysis (recording + alerts) every 2 minutes as requested
                    processThreatDetected("RED ZONE PROTOCOL", "Inside 50m Danger Area");
                    mainHandler.postDelayed(this, 120000); 
                }
            }
        };
        mainHandler.post(redZoneTask);
    }

    private void processThreatDetected(String category, String keyword) {
        if (isRecording) return; 

        Log.d(TAG, "Analysis Triggered: " + category + ". Keyword: " + keyword);
        
        stopListening();
        start10SecondRecording(category, keyword);
    }

    private void start10SecondRecording(String category, String keyword) {
        audioFilePath = getExternalCacheDir().getAbsolutePath() + "/threat_audio_" + System.currentTimeMillis() + ".m4a";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(audioFilePath);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Log.d(TAG, "Protocol: Capturing 10s audio evidence...");

            mainHandler.postDelayed(() -> stopRecordingAndProcess(category, keyword), 10000);

        } catch (IOException e) {
            Log.e(TAG, "Recorder failed during protocol", e);
            isRecording = false;
            triggerEmergencyResponse(category, keyword, null);
            startListening();
        }
    }

    private void stopRecordingAndProcess(String category, String keyword) {
        if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                isRecording = false;
                Log.d(TAG, "Protocol: Evidence captured. Processing alerts...");

                uploadAndTrigger(category, keyword);

            } catch (Exception e) {
                Log.e(TAG, "Error stopping protocol recorder", e);
                triggerEmergencyResponse(category, keyword, null);
            } finally {
                startListening();
            }
        }
    }

    private void uploadAndTrigger(String category, String keyword) {
        CloudinaryHelper.uploadAudio(audioFilePath, new CloudinaryHelper.UploadResponse() {
            @Override
            public void onSuccess(String url) {
                triggerEmergencyResponse(category, keyword, url);
                deleteTempFile();
            }

            @Override
            public void onError(String error) {
                triggerEmergencyResponse(category, keyword, null);
                deleteTempFile();
            }
        });
    }

    private void deleteTempFile() {
        if (audioFilePath != null) {
            File file = new File(audioFilePath);
            if (file.exists()) file.delete();
        }
    }

    private void triggerEmergencyResponse(String reason, String keyword, String audioUrl) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                String locUrl = (location != null) ? "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude() : "Location Unknown";
                
                String message = "🚨 EMPOWHER ALERT! 🚨\nReason: " + reason + "\nDetails: " + keyword + "\nLocation: " + locUrl;
                if (audioUrl != null) {
                    message += "\nEvidence: " + audioUrl;
                }
                
                sendAlertsToContacts(message);
            });
        }
    }

    private void sendAlertsToContacts(String message) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String contactsJson = prefs.getString("emergency_contacts", "[]");
        try {
            JSONArray array = new JSONArray(contactsJson);
            for (int i = 0; i < array.length(); i++) {
                SmsHelper.sendSMS(this, array.getJSONObject(i).getString("number"), message);
            }
        } catch (JSONException e) { Log.e(TAG, "Error sending protocol alerts", e); }
    }

    private void initializeSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { isListening = true; }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { isListening = false; }
            @Override public void onError(int error) {
                isListening = false;
                if (!isRecording) mainHandler.postDelayed(() -> startListening(), 1000);
            }
            @Override public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) processSpeechResults(matches);
                if (!isRecording) startListening();
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListening() {
        if (!isListening && speechRecognizer != null && !isRecording) {
            try {
                speechRecognizer.startListening(speechRecognizerIntent);
            } catch (Exception e) {
                initializeSpeechRecognizer();
                mainHandler.postDelayed(() -> startListening(), 2000);
            }
        }
    }

    private void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
            isListening = false;
        }
    }

    private void processSpeechResults(ArrayList<String> matches) {
        for (String match : matches) {
            String category = voiceAI.getThreatCategory(match);
            if (category != null) {
                String keyword = voiceAI.getMatchingKeyword(match);
                processThreatDetected(category, keyword != null ? keyword : "N/A");
                break;
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        updateDefaultNotification();
        startListening();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Suraksha Service Channel", NotificationManager.IMPORTANCE_LOW);
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showRedZoneNotification() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⚠️ WARNING: RED ZONE DETECTED")
                .setContentText("50m radius danger area. Protocols active.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setColor(0xFFFF0000)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();
        startForeground(1, notification);
    }

    private void updateDefaultNotification() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Voice Shield Active")
                .setContentText("Monitoring for threats...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(1, notification);
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopListening();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (fusedLocationClient != null) fusedLocationClient.removeLocationUpdates(locationCallback);
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
    }
}

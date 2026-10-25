package com.example.woshield;

import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.io.File;
import java.io.IOException;

public class VideoRecordingService extends Service {
    private MediaRecorder mediaRecorder;
    private File videoFile;

    @Override
    public void onCreate() {
        super.onCreate();
        startRecording();
    }

    private void startRecording() {
        try {
            String filePath = getExternalFilesDir(Environment.DIRECTORY_MOVIES) + "/evidence.mp4";
            videoFile = new File(filePath);

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setOutputFile(videoFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();

            Log.d("VideoRecordingService", "Recording started...");
            Thread.sleep(10000); // Record for 10 seconds
            stopRecording();

        } catch (Exception e) {
            Log.e("VideoRecordingService", "Recording error: " + e.getMessage());
        }
    }

    private void stopRecording() {
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Log.d("VideoRecordingService", "Recording stopped. Uploading file...");
            uploadToFirebase();
        } catch (Exception e) {
            Log.e("VideoRecordingService", "Error stopping recording: " + e.getMessage());
        }
    }

    private void uploadToFirebase() {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference().child("evidence/" + System.currentTimeMillis() + ".mp4");

        storageRef.putFile(android.net.Uri.fromFile(videoFile))
                .addOnSuccessListener(taskSnapshot -> Log.d("VideoRecordingService", "Upload successful!"))
                .addOnFailureListener(e -> Log.e("VideoRecordingService", "Upload failed: " + e.getMessage()));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

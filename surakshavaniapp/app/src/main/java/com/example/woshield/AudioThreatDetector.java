package com.example.woshield;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class AudioThreatDetector {

    private static final String TAG = "AudioThreatDetector";
    private MediaRecorder recorder;
    private String audioFilePath;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isSampling = false;

    public AudioThreatDetector(Context context) {
        this.context = context;
    }

    public void startSampling() {
        if (isSampling) return;
        isSampling = true;
        sampleTask.run();
    }

    public void stopSampling() {
        isSampling = false;
        handler.removeCallbacks(sampleTask);
        stopRecording();
    }

    private final Runnable sampleTask = new Runnable() {
        @Override
        public void run() {
            if (!isSampling) return;
            startRecording(5000); // Sample for 5 seconds
            handler.postDelayed(() -> {
                stopRecording();
                analyzeAudio();
                if (isSampling) {
                    handler.postDelayed(sampleTask, 5000); // 5 seconds wait
                }
            }, 5000);
        }
    };

    public void recordEvidence(int durationMs) {
        startRecording(durationMs);
        handler.postDelayed(() -> {
            stopRecording();
            Log.d(TAG, "Evidence recording finished: " + audioFilePath);
            // TODO: Upload to Cloudinary/Firebase
        }, durationMs);
    }

    private void startRecording(int durationMs) {
        audioFilePath = context.getExternalCacheDir().getAbsolutePath() + "/audio_" + System.currentTimeMillis() + ".m4a";
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioSamplingRate(44100);
        recorder.setAudioEncodingBitRate(128000);
        recorder.setOutputFile(audioFilePath);

        try {
            recorder.prepare();
            recorder.start();
            Log.d(TAG, "Recording started for " + durationMs + "ms");
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed", e);
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
                Log.d(TAG, "Recording stopped");
            } catch (RuntimeException e) {
                Log.e(TAG, "stop() failed or stopped too early", e);
            }
            recorder = null;
        }
    }

    private void analyzeAudio() {
        // Simple analysis placeholder
        Log.d(TAG, "Analyzing audio file: " + audioFilePath);
        // If threat detected in analysis, call recordEvidence(10000)
    }
}

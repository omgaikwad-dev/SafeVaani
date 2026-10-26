package com.example.woshield;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Automatically captures unwanted voice and ambient distress audio evidence.
 * Produces both an offline local audio file and an inline Base64 audio Data URI
 * for instant streaming playback in Police CAD & Parent Dashboards.
 */
public class AudioEvidenceRecorder {
    private static final String TAG = "AudioEvidenceRecorder";
    private static final int DEFAULT_RECORD_DURATION_MS = 8000; // 8 seconds

    public interface AudioRecordCallback {
        void onRecordingFinished(File audioFile, String audioDataUri);
        void onRecordingFailed(String error);
    }

    private static MediaRecorder activeRecorder = null;
    private static boolean isRecording = false;

    public static synchronized void recordDistressAudio(Context context, int durationMs, AudioRecordCallback callback) {
        if (isRecording) {
            Log.w(TAG, "Audio recording already in progress");
            if (callback != null) callback.onRecordingFailed("Recording already in progress");
            return;
        }

        final int duration = durationMs > 0 ? durationMs : DEFAULT_RECORD_DURATION_MS;
        File evidenceDir = new File(context.getFilesDir(), "evidence");
        if (!evidenceDir.exists()) {
            evidenceDir.mkdirs();
        }

        final File outputFile = new File(evidenceDir, "distress_voice_" + System.currentTimeMillis() + ".m4a");

        try {
            activeRecorder = new MediaRecorder();
            activeRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            activeRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            activeRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            activeRecorder.setAudioSamplingRate(22050); // Optimized for speech clarity & low payload size
            activeRecorder.setAudioEncodingBitRate(32000);
            activeRecorder.setOutputFile(outputFile.getAbsolutePath());
            activeRecorder.prepare();
            activeRecorder.start();
            isRecording = true;
            Log.d(TAG, "Distress audio evidence recording started: " + outputFile.getAbsolutePath());

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                stopAndFinalize(outputFile, callback);
            }, duration);

        } catch (Exception e) {
            Log.e(TAG, "Failed starting audio recorder", e);
            cleanupRecorder();
            if (callback != null) {
                callback.onRecordingFailed(e.getMessage() != null ? e.getMessage() : "Audio record error");
            }
        }
    }

    private static synchronized void stopAndFinalize(File outputFile, AudioRecordCallback callback) {
        if (!isRecording) return;
        try {
            if (activeRecorder != null) {
                activeRecorder.stop();
                activeRecorder.release();
            }
        } catch (Exception e) {
            Log.w(TAG, "Exception while stopping MediaRecorder", e);
        } finally {
            cleanupRecorder();
        }

        String dataUri = "";
        if (outputFile.exists() && outputFile.length() > 0) {
            dataUri = convertFileToBase64DataUri(outputFile);
            Log.d(TAG, "Evidence recorded successfully (" + outputFile.length() + " bytes). Base64 URI created.");
        }

        if (callback != null) {
            callback.onRecordingFinished(outputFile, dataUri);
        }
    }

    private static synchronized void cleanupRecorder() {
        activeRecorder = null;
        isRecording = false;
    }

    /**
     * Converts the .m4a audio file into a standards-compliant data:audio/mp4;base64 URI.
     */
    public static String convertFileToBase64DataUri(File file) {
        if (file == null || !file.exists()) return "";
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int bytesRead = fis.read(buffer);
            if (bytesRead > 0) {
                String base64Str = Base64.encodeToString(buffer, 0, bytesRead, Base64.NO_WRAP);
                return "data:audio/mp4;base64," + base64Str;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed converting audio to Base64", e);
        }
        return "";
    }
}

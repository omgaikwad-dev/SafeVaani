package com.example.woshield;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

public class VoiceHelp extends AppCompatActivity {
    private Button startbtn, stopbtn, playbtn, stopplay, btnSend;
    private MediaRecorder mRecorder;
    private MediaPlayer mPlayer;
    private static final String LOG_TAG = "AudioRecording";
    private String mFileName = null;
    public static final int REQUEST_PERMISSIONS_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_help);

        startbtn = findViewById(R.id.btnRecord);
        stopbtn = findViewById(R.id.btnStop);
        playbtn = findViewById(R.id.btnPlay);
        stopplay = findViewById(R.id.btnStopPlay);
        // Assuming btnSend is added to layout or I will add it
        btnSend = findViewById(R.id.btnSend);

        stopbtn.setEnabled(false);
        playbtn.setEnabled(false);
        stopplay.setEnabled(false);

        mFileName = getExternalFilesDir(null).getAbsolutePath() + "/EmergencyVoice.3gp";

        startbtn.setOnClickListener(v -> startRecording());
        stopbtn.setOnClickListener(v -> stopRecording());
        playbtn.setOnClickListener(v -> playRecording());
        stopplay.setOnClickListener(v -> stopPlaying());
        
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> sendRecording());
        }
    }

    private void startRecording() {
        if (checkPermissions()) {
            try {
                mRecorder = new MediaRecorder();
                mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mRecorder.setOutputFile(mFileName);
                mRecorder.prepare();
                mRecorder.start();
                
                startbtn.setEnabled(false);
                stopbtn.setEnabled(true);
                playbtn.setEnabled(false);
                Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(LOG_TAG, "prepare() failed: " + e.getMessage());
            }
        } else {
            requestPermissions();
        }
    }

    private void stopRecording() {
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
            startbtn.setEnabled(true);
            stopbtn.setEnabled(false);
            playbtn.setEnabled(true);
            Toast.makeText(this, "Recording Saved", Toast.LENGTH_SHORT).show();
        }
    }

    private void playRecording() {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mFileName);
            mPlayer.prepare();
            mPlayer.start();
            stopplay.setEnabled(true);
            Toast.makeText(this, "Playing...", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Playback failed");
        }
    }

    private void stopPlaying() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
            stopplay.setEnabled(false);
            Toast.makeText(this, "Stopped Playing", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendRecording() {
        File file = new File(mFileName);
        if (!file.exists()) {
            Toast.makeText(this, "No recording found!", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("audio/3gp");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Send Recording via:"));
    }

    private boolean checkPermissions() {
        int record = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return record == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSIONS_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            }
        }
    }
}

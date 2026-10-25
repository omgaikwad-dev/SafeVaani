package com.example.woshield;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class IncomingCallActivity extends AppCompatActivity {

    public static final String EXTRA_CALLER_NAME = "extra_caller_name";
    public static final String EXTRA_CALLER_NUMBER = "extra_caller_number";
    private static final String TAG = "IncomingCallActivity";

    private Ringtone ringtone;
    private Vibrator vibrator;
    private TextToSpeech textToSpeech;

    private TextView tvCallMode;
    private TextView tvCallerName;
    private TextView tvCallerNumber;
    private TextView tvCallTimer;
    private TextView tvRingingStatus;
    private View layoutIncomingActions;
    private View layoutInCallActions;

    private boolean isCallAnswered = false;
    private int callDurationSeconds = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isCallAnswered) {
                callDurationSeconds++;
                int minutes = callDurationSeconds / 60;
                int seconds = callDurationSeconds % 60;
                tvCallTimer.setText(String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds));
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn on screen and show when locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        setContentView(R.layout.activity_incoming_call);

        initViews();
        setupCallerInfo();
        startRingingAndVibration();
        initTextToSpeech();
    }

    private void initViews() {
        tvCallMode = findViewById(R.id.tv_call_mode);
        tvCallerName = findViewById(R.id.tv_caller_name);
        tvCallerNumber = findViewById(R.id.tv_caller_number);
        tvCallTimer = findViewById(R.id.tv_call_timer);
        tvRingingStatus = findViewById(R.id.tv_ringing_status);
        layoutIncomingActions = findViewById(R.id.layout_incoming_actions);
        layoutInCallActions = findViewById(R.id.layout_in_call_actions);

        findViewById(R.id.btn_decline_call).setOnClickListener(v -> declineCall());
        findViewById(R.id.btn_answer_call).setOnClickListener(v -> answerCall());
        findViewById(R.id.btn_end_call).setOnClickListener(v -> endCall());

        // In-call utility toggles
        findViewById(R.id.btn_mute).setOnClickListener(v ->
                Toast.makeText(this, "Microphone muted", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btn_speaker).setOnClickListener(v ->
                Toast.makeText(this, "Speaker on", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btn_keypad).setOnClickListener(v ->
                Toast.makeText(this, "Keypad ready", Toast.LENGTH_SHORT).show());
    }

    private void setupCallerInfo() {
        String name = getIntent().getStringExtra(EXTRA_CALLER_NAME);
        String number = getIntent().getStringExtra(EXTRA_CALLER_NUMBER);

        if (name == null || name.trim().isEmpty()) {
            name = "Dad (Rajesh Sharma)";
        }
        if (number == null || number.trim().isEmpty()) {
            number = "+91 98765 43210 • Mobile";
        }

        tvCallerName.setText(name);
        tvCallerNumber.setText(number);
    }

    private void startRingingAndVibration() {
        try {
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (ringtoneUri == null) {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.setLooping(true);
                }
                ringtone.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing ringtone: " + e.getMessage());
        }

        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 1000, 1000};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting vibration: " + e.getMessage());
        }
    }

    private void stopRingingAndVibration() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });
    }

    private void answerCall() {
        isCallAnswered = true;
        stopRingingAndVibration();

        layoutIncomingActions.setVisibility(View.GONE);
        layoutInCallActions.setVisibility(View.VISIBLE);
        tvRingingStatus.setVisibility(View.GONE);
        tvCallTimer.setVisibility(View.VISIBLE);
        tvCallMode.setText("CONNECTED");
        tvCallMode.setTextColor(0xFF22C55E); // Green

        // Start call duration count-up
        callDurationSeconds = 0;
        tvCallTimer.setText("00:00");
        timerHandler.postDelayed(timerRunnable, 1000);

        // Speak deterrent simulated conversation
        if (textToSpeech != null) {
            String script = "Hey, where are you right now? I am waiting nearby in the car. Stay right there, I am coming to pick you up in two minutes.";
            textToSpeech.speak(script, TextToSpeech.QUEUE_FLUSH, null, "fake_call_utterance");
        }
    }

    private void declineCall() {
        stopRingingAndVibration();
        Toast.makeText(this, "Call Declined", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void endCall() {
        isCallAnswered = false;
        timerHandler.removeCallbacks(timerRunnable);
        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
        stopRingingAndVibration();
        Toast.makeText(this, "Call Ended", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isCallAnswered = false;
        timerHandler.removeCallbacks(timerRunnable);
        stopRingingAndVibration();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}

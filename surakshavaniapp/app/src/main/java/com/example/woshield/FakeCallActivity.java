package com.example.woshield;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

public class FakeCallActivity extends AppCompatActivity {

    private static final String TAG = "FakeCallActivity";

    private MediaPlayer mediaPlayer;
    private ImageView sirenPlayButton;
    private View glowEffect;
    private TextView tvSirenStatus;
    private TextView tvCallCountdown;
    private MaterialButton btnSimulateCall;
    private ChipGroup chipGroupCaller;
    private ChipGroup chipGroupDelay;

    private ObjectAnimator glowAnimator;
    private CountDownTimer callDelayTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_call);

        initViews();
        setupSiren();
        setupFakeCallTrigger();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar_fake_call);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        sirenPlayButton = findViewById(R.id.siren_play);
        glowEffect = findViewById(R.id.glow_effect);
        tvSirenStatus = findViewById(R.id.tv_siren_status);
        tvCallCountdown = findViewById(R.id.tv_call_countdown);
        btnSimulateCall = findViewById(R.id.btn_simulate_call);
        chipGroupCaller = findViewById(R.id.chip_group_caller);
        chipGroupDelay = findViewById(R.id.chip_group_delay);
    }

    private void setupSiren() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.siren);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing siren media player: " + e.getMessage());
        }

        sirenPlayButton.setOnClickListener(v -> toggleSiren());
    }

    private void toggleSiren() {
        if (mediaPlayer == null) {
            try {
                mediaPlayer = MediaPlayer.create(this, R.raw.siren);
                if (mediaPlayer != null) mediaPlayer.setLooping(true);
            } catch (Exception e) {
                Toast.makeText(this, "Audio file unavailable", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            sirenPlayButton.setImageResource(R.drawable.emergency);
            tvSirenStatus.setText("TAP FOR LOUD SIREN");
            stopGlowAnimation();
        } else {
            mediaPlayer.start();
            sirenPlayButton.setImageResource(R.drawable.siren_stop);
            tvSirenStatus.setText("TAP TO STOP SIREN");
            startGlowAnimation();
        }
    }

    private void startGlowAnimation() {
        if (glowEffect == null) return;
        if (glowAnimator == null) {
            PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.35f, 1.0f);
            PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.35f, 1.0f);
            PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.25f, 0.6f, 0.25f);
            glowAnimator = ObjectAnimator.ofPropertyValuesHolder(glowEffect, scaleX, scaleY, alpha);
            glowAnimator.setDuration(900);
            glowAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        }
        if (!glowAnimator.isRunning()) {
            glowAnimator.start();
        }
    }

    private void stopGlowAnimation() {
        if (glowAnimator != null && glowAnimator.isRunning()) {
            glowAnimator.cancel();
        }
        if (glowEffect != null) {
            glowEffect.setScaleX(1.0f);
            glowEffect.setScaleY(1.0f);
            glowEffect.setAlpha(0.25f);
        }
    }

    private void setupFakeCallTrigger() {
        btnSimulateCall.setOnClickListener(v -> triggerFakeCall());
    }

    private void triggerFakeCall() {
        // Resolve chosen caller
        String callerName = "Dad (Rajesh Sharma)";
        String callerNumber = "+91 98765 43210";

        int checkedCallerId = chipGroupCaller.getCheckedChipId();
        if (checkedCallerId == R.id.chip_caller_mom) {
            callerName = "Mom";
            callerNumber = "+91 98765 12345";
        } else if (checkedCallerId == R.id.chip_caller_police) {
            callerName = "Police Control Room";
            callerNumber = "100 / 112";
        } else if (checkedCallerId == R.id.chip_caller_brother) {
            callerName = "Brother";
            callerNumber = "+91 98765 67890";
        }

        // Resolve delay
        int delaySeconds = 0;
        int checkedDelayId = chipGroupDelay.getCheckedChipId();
        if (checkedDelayId == R.id.chip_delay_5s) {
            delaySeconds = 5;
        } else if (checkedDelayId == R.id.chip_delay_15s) {
            delaySeconds = 15;
        } else if (checkedDelayId == R.id.chip_delay_30s) {
            delaySeconds = 30;
        }

        if (delaySeconds == 0) {
            launchIncomingCall(callerName, callerNumber);
        } else {
            scheduleIncomingCall(callerName, callerNumber, delaySeconds);
        }
    }

    private void launchIncomingCall(String callerName, String callerNumber) {
        if (tvCallCountdown != null) {
            tvCallCountdown.setVisibility(View.GONE);
        }
        if (callDelayTimer != null) {
            callDelayTimer.cancel();
        }

        Intent intent = new Intent(this, IncomingCallActivity.class);
        intent.putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName);
        intent.putExtra(IncomingCallActivity.EXTRA_CALLER_NUMBER, callerNumber);
        startActivity(intent);
    }

    private void scheduleIncomingCall(String callerName, String callerNumber, int delaySeconds) {
        if (callDelayTimer != null) {
            callDelayTimer.cancel();
        }

        tvCallCountdown.setVisibility(View.VISIBLE);
        tvCallCountdown.setText("Incoming call scheduled in " + delaySeconds + "s... You can lock your phone");
        btnSimulateCall.setEnabled(false);
        btnSimulateCall.setText("CALL SCHEDULED (" + delaySeconds + "s)");

        Toast.makeText(this, "Fake call incoming in " + delaySeconds + " seconds", Toast.LENGTH_SHORT).show();

        callDelayTimer = new CountDownTimer((long) delaySeconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000) + 1;
                tvCallCountdown.setText("Incoming call in " + secondsRemaining + "s... (Feel free to lock screen)");
                btnSimulateCall.setText("CALL SCHEDULED (" + secondsRemaining + "s)");
            }

            @Override
            public void onFinish() {
                tvCallCountdown.setVisibility(View.GONE);
                btnSimulateCall.setEnabled(true);
                btnSimulateCall.setText("SIMULATE INCOMING CALL");
                launchIncomingCall(callerName, callerNumber);
            }
        }.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Do not stop mediaPlayer if user purposely keeps siren on, or stop if desired
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (callDelayTimer != null) {
            callDelayTimer.cancel();
        }
        stopGlowAnimation();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
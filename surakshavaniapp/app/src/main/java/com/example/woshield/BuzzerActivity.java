package com.example.woshield;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BuzzerActivity extends AppCompatActivity {
    private static final String TAG = "BuzzerActivity";
    private static final int PERMISSION_REQUEST_CODE = 101;

    private List<String> emergencyNumbers;
    private ExtendedFloatingActionButton sosButton;
    private View pulseView;
    private TextView tvStatus, tvCountdown, tvEmergencyNumber;
    private ObjectAnimator pulseAnimator;
    private CountDownTimer localCountDownTimer;

    private boolean isReceiverRegistered = false;

    private final BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;

            String action = intent.getAction();
            Log.d(TAG, "Received broadcast action: " + action);

            switch (action) {
                case BuzzerTimerService.ACTION_TICK:
                    long remaining = intent.getLongExtra(BuzzerTimerService.EXTRA_REMAINING_MILLIS, 0);
                    updateCountdownDisplay(remaining);
                    break;

                case BuzzerTimerService.ACTION_TRIGGERED:
                    onAlertTriggeredUI();
                    break;

                case BuzzerTimerService.ACTION_CANCELLED:
                    onAlertCancelledUI();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buzzer);

        emergencyNumbers = new ArrayList<>();
        loadEmergencyContacts();

        sosButton = findViewById(R.id.btn_sos);
        pulseView = findViewById(R.id.pulseView);
        tvStatus = findViewById(R.id.tv_status);
        tvCountdown = findViewById(R.id.tv_countdown);
        tvEmergencyNumber = findViewById(R.id.tv_emergency_number);

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        View cardContacts = findViewById(R.id.card_contacts_info);
        if (cardContacts != null) {
            cardContacts.setOnClickListener(v -> startActivity(new Intent(this, EmergencyContactActivity.class)));
        }

        sosButton.setOnClickListener(v -> handleSosButtonClick());

        checkAndRequestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEmergencyContacts();
        updateContactsDisplay();
        registerServiceReceiver();
        syncUIWithServiceState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterServiceReceiver();
        if (localCountDownTimer != null) {
            localCountDownTimer.cancel();
            localCountDownTimer = null;
        }
    }

    private void registerServiceReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BuzzerTimerService.ACTION_TICK);
            filter.addAction(BuzzerTimerService.ACTION_TRIGGERED);
            filter.addAction(BuzzerTimerService.ACTION_CANCELLED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(serviceReceiver, filter);
            }
            isReceiverRegistered = true;
        }
    }

    private void unregisterServiceReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(serviceReceiver);
            } catch (Exception ignored) {}
            isReceiverRegistered = false;
        }
    }

    /**
     * Synchronizes UI with the actual persistent state of BuzzerTimerService.
     */
    private void syncUIWithServiceState() {
        if (BuzzerTimerService.isTriggered(this)) {
            onAlertTriggeredUI();
        } else if (BuzzerTimerService.isTimerActive(this)) {
            long targetTime = BuzzerTimerService.getTargetTime(this);
            long remaining = targetTime - System.currentTimeMillis();
            if (remaining > 0) {
                onTimerRunningUI(remaining);
            } else {
                onAlertTriggeredUI();
            }
        } else {
            onAlertCancelledUI();
        }
    }

    private void handleSosButtonClick() {
        if (BuzzerTimerService.isTriggered(this)) {
            // Stop siren and reset
            BuzzerTimerService.stopSiren(this);
            onAlertCancelledUI();
            Toast.makeText(this, "Emergency siren stopped", Toast.LENGTH_SHORT).show();
        } else if (BuzzerTimerService.isTimerActive(this)) {
            // Cancel active countdown
            BuzzerTimerService.cancelTimer(this);
            onAlertCancelledUI();
            Toast.makeText(this, "Emergency alert cancelled", Toast.LENGTH_SHORT).show();
        } else {
            // Not active: check contacts and show timer dialog
            if (emergencyNumbers.isEmpty()) {
                Toast.makeText(this, "Please add emergency contacts first!", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, EmergencyContactActivity.class));
            } else {
                promptDelayTimeDialog();
            }
        }
    }

    private void promptDelayTimeDialog() {
        String[] options = {
                "30 Seconds (Quick Check)",
                "1 Minute (Short Walk)",
                "2 Minutes (Standard Transit)",
                "5 Minutes (Extended Walk)",
                "Custom Duration (Minutes)",
                "🚨 SEND SOS IMMEDIATELY"
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle("Schedule Safety Timer")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            startCountdownTimer(30 * 1000L);
                            break;
                        case 1:
                            startCountdownTimer(60 * 1000L);
                            break;
                        case 2:
                            startCountdownTimer(2 * 60 * 1000L);
                            break;
                        case 3:
                            startCountdownTimer(5 * 60 * 1000L);
                            break;
                        case 4:
                            showCustomTimeDialog();
                            break;
                        case 5:
                            dispatchSosImmediately();
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCustomTimeDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Minutes (e.g. 3)");
        input.setText("1");
        input.setPadding(48, 32, 48, 16);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Custom Timer Duration")
                .setMessage("Enter delay before emergency alert is dispatched:")
                .setView(input)
                .setPositiveButton("Start Timer", (dialog, which) -> {
                    String str = input.getText().toString().trim();
                    if (!str.isEmpty()) {
                        try {
                            int minutes = Integer.parseInt(str);
                            if (minutes <= 0) minutes = 1;
                            startCountdownTimer(minutes * 60000L);
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startCountdownTimer(long delayMillis) {
        BuzzerTimerService.startTimer(this, delayMillis);
        onTimerRunningUI(delayMillis);
        Toast.makeText(this, "Safety timer active. Running in background.", Toast.LENGTH_SHORT).show();
    }

    private void dispatchSosImmediately() {
        Intent intent = new Intent(this, BuzzerTimerService.class);
        intent.setAction(BuzzerTimerService.ACTION_TRIGGER_IMMEDIATELY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        onAlertTriggeredUI();
        Toast.makeText(this, "Emergency SOS dispatched immediately!", Toast.LENGTH_LONG).show();
    }

    private void onTimerRunningUI(long remainingMillis) {
        sosButton.setText("CANCEL");
        tvStatus.setText("ALERT SCHEDULED");
        startActivePulse();
        updateCountdownDisplay(remainingMillis);

        if (localCountDownTimer != null) {
            localCountDownTimer.cancel();
        }
        localCountDownTimer = new CountDownTimer(remainingMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateCountdownDisplay(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                updateCountdownDisplay(0);
            }
        }.start();
    }

    private void onAlertTriggeredUI() {
        if (localCountDownTimer != null) {
            localCountDownTimer.cancel();
            localCountDownTimer = null;
        }
        sosButton.setText("STOP SIREN");
        tvStatus.setText("ALERTS DISPATCHED");
        tvCountdown.setText("00:00");
        startActivePulse();
    }

    private void onAlertCancelledUI() {
        if (localCountDownTimer != null) {
            localCountDownTimer.cancel();
            localCountDownTimer = null;
        }
        sosButton.setText("SOS");
        tvStatus.setText("READY TO PROTECT");
        tvCountdown.setText("");
        startIdlePulse();
    }

    private void updateCountdownDisplay(long millisUntilFinished) {
        int minutes = (int) (millisUntilFinished / 1000) / 60;
        int seconds = (int) (millisUntilFinished / 1000) % 60;
        tvCountdown.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    private void startIdlePulse() {
        if (pulseAnimator != null) pulseAnimator.cancel();
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                pulseView,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.2f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.2f),
                PropertyValuesHolder.ofFloat("alpha", 0.5f, 0.0f)
        );
        pulseAnimator.setDuration(2000);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.start();
    }

    private void startActivePulse() {
        if (pulseAnimator != null) pulseAnimator.cancel();
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                pulseView,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.5f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.5f),
                PropertyValuesHolder.ofFloat("alpha", 0.8f, 0.0f)
        );
        pulseAnimator.setDuration(800);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.start();
    }

    private void loadEmergencyContacts() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String contactsJson = prefs.getString("emergency_contacts", "[]");
        try {
            JSONArray array = new JSONArray(contactsJson);
            emergencyNumbers.clear();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                emergencyNumbers.add(obj.getString("number"));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading contacts", e);
        }
    }

    private void updateContactsDisplay() {
        if (emergencyNumbers.isEmpty()) {
            tvEmergencyNumber.setText("No Contacts Added");
        } else {
            tvEmergencyNumber.setText(emergencyNumbers.size() + " Contacts Secured");
        }
    }

    private void checkAndRequestPermissions() {
        List<String> neededPermissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.SEND_SMS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    neededPermissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "Permissions required for reliable emergency SOS dispatch", Toast.LENGTH_LONG).show();
            }
        }
    }
}

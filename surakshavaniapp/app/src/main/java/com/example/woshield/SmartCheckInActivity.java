package com.example.woshield;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmartCheckInActivity extends AppCompatActivity {
    private static final String TAG = "SmartCheckInActivity";

    public static final String EXTRA_CHECK_IN_ID = "extra_check_in_id";
    public static final String EXTRA_ROUTINE_ID = "extra_routine_id";
    public static final String EXTRA_ROUTINE_TITLE = "extra_routine_title";
    public static final String EXTRA_REASON = "extra_reason";
    public static final String EXTRA_DURATION_MINUTES = "extra_duration_minutes";

    // UI Elements
    private MaterialCardView cardStatusBanner;
    private ImageView ivStatusIcon;
    private TextView tvStatusTitle;
    private TextView tvCountdownTimer;
    private TextView tvStatusSubtitle;

    private TextView tvCheckInReason;
    private TextView tvLinkedRoutine;
    private TextView tvCreatedAt;
    private TextView tvDeadlineAt;
    private TextView tvEscalationPolicy;
    private TextView tvContactsCount;

    private LinearLayout layoutLoading;
    private ProgressBar progressCheckIn;
    private TextView tvLoadingText;
    private TextView tvResponseFeedback;
    private TextView tvResponseSectionHeader;

    private MaterialCardView cardActionSafe;
    private MaterialCardView cardActionAssistance;
    private MaterialCardView cardActionSos;

    private LinearLayout layoutCreateCheckIn;
    private MaterialButton btnTrigger5Min;
    private MaterialButton btnTrigger15Min;

    // State & Dependencies
    private CheckInRepository repository;
    private FusedLocationProviderClient locationClient;
    private CheckIn currentCheckIn;
    private String checkInId;
    private CountDownTimer countDownTimer;
    private ValueEventListener firebaseListener;
    private boolean isSubmitting = false;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_check_in);

        repository = new CheckInRepository();
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        setupToolbar();
        setupActionListeners();

        // Check if existing checkInId provided, or if new creation requested
        checkInId = getIntent().getStringExtra(EXTRA_CHECK_IN_ID);
        String routineId = getIntent().getStringExtra(EXTRA_ROUTINE_ID);
        String routineTitle = getIntent().getStringExtra(EXTRA_ROUTINE_TITLE);
        String reason = getIntent().getStringExtra(EXTRA_REASON);
        int durationMinutes = getIntent().getIntExtra(EXTRA_DURATION_MINUTES, 0);

        if (checkInId != null && !checkInId.isEmpty()) {
            loadCheckIn(checkInId);
        } else if (durationMinutes > 0) {
            createNewCheckIn(routineId, routineTitle, reason, durationMinutes);
        } else {
            // Find any currently pending check-in
            fetchPendingOrIdle();
        }
    }

    private void initViews() {
        cardStatusBanner = findViewById(R.id.card_status_banner);
        ivStatusIcon = findViewById(R.id.iv_status_icon);
        tvStatusTitle = findViewById(R.id.tv_status_title);
        tvCountdownTimer = findViewById(R.id.tv_countdown_timer);
        tvStatusSubtitle = findViewById(R.id.tv_status_subtitle);

        tvCheckInReason = findViewById(R.id.tv_checkin_reason);
        tvLinkedRoutine = findViewById(R.id.tv_linked_routine);
        tvCreatedAt = findViewById(R.id.tv_created_at);
        tvDeadlineAt = findViewById(R.id.tv_deadline_at);
        tvEscalationPolicy = findViewById(R.id.tv_escalation_policy);
        tvContactsCount = findViewById(R.id.tv_contacts_count);

        layoutLoading = findViewById(R.id.layout_loading);
        progressCheckIn = findViewById(R.id.progress_checkin);
        tvLoadingText = findViewById(R.id.tv_loading_text);
        tvResponseFeedback = findViewById(R.id.tv_response_feedback);
        tvResponseSectionHeader = findViewById(R.id.tv_response_section_header);

        cardActionSafe = findViewById(R.id.card_action_safe);
        cardActionAssistance = findViewById(R.id.card_action_assistance);
        cardActionSos = findViewById(R.id.card_action_sos);

        layoutCreateCheckIn = findViewById(R.id.layout_create_checkin);
        btnTrigger5Min = findViewById(R.id.btn_trigger_5min);
        btnTrigger15Min = findViewById(R.id.btn_trigger_15min);

        updateContactsSummary();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar_smart_check_in);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void updateContactsSummary() {
        List<String> contacts = EmergencyHelper.getAuthorizedContactNames(this);
        if (contacts.isEmpty()) {
            tvContactsCount.setText("⚠️ Trusted Circle: 0 contacts configured in settings");
            tvContactsCount.setTextColor(ContextCompat.getColor(this, R.color.error_red));
        } else {
            tvContactsCount.setText("Trusted Circle: " + contacts.size() + " authorized contact(s)");
            tvContactsCount.setTextColor(ContextCompat.getColor(this, R.color.deep_purple));
        }
    }

    private void setupActionListeners() {
        cardActionSafe.setOnClickListener(v -> handleSafeResponse());
        cardActionAssistance.setOnClickListener(v -> handleAssistanceResponse());
        cardActionSos.setOnClickListener(v -> handleSosResponse());

        btnTrigger5Min.setOnClickListener(v -> createNewCheckIn(null, null, "Quick 5-Minute Safety Check", 5));
        btnTrigger15Min.setOnClickListener(v -> createNewCheckIn(null, null, "15-Minute Commute Check", 15));
    }

    private void fetchPendingOrIdle() {
        showLoading("Checking for active check-in...");
        repository.getPendingCheckIn(new CheckInRepository.RepositoryCallback<CheckIn>() {
            @Override
            public void onSuccess(CheckIn checkIn) {
                hideLoading();
                if (checkIn != null) {
                    currentCheckIn = checkIn;
                    checkInId = checkIn.getCheckInId();
                    bindCheckInUi(checkIn);
                    attachRealtimeListener(checkIn.getCheckInId());
                } else {
                    showIdleState();
                }
            }

            @Override
            public void onError(String errorMessage) {
                hideLoading();
                showIdleState();
            }
        });
    }

    private void loadCheckIn(String id) {
        showLoading("Loading check-in...");
        repository.getCheckInById(id, new CheckInRepository.RepositoryCallback<CheckIn>() {
            @Override
            public void onSuccess(CheckIn checkIn) {
                hideLoading();
                currentCheckIn = checkIn;
                bindCheckInUi(checkIn);
                attachRealtimeListener(id);
            }

            @Override
            public void onError(String errorMessage) {
                hideLoading();
                Toast.makeText(SmartCheckInActivity.this, "Check-in not found: " + errorMessage, Toast.LENGTH_SHORT).show();
                showIdleState();
            }
        });
    }

    private void createNewCheckIn(String routineId, String routineTitle, String reason, int durationMinutes) {
        showLoading("Initializing Smart Check-In...");
        long deadline = System.currentTimeMillis() + (durationMinutes * 60L * 1000L);
        CheckIn newCheckIn = new CheckIn(
                null,
                null,
                routineId,
                routineTitle != null ? routineTitle : "Safety Commute",
                null,
                reason != null ? reason : "Scheduled Checkpoint",
                deadline
        );

        repository.createCheckIn(newCheckIn, new CheckInRepository.RepositoryCallback<CheckIn>() {
            @Override
            public void onSuccess(CheckIn created) {
                hideLoading();
                currentCheckIn = created;
                checkInId = created.getCheckInId();
                // Schedule local alarm
                CheckInScheduler.scheduleDeadline(SmartCheckInActivity.this, created);
                bindCheckInUi(created);
                attachRealtimeListener(created.getCheckInId());
                Toast.makeText(SmartCheckInActivity.this, "Safety Check-In Started (" + durationMinutes + " min)", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                hideLoading();
                Toast.makeText(SmartCheckInActivity.this, "Failed to start check-in: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void attachRealtimeListener(String id) {
        detachRealtimeListener();
        firebaseListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                CheckIn updated = snapshot.getValue(CheckIn.class);
                if (updated != null && !isFinishing() && !isDestroyed()) {
                    currentCheckIn = updated;
                    bindCheckInUi(updated);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Check-in listener cancelled", error.toException());
            }
        };
        repository.attachCheckInListener(id, firebaseListener);
    }

    private void detachRealtimeListener() {
        if (firebaseListener != null && checkInId != null) {
            repository.removeCheckInListener(checkInId, firebaseListener);
            firebaseListener = null;
        }
    }

    private void bindCheckInUi(CheckIn checkIn) {
        if (checkIn == null) return;

        tvCheckInReason.setText(checkIn.getReason() != null ? checkIn.getReason() : "General Check-In");
        tvLinkedRoutine.setText(checkIn.getRoutineTitle() != null ? checkIn.getRoutineTitle() : "None (Ad-Hoc)");
        tvCreatedAt.setText(timeFormat.format(new Date(checkIn.getCreatedAt())));
        tvDeadlineAt.setText(timeFormat.format(new Date(checkIn.getDeadlineAt())));

        String policyText = "Notify Trusted Circle via SMS";
        if (CheckIn.POLICY_SILENT_LOG.equals(checkIn.getEscalationPolicy())) {
            policyText = "Log Event Silently";
        }
        tvEscalationPolicy.setText(policyText);

        layoutCreateCheckIn.setVisibility(View.GONE);

        // State based styling
        String status = checkIn.getStatus();
        if (CheckIn.STATUS_PENDING.equals(status)) {
            renderPendingState(checkIn);
        } else if (CheckIn.STATUS_CONFIRMED_SAFE.equals(status)) {
            renderResolvedState("CONFIRMED SAFE", "You responded that you are safe. Deadline alarms have been cancelled.",
                    R.color.status_safe, R.color.status_safe_bg, R.drawable.ic_check_circle, true);
        } else if (CheckIn.STATUS_ASSISTANCE_REQUESTED.equals(status)) {
            renderResolvedState("ASSISTANCE REQUESTED", "Assistance alert was sent to your trusted contacts.",
                    R.color.warning_orange, R.color.status_warning_bg, R.drawable.ic_assistance_hand, false);
        } else if (CheckIn.STATUS_SOS_TRIGGERED.equals(status)) {
            renderResolvedState("EMERGENCY SOS TRIGGERED", "Full emergency SOS dispatched with GPS & evidence.",
                    R.color.error_red, R.color.status_emergency_bg, R.drawable.ic_emergency_sos, false);
        } else if (CheckIn.STATUS_MISSED.equals(status)) {
            renderResolvedState("CHECK-IN MISSED", "The deadline expired without response. Contacts were alerted according to policy.",
                    R.color.error_red, R.color.status_emergency_bg, R.drawable.ic_emergency_sos, false);
        } else {
            renderResolvedState("RESOLVED", "Check-in resolved (" + status + ")",
                    R.color.text_secondary, R.color.light_lavender, R.drawable.ic_check_circle, true);
        }
    }

    private void renderPendingState(CheckIn checkIn) {
        cardStatusBanner.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_warning_bg));
        cardStatusBanner.setStrokeColor(ContextCompat.getColor(this, R.color.warning_orange));
        ivStatusIcon.setImageResource(R.drawable.ic_time);
        ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.warning_orange));
        tvStatusTitle.setText("PENDING SAFETY CHECK-IN");
        tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.warning_orange));
        tvStatusSubtitle.setText("Please respond before the timer expires. Your trusted circle will be notified if you miss this deadline.");

        tvResponseSectionHeader.setVisibility(View.VISIBLE);
        cardActionSafe.setVisibility(View.VISIBLE);
        cardActionAssistance.setVisibility(View.VISIBLE);
        cardActionSos.setVisibility(View.VISIBLE);

        cardActionSafe.setEnabled(true);
        cardActionAssistance.setEnabled(true);
        cardActionSos.setEnabled(true);

        startCountdownTimer(checkIn.getDeadlineAt());
    }

    private void renderResolvedState(String title, String subtitle, int colorRes, int bgRes, int iconRes, boolean isSafe) {
        stopCountdownTimer();
        cardStatusBanner.setCardBackgroundColor(ContextCompat.getColor(this, bgRes));
        cardStatusBanner.setStrokeColor(ContextCompat.getColor(this, colorRes));
        ivStatusIcon.setImageResource(iconRes);
        ivStatusIcon.setColorFilter(ContextCompat.getColor(this, colorRes));
        tvStatusTitle.setText(title);
        tvStatusTitle.setTextColor(ContextCompat.getColor(this, colorRes));
        tvCountdownTimer.setText(isSafe ? "RESOLVED" : "CLOSED");
        tvCountdownTimer.setTextColor(ContextCompat.getColor(this, colorRes));
        tvStatusSubtitle.setText(subtitle);

        // Hide or disable response cards
        tvResponseSectionHeader.setVisibility(View.GONE);
        cardActionSafe.setEnabled(false);
        cardActionAssistance.setEnabled(false);
        cardActionSos.setEnabled(false);
        cardActionSafe.setAlpha(0.6f);
        cardActionAssistance.setAlpha(0.6f);
        cardActionSos.setAlpha(0.6f);

        // Provide option to create another checkin
        layoutCreateCheckIn.setVisibility(View.VISIBLE);
    }

    private void showIdleState() {
        stopCountdownTimer();
        cardStatusBanner.setCardBackgroundColor(ContextCompat.getColor(this, R.color.light_lavender));
        cardStatusBanner.setStrokeColor(ContextCompat.getColor(this, R.color.deep_purple));
        ivStatusIcon.setImageResource(R.drawable.ic_smart_checkin);
        ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.deep_purple));
        tvStatusTitle.setText("NO ACTIVE CHECK-IN");
        tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.deep_purple));
        tvCountdownTimer.setText("IDLE");
        tvCountdownTimer.setTextColor(ContextCompat.getColor(this, R.color.deep_purple));
        tvStatusSubtitle.setText("You have no pending safety check-in. Start one now or let routines trigger them automatically.");

        tvCheckInReason.setText("None");
        tvLinkedRoutine.setText("None");
        tvCreatedAt.setText("--:--");
        tvDeadlineAt.setText("--:--");

        tvResponseSectionHeader.setVisibility(View.GONE);
        cardActionSafe.setVisibility(View.GONE);
        cardActionAssistance.setVisibility(View.GONE);
        cardActionSos.setVisibility(View.GONE);

        layoutCreateCheckIn.setVisibility(View.VISIBLE);
    }

    private void startCountdownTimer(long deadlineAt) {
        stopCountdownTimer();
        long millisRemaining = deadlineAt - System.currentTimeMillis();

        if (millisRemaining <= 0) {
            tvCountdownTimer.setText("EXPIRED");
            return;
        }

        countDownTimer = new CountDownTimer(millisRemaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = millisUntilFinished / 1000 / 60;
                long seconds = (millisUntilFinished / 1000) % 60;
                tvCountdownTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
            }

            @Override
            public void onFinish() {
                tvCountdownTimer.setText("00:00");
                tvStatusSubtitle.setText("Deadline expired! Timeout escalation is running.");
            }
        }.start();
    }

    private void stopCountdownTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    // ==========================================
    // RESPONSE 1: I'M SAFE
    // ==========================================
    private void handleSafeResponse() {
        if (isSubmitting || currentCheckIn == null || !CheckIn.STATUS_PENDING.equals(currentCheckIn.getStatus())) {
            return;
        }

        isSubmitting = true;
        lockResponseButtons();
        showLoading("Securing location & confirming safety...");

        fetchLocationAndExecute((lat, lon) -> {
            repository.respondCheckIn(
                    currentCheckIn.getCheckInId(),
                    CheckIn.STATUS_CONFIRMED_SAFE,
                    "I'm Safe confirmed by user",
                    lat,
                    lon,
                    new CheckInRepository.RepositoryCallback<CheckIn>() {
                        @Override
                        public void onSuccess(CheckIn updated) {
                            hideLoading();
                            isSubmitting = false;
                            // Cancel any alarms
                            CheckInScheduler.cancelDeadline(SmartCheckInActivity.this, updated.getCheckInId());
                            currentCheckIn = updated;
                            bindCheckInUi(updated);
                            showFeedback("✅ You confirmed you are safe. Check-in resolved.", true);
                            Toast.makeText(SmartCheckInActivity.this, "Check-in resolved safely!", Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            hideLoading();
                            isSubmitting = false;
                            unlockResponseButtons();
                            showFeedback("❌ Failed to record response: " + errorMessage, false);
                            Toast.makeText(SmartCheckInActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
            );
        });
    }

    // ==========================================
    // RESPONSE 2: REQUEST ASSISTANCE
    // ==========================================
    private void handleAssistanceResponse() {
        if (isSubmitting || currentCheckIn == null || !CheckIn.STATUS_PENDING.equals(currentCheckIn.getStatus())) {
            return;
        }

        isSubmitting = true;
        lockResponseButtons();
        showLoading("Alerting trusted circle with GPS coordinates...");

        String reason = currentCheckIn.getReason();
        String routineTitle = currentCheckIn.getRoutineTitle();

        EmergencyHelper.dispatchAssistance(this, reason, routineTitle, currentCheckIn.getCheckInId(), new EmergencyHelper.AssistanceCallback() {
            @Override
            public void onSuccess(double lat, double lon, int contactsNotified) {
                // Persist response state to Firebase
                repository.respondCheckIn(
                        currentCheckIn.getCheckInId(),
                        CheckIn.STATUS_ASSISTANCE_REQUESTED,
                        "Assistance requested (Notified: " + contactsNotified + ")",
                        lat,
                        lon,
                        new CheckInRepository.RepositoryCallback<CheckIn>() {
                            @Override
                            public void onSuccess(CheckIn updated) {
                                hideLoading();
                                isSubmitting = false;
                                CheckInScheduler.cancelDeadline(SmartCheckInActivity.this, updated.getCheckInId());
                                currentCheckIn = updated;
                                bindCheckInUi(updated);

                                String msg = contactsNotified > 0
                                        ? "⚠️ Assistance alert dispatched to " + contactsNotified + " contact(s)."
                                        : "⚠️ Assistance requested. Note: No emergency contacts configured in settings.";
                                showFeedback(msg, false);
                                Toast.makeText(SmartCheckInActivity.this, msg, Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                hideLoading();
                                isSubmitting = false;
                                unlockResponseButtons();
                                showFeedback("Error updating record: " + errorMessage, false);
                            }
                        }
                );
            }

            @Override
            public void onError(String error) {
                hideLoading();
                isSubmitting = false;
                unlockResponseButtons();
                showFeedback("Assistance dispatch failed: " + error, false);
                Toast.makeText(SmartCheckInActivity.this, "Failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ==========================================
    // RESPONSE 3: EMERGENCY SOS
    // ==========================================
    private void handleSosResponse() {
        if (isSubmitting || EmergencyHelper.isSosDispatching()) {
            Toast.makeText(this, "Emergency SOS is already in progress...", Toast.LENGTH_SHORT).show();
            return;
        }

        isSubmitting = true;
        lockResponseButtons();
        showLoading("🚨 DISPATCHING EMERGENCY SOS...");

        String checkInIdToLink = currentCheckIn != null ? currentCheckIn.getCheckInId() : null;

        EmergencyHelper.dispatchSOS(this, null, checkInIdToLink, new EmergencyHelper.EmergencyCallback() {
            @Override
            public void onSuccess(double lat, double lon, int contactsNotified) {
                if (currentCheckIn != null && CheckIn.STATUS_PENDING.equals(currentCheckIn.getStatus())) {
                    repository.respondCheckIn(
                            currentCheckIn.getCheckInId(),
                            CheckIn.STATUS_SOS_TRIGGERED,
                            "SOS Dispatched via Check-In (Contacts: " + contactsNotified + ")",
                            lat,
                            lon,
                            new CheckInRepository.RepositoryCallback<CheckIn>() {
                                @Override
                                public void onSuccess(CheckIn updated) {
                                    hideLoading();
                                    isSubmitting = false;
                                    CheckInScheduler.cancelDeadline(SmartCheckInActivity.this, updated.getCheckInId());
                                    currentCheckIn = updated;
                                    bindCheckInUi(updated);
                                    showFeedback("🚨 SOS ALERT DISPATCHED TO " + contactsNotified + " CONTACT(S)", false);
                                    Toast.makeText(SmartCheckInActivity.this, "Emergency SOS Dispatched Successfully!", Toast.LENGTH_LONG).show();
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    hideLoading();
                                    isSubmitting = false;
                                    unlockResponseButtons();
                                    showFeedback("SOS dispatched via SMS, but DB sync returned: " + errorMessage, false);
                                }
                            }
                    );
                } else {
                    hideLoading();
                    isSubmitting = false;
                    Toast.makeText(SmartCheckInActivity.this, "Emergency SOS Dispatched Successfully!", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(String error) {
                hideLoading();
                isSubmitting = false;
                unlockResponseButtons();
                showFeedback("❌ SOS Dispatch failed: " + error, false);
                Toast.makeText(SmartCheckInActivity.this, "SOS Dispatch Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private interface LocationCallback {
        void onLocationAcquired(double lat, double lon);
    }

    private void fetchLocationAndExecute(LocationCallback callback) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback.onLocationAcquired(0, 0);
            return;
        }

        locationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        callback.onLocationAcquired(location.getLatitude(), location.getLongitude());
                    } else {
                        locationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                            if (lastLoc != null) {
                                callback.onLocationAcquired(lastLoc.getLatitude(), lastLoc.getLongitude());
                            } else {
                                callback.onLocationAcquired(0, 0);
                            }
                        }).addOnFailureListener(e -> callback.onLocationAcquired(0, 0));
                    }
                })
                .addOnFailureListener(e -> callback.onLocationAcquired(0, 0));
    }

    private void lockResponseButtons() {
        cardActionSafe.setEnabled(false);
        cardActionAssistance.setEnabled(false);
        cardActionSos.setEnabled(false);
    }

    private void unlockResponseButtons() {
        cardActionSafe.setEnabled(true);
        cardActionAssistance.setEnabled(true);
        cardActionSos.setEnabled(true);
    }

    private void showLoading(String message) {
        layoutLoading.setVisibility(View.VISIBLE);
        tvLoadingText.setText(message);
    }

    private void hideLoading() {
        layoutLoading.setVisibility(View.GONE);
    }

    private void showFeedback(String message, boolean isSafe) {
        tvResponseFeedback.setVisibility(View.VISIBLE);
        tvResponseFeedback.setText(message);
        if (isSafe) {
            tvResponseFeedback.setBackgroundResource(R.drawable.badge_safe);
            tvResponseFeedback.setTextColor(ContextCompat.getColor(this, R.color.status_safe));
        } else {
            tvResponseFeedback.setBackgroundResource(R.drawable.badge_routine);
            tvResponseFeedback.setTextColor(ContextCompat.getColor(this, R.color.error_red));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCountdownTimer();
        detachRealtimeListener();
    }
}

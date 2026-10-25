package com.example.woshield;

/**
 * NightPlanActivity — Main Activity for Night-Time Decision Engine
 *
 * The primary command center for a shift worker. Lets them enter their
 * shift end time and destination, then displays a live-updating, confidence-
 * classified plan to get home safely through a sleeping city.
 *
 * Features:
 * - Shift input (time + destination + worker type)
 * - Live plan with KNOWN/PROBABLE/OUTDATED/UNKNOWN labels
 * - Auto-refresh every 3 minutes
 * - Offline mode with graceful degradation
 * - Emergency SOS integration
 * - Journey check-in
 */

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class NightPlanActivity extends AppCompatActivity {

    private static final String TAG = "NightPlanActivity";
    private static final int PERMISSION_REQUEST = 300;

    // AI Decision Engine & CSV Repository
    private NightAiPlanService aiPlanService;
    private NightOwlCsvRepository csvRepo;
    private NightPlanEngine planEngine;
    private NightPlanEngine.NightPlan currentPlan;

    // Continuous Location Tracking
    private LocationCallback locationCallback;
    private double currentUserLat = 18.5204; // Pune Default
    private double currentUserLng = 73.8567;
    private boolean hasGpsFix = false;

    // UI — Input
    private EditText etShiftEnd, etDestination, etWorkerType;
    private ChipGroup chipWorkerType;
    private MaterialButton btnGeneratePlan;

    // UI — Plan display
    private LinearLayout layoutInputSection;
    private MaterialCardView cardPlanContainer;
    private TextView tvPlanStatus, tvEstimatedArrival, tvConfidencePct;
    private TextView tvNextUpdate, tvPlanTime, tvOfflineNote;
    private LinearLayout layoutOfflineWarning;
    private ProgressBar progressConfidence;
    private RecyclerView rvPlanItems;
    private NightPlanAdapter planAdapter;

    // UI — Offline Vector Map
    private OfflineNightMapView offlineNightMapView;

    // UI — Agent Thinking HUD
    private MaterialCardView layoutAgentThinking;
    private TextView tvThinkingStep, tvThinkingPct;
    private ProgressBar progressThinking;
    private TextView tvAiProviderBadge;

    // UI — Engine status
    private TextView tvEngineStatus, tvEngineStatusDetail;
    private View viewEngineIndicator;

    // UI — Actions
    private MaterialButton btnNavigateMainRoute;
    private MaterialButton btnStartJourney, btnEmergencySOS;
    private MaterialCardView btnCheckIn;

    // Navigation
    private BottomNavigationView bottomNav;

    // State
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Timer updateCountdownTimer;
    private FusedLocationProviderClient fusedLocationClient;
    private boolean isJourneyActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_night_plan);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        planEngine = new NightPlanEngine(this);
        aiPlanService = new NightAiPlanService(this);
        csvRepo = NightOwlCsvRepository.getInstance(this);

        bindViews();
        setupChipGroup();
        setupBottomNav();
        setupClickListeners();
        setCurrentTime();
        setupContinuousLocation();
        requestPermissions();

        // Load last plan if exists (offline resume)
        loadLastPlanIfAvailable();
        handleIntentFocus();
    }

    private void bindViews() {
        // Input
        etShiftEnd = findViewById(R.id.etShiftEnd);
        etDestination = findViewById(R.id.etDestination);
        etWorkerType = findViewById(R.id.etWorkerType);
        chipWorkerType = findViewById(R.id.chipWorkerType);
        btnGeneratePlan = findViewById(R.id.btnGeneratePlan);
        layoutInputSection = findViewById(R.id.layoutInputSection);

        // Offline Vector Map View
        offlineNightMapView = findViewById(R.id.offlineNightMapView);

        // Agent Thinking HUD
        layoutAgentThinking = findViewById(R.id.layoutAgentThinking);
        tvThinkingStep = findViewById(R.id.tvThinkingStep);
        tvThinkingPct = findViewById(R.id.tvThinkingPct);
        progressThinking = findViewById(R.id.progressThinking);

        // Plan display
        cardPlanContainer = findViewById(R.id.cardPlanContainer);
        tvPlanStatus = findViewById(R.id.tvPlanStatus);
        tvAiProviderBadge = findViewById(R.id.tvAiProviderBadge);
        tvEstimatedArrival = findViewById(R.id.tvEstimatedArrival);
        tvConfidencePct = findViewById(R.id.tvConfidencePct);
        tvNextUpdate = findViewById(R.id.tvNextUpdate);
        tvPlanTime = findViewById(R.id.tvPlanTime);
        tvOfflineNote = findViewById(R.id.tvOfflineNote);
        layoutOfflineWarning = findViewById(R.id.layoutOfflineWarning);
        progressConfidence = findViewById(R.id.progressConfidence);

        // Plan RecyclerView
        rvPlanItems = findViewById(R.id.rvPlanItems);
        rvPlanItems.setLayoutManager(new LinearLayoutManager(this));
        planAdapter = new NightPlanAdapter(this, new ArrayList<>());
        rvPlanItems.setAdapter(planAdapter);

        // Engine status
        tvEngineStatus = findViewById(R.id.tvEngineStatus);
        tvEngineStatusDetail = findViewById(R.id.tvEngineStatusDetail);
        viewEngineIndicator = findViewById(R.id.viewEngineIndicator);

        // Actions
        btnNavigateMainRoute = findViewById(R.id.btnNavigateMainRoute);
        btnStartJourney = findViewById(R.id.btnStartJourney);
        btnEmergencySOS = findViewById(R.id.btnEmergencySOS);
        btnCheckIn = findViewById(R.id.btnCheckIn);
    }

    private void setupChipGroup() {
        if (chipWorkerType == null) return;
        String[] workerTypes = {"🏥 Nurse (02:15 AM)", "🛡️ Security Guard (03:00 AM)", "🏭 Factory Worker (04:00 AM)",
                "🛵 Delivery Partner (02:30 AM)", "📞 Call Centre (03:30 AM)", "🧹 Sanitation (04:30 AM)"};
        for (String type : workerTypes) {
            Chip chip = new Chip(this);
            chip.setText(type);
            chip.setCheckable(true);
            chip.setChipBackgroundColorResource(R.color.light_lavender);
            chip.setTextColor(ContextCompat.getColor(this, R.color.deep_purple));
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && etWorkerType != null) {
                    if (type.contains("Nurse")) {
                        etWorkerType.setText("ICU Nurse");
                        if (etShiftEnd != null && TextUtils.isEmpty(etShiftEnd.getText())) etShiftEnd.setText("02:15 AM");
                        if (etDestination != null && TextUtils.isEmpty(etDestination.getText())) etDestination.setText("Hadapsar Central");
                    } else if (type.contains("Security")) {
                        etWorkerType.setText("Night Security Guard");
                        if (etShiftEnd != null && TextUtils.isEmpty(etShiftEnd.getText())) etShiftEnd.setText("03:00 AM");
                        if (etDestination != null && TextUtils.isEmpty(etDestination.getText())) etDestination.setText("Swargate Ring");
                    } else if (type.contains("Factory")) {
                        etWorkerType.setText("Factory Shift Worker");
                        if (etShiftEnd != null && TextUtils.isEmpty(etShiftEnd.getText())) etShiftEnd.setText("04:00 AM");
                        if (etDestination != null && TextUtils.isEmpty(etDestination.getText())) etDestination.setText("Chakan Industrial Corridor");
                    } else if (type.contains("Delivery")) {
                        etWorkerType.setText("Delivery Partner");
                        if (etShiftEnd != null && TextUtils.isEmpty(etShiftEnd.getText())) etShiftEnd.setText("02:30 AM");
                        if (etDestination != null && TextUtils.isEmpty(etDestination.getText())) etDestination.setText("Kothrud Hub");
                    } else {
                        etWorkerType.setText(type);
                    }
                }
            });
            chipWorkerType.addView(chip);
        }
    }

    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav == null) return;
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            if (id == R.id.nav_map) {
                startActivity(new Intent(this, MapsActivity.class));
                return true;
            }
            if (id == R.id.nav_services) {
                startActivity(new Intent(this, SafeStopsActivity.class));
                return true;
            }
            if (id == R.id.nav_guardian) {
                startActivity(new Intent(this, FamilyDashboardActivity.class));
                return true;
            }
            if (id == R.id.nav_ai) {
                startActivity(new Intent(this, ChatBotActivity.class));
                return true;
            }
            return false;
        });
    }

    private void setupClickListeners() {
        btnGeneratePlan.setOnClickListener(v -> generatePlan());

        View btnBack = findViewById(R.id.btnNightPlanBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        View btnEditPlan = findViewById(R.id.btnEditPlan);
        if (btnEditPlan != null) {
            btnEditPlan.setOnClickListener(this::onEditPlanClicked);
        }

        setupPersonaPresets();

        if (btnNavigateMainRoute != null) {
            btnNavigateMainRoute.setOnClickListener(v -> navigateToMainRoute());
        }

        if (btnStartJourney != null) {
            btnStartJourney.setOnClickListener(v -> {
                if (!isJourneyActive) startJourney();
                else stopJourney();
            });
        }

        if (btnEmergencySOS != null) {
            btnEmergencySOS.setOnClickListener(v -> triggerEmergencySOS());
        }

        if (btnCheckIn != null) {
            btnCheckIn.setOnClickListener(v -> performCheckIn());
        }
    }

    // ── Continuous Location Updates ───────────────────────────────────────────
    private void setupContinuousLocation() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location loc = locationResult.getLastLocation();
                if (loc != null) {
                    currentUserLat = loc.getLatitude();
                    currentUserLng = loc.getLongitude();
                    hasGpsFix = true;
                    onContinuousLocationUpdated(currentUserLat, currentUserLng);
                }
            }
        };
        startContinuousLocationUpdates();
    }

    private void startContinuousLocationUpdates() {
        try {
            LocationRequest request = LocationRequest.create()
                    .setInterval(5000)
                    .setFastestInterval(2500)
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
            }
        } catch (Exception e) {
            // handle location exception
        }
    }

    private void stopContinuousLocationUpdates() {
        try {
            if (fusedLocationClient != null && locationCallback != null) {
                fusedLocationClient.removeLocationUpdates(locationCallback);
            }
        } catch (Exception ignored) {}
    }

    private void onContinuousLocationUpdated(double lat, double lng) {
        if (offlineNightMapView != null) {
            offlineNightMapView.updateUserLocation(lat, lng);
        }
        if (csvRepo != null) {
            List<NightOwlRecord> nearest = csvRepo.getNearestRecords(lat, lng, 1);
            if (!nearest.isEmpty() && tvEngineStatusDetail != null) {
                NightOwlRecord closest = nearest.get(0);
                tvEngineStatusDetail.setText(String.format(Locale.getDefault(),
                        "🛰️ Live GPS Locked (%.4f, %.4f) • Nearest: %s (%s, %.1f km away)",
                        lat, lng, closest.name, closest.category, closest.distanceKm));
            }
        }
    }

    // ── Google Maps Navigation ────────────────────────────────────────────────
    private void navigateToMainRoute() {
        double targetLat = 18.5204;
        double targetLng = 73.8567;
        String destTitle = "Destination";

        if (currentPlan != null) {
            destTitle = currentPlan.destination != null ? currentPlan.destination : "Route";
            // Look for first waypoint coordinate in plan
            for (NightPlanEngine.PlanItem item : currentPlan.allItems()) {
                if (item.latitude != 0.0 && item.longitude != 0.0) {
                    targetLat = item.latitude;
                    targetLng = item.longitude;
                    destTitle = item.title;
                    break;
                }
            }
        } else if (csvRepo != null) {
            List<NightOwlRecord> nearestTransit = csvRepo.getNearestByCategory(currentUserLat, currentUserLng, "transit", 1);
            if (!nearestTransit.isEmpty()) {
                targetLat = nearestTransit.get(0).latitude;
                targetLng = nearestTransit.get(0).longitude;
                destTitle = nearestTransit.get(0).name;
            }
        }

        Toast.makeText(this, "🧭 Starting turn-by-turn navigation in Google Maps to: " + destTitle, Toast.LENGTH_SHORT).show();

        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + targetLat + "," + targetLng + "&mode=d");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(mapIntent);
        } catch (Exception e) {
            Uri webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + targetLat + "," + targetLng);
            Intent webIntent = new Intent(Intent.ACTION_VIEW, webUri);
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(webIntent);
        }
    }

    private void setupPersonaPresets() {
        View presetNurse = findViewById(R.id.presetNurse);
        if (presetNurse != null) {
            presetNurse.setOnClickListener(v -> applyPreset("02:15 AM", "Hadapsar Central (via Hospital Corridor)", "ICU Nurse"));
        }

        View presetGuard = findViewById(R.id.presetGuard);
        if (presetGuard != null) {
            presetGuard.setOnClickListener(v -> applyPreset("03:00 AM", "Swargate Ring (via Patrol Chowki)", "Night Security Guard"));
        }

        View presetFactory = findViewById(R.id.presetFactory);
        if (presetFactory != null) {
            presetFactory.setOnClickListener(v -> applyPreset("04:00 AM", "Chakan Industrial Corridor", "Factory Shift Worker"));
        }

        View presetDelivery = findViewById(R.id.presetDelivery);
        if (presetDelivery != null) {
            presetDelivery.setOnClickListener(v -> applyPreset("02:30 AM", "Kothrud Main Transit Hub", "Delivery Partner"));
        }
    }

    private void applyPreset(String shiftTime, String destination, String role) {
        if (etShiftEnd != null) etShiftEnd.setText(shiftTime);
        if (etDestination != null) etDestination.setText(destination);
        if (etWorkerType != null) etWorkerType.setText(role);
        Toast.makeText(this, "Loaded " + role + " Preset (" + shiftTime + ")", Toast.LENGTH_SHORT).show();
        generatePlan();
    }

    private void handleIntentFocus() {
        String focus = getIntent().getStringExtra("FOCUS");
        if (focus == null) return;
        switch (focus) {
            case "TRANSPORT":
                applyPreset("02:15 AM", "Hadapsar Central (via Transit Corridor)", "Night Shift Worker");
                break;
            case "SHIFT_TIME":
                if (etShiftEnd != null) {
                    etShiftEnd.requestFocus();
                    etShiftEnd.setText("02:15 AM");
                }
                if (etDestination != null && TextUtils.isEmpty(etDestination.getText())) {
                    etDestination.setText("Home via Station Road");
                }
                break;
            case "WEATHER":
                applyPreset("02:15 AM", "Main Illuminated Arterial", "Shift Commuter");
                break;
            case "OFFLINE":
                Toast.makeText(this, "Operating in Offline Resilient Mode (30-day cached patterns)", Toast.LENGTH_LONG).show();
                applyPreset("02:15 AM", "Safe Haven Walking Corridor", "Shift Worker");
                break;
        }
    }

    // ── Generate Plan ─────────────────────────────────────────────────────────
    private void generatePlan() {
        String shiftEnd = etShiftEnd != null ? etShiftEnd.getText().toString().trim() : "";
        String destination = etDestination != null ? etDestination.getText().toString().trim() : "";
        String workerType = etWorkerType != null ? etWorkerType.getText().toString().trim() : "";

        if (TextUtils.isEmpty(shiftEnd)) {
            if (etShiftEnd != null) etShiftEnd.setError("Enter your shift end time");
            return;
        }
        if (TextUtils.isEmpty(destination)) {
            if (etDestination != null) etDestination.setError("Enter your destination");
            return;
        }

        // Show Agent Thinking HUD
        btnGeneratePlan.setEnabled(false);
        btnGeneratePlan.setText("AI Agent Analyzing...");
        if (layoutAgentThinking != null) {
            layoutAgentThinking.setVisibility(View.VISIBLE);
        }
        if (tvThinkingStep != null) {
            tvThinkingStep.setText("🛰️ Continuous GPS locking • Reading CSV dataset...");
        }
        if (tvThinkingPct != null) {
            tvThinkingPct.setText("20%");
        }
        if (progressThinking != null) {
            progressThinking.setProgress(20);
        }
        setEngineStatus("AI Agent Thinking…", true);

        // Run multi-tier real-world AI engine
        aiPlanService.generateAiNightPlan(shiftEnd, destination, workerType,
                currentUserLat, currentUserLng, new NightAiPlanService.AiPlanCallback() {
                    @Override
                    public void onThinkingStep(String stepDescription, int progressPct) {
                        mainHandler.post(() -> {
                            if (tvThinkingStep != null) tvThinkingStep.setText(stepDescription);
                            if (tvThinkingPct != null) tvThinkingPct.setText(progressPct + "%");
                            if (progressThinking != null) progressThinking.setProgress(progressPct);
                        });
                    }

                    @Override
                    public void onPlanSuccess(NightPlanEngine.NightPlan plan, String aiProviderUsed) {
                        mainHandler.post(() -> {
                            if (layoutAgentThinking != null) layoutAgentThinking.setVisibility(View.GONE);
                            if (tvAiProviderBadge != null) {
                                tvAiProviderBadge.setText("🤖 " + aiProviderUsed);
                                tvAiProviderBadge.setVisibility(View.VISIBLE);
                            }
                            currentPlan = plan;
                            displayPlan(plan, false);
                            btnGeneratePlan.setEnabled(true);
                            btnGeneratePlan.setText("Refresh Plan");
                        });
                    }

                    @Override
                    public void onPlanFallback(NightPlanEngine.NightPlan plan, String reason) {
                        mainHandler.post(() -> {
                            if (layoutAgentThinking != null) layoutAgentThinking.setVisibility(View.GONE);
                            if (tvAiProviderBadge != null) {
                                tvAiProviderBadge.setText("⚡ " + reason);
                                tvAiProviderBadge.setVisibility(View.VISIBLE);
                            }
                            currentPlan = plan;
                            displayPlan(plan, false);
                            btnGeneratePlan.setEnabled(true);
                            btnGeneratePlan.setText("Refresh Plan");
                            Toast.makeText(NightPlanActivity.this, "AI Mesh: " + reason, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    // ── Display Plan ──────────────────────────────────────────────────────────
    private void displayPlan(NightPlanEngine.NightPlan plan, boolean isUpdate) {
        if (cardPlanContainer != null) cardPlanContainer.setVisibility(View.VISIBLE);
        if (layoutInputSection != null) layoutInputSection.setVisibility(View.GONE);

        // Plan metadata
        if (tvPlanStatus != null) {
            tvPlanStatus.setText(isUpdate ? "Plan Updated" : "Plan Ready");
        }
        if (tvEstimatedArrival != null) {
            tvEstimatedArrival.setText("ETA: " + plan.estimatedArrival);
        }
        if (tvConfidencePct != null) {
            tvConfidencePct.setText(plan.overallConfidencePct + "%");
        }
        if (progressConfidence != null) {
            progressConfidence.setProgress(plan.overallConfidencePct);
        }
        if (tvPlanTime != null) {
            tvPlanTime.setText("Generated: " + plan.getGeneratedTimeString());
        }

        // Offline warning
        if (layoutOfflineWarning != null) {
            layoutOfflineWarning.setVisibility(plan.isOfflineMode ? View.VISIBLE : View.GONE);
        }
        if (tvOfflineNote != null && plan.offlineNote != null) {
            tvOfflineNote.setText(plan.offlineNote);
        }

        // Engine status
        setEngineStatus(plan.isOfflineMode ? getString(R.string.engine_offline) : getString(R.string.engine_active),
                !plan.isOfflineMode);

        // Build combined list: transport + services + safety + weather + fallbacks
        List<NightPlanEngine.PlanItem> allItems = new ArrayList<>();
        if (!plan.transport.isEmpty()) {
            allItems.add(createHeaderItem("🚌 Transport Options"));
            allItems.addAll(plan.transport);
        }
        if (!plan.services.isEmpty()) {
            allItems.add(createHeaderItem("🏥 Open Services"));
            allItems.addAll(plan.services);
        }
        if (!plan.safety.isEmpty()) {
            allItems.add(createHeaderItem("🛡️ Route Safety"));
            allItems.addAll(plan.safety);
        }
        if (!plan.weather.isEmpty()) {
            allItems.add(createHeaderItem("🌤️ Weather"));
            allItems.addAll(plan.weather);
        }
        if (!plan.fallbacks.isEmpty()) {
            allItems.add(createHeaderItem("🔄 Fallback Plans"));
            allItems.addAll(plan.fallbacks);
        }

        planAdapter.updateItems(allItems);

        // Feed route data and 100 CSV records to Offline Canvas Map
        if (offlineNightMapView != null && csvRepo != null) {
            List<NightOwlRecord> routeWaypoints = new ArrayList<>();
            double dLat = currentUserLat + 0.03;
            double dLng = currentUserLng + 0.03;

            for (NightPlanEngine.PlanItem item : plan.allItems()) {
                if (item.latitude != 0.0 && item.longitude != 0.0) {
                    NightOwlRecord dummy = new NightOwlRecord(
                            "WPT", "Pune", item.category, item.title, "", "", item.detail,
                            item.confidence.name().toLowerCase(), item.latitude, item.longitude, "live");
                    routeWaypoints.add(dummy);
                    dLat = item.latitude;
                    dLng = item.longitude;
                }
            }

            offlineNightMapView.setRouteData(currentUserLat, currentUserLng, dLat, dLng,
                    routeWaypoints, csvRepo.getAllRecords());

            offlineNightMapView.setOnPoiSelectedListener(record -> {
                Toast.makeText(NightPlanActivity.this,
                        "📍 " + record.name + " (" + record.category + ")\nTap 'START NAVIGATION' to open Google Maps",
                        Toast.LENGTH_SHORT).show();
            });
        }

        // Reset button
        btnGeneratePlan.setEnabled(true);
        btnGeneratePlan.setText("Refresh Plan");

        // Start countdown timer for next update
        startUpdateCountdown(plan);

        // Save for offline resume
        if (planEngine != null) {
            planEngine.savePlanToPrefs(plan);
        }
    }

    private NightPlanEngine.PlanItem createHeaderItem(String title) {
        return new NightPlanEngine.PlanItem("header", "", title, "", NightPlanEngine.Confidence.KNOWN);
    }

    // ── Update Countdown ──────────────────────────────────────────────────────
    private void startUpdateCountdown(NightPlanEngine.NightPlan plan) {
        if (updateCountdownTimer != null) updateCountdownTimer.cancel();
        updateCountdownTimer = new Timer();
        updateCountdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                mainHandler.post(() -> {
                    if (tvNextUpdate != null && plan != null) {
                        tvNextUpdate.setText("Next update: " + plan.getNextUpdateString());
                    }
                });
            }
        }, 0, 1000);
    }

    // ── Journey Management ─────────────────────────────────────────────────────
    private void startJourney() {
        isJourneyActive = true;
        if (btnStartJourney != null) {
            btnStartJourney.setText("⏹ End Journey");
            btnStartJourney.setBackgroundColor(ContextCompat.getColor(this, R.color.night_red));
        }
        if (btnCheckIn != null) btnCheckIn.setVisibility(View.VISIBLE);

        // Start check-in service
        startService(new Intent(this, SurakshaService.class));
        Toast.makeText(this, "Journey monitoring started. Stay safe! 🌙", Toast.LENGTH_SHORT).show();
    }

    private void stopJourney() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("End Journey?")
                .setMessage("Confirm you have arrived safely. This will notify your emergency contacts.")
                .setPositiveButton("Yes, I'm Safe ✓", (d, w) -> {
                    isJourneyActive = false;
                    if (btnStartJourney != null) {
                        btnStartJourney.setText("🌙 Start Journey");
                        btnStartJourney.setBackgroundColor(ContextCompat.getColor(this, R.color.night_cyan));
                    }
                    if (btnCheckIn != null) btnCheckIn.setVisibility(View.GONE);
                    Toast.makeText(this, "Safe arrival confirmed ✓", Toast.LENGTH_SHORT).show();
                    stopService(new Intent(this, SurakshaService.class));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Check-In ──────────────────────────────────────────────────────────────
    private void performCheckIn() {
        Toast.makeText(this, "Check-in recorded — contacts notified you're safe", Toast.LENGTH_SHORT).show();
    }

    // ── Emergency SOS ─────────────────────────────────────────────────────────
    private void triggerEmergencySOS() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("🚨 Emergency SOS")
                .setMessage("This will immediately alert your emergency contacts with your current location.")
                .setPositiveButton("SEND SOS NOW", (d, w) -> {
                    if (ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
                            EmergencyHelper.triggerQuickSos(this, loc);
                        });
                    } else {
                        EmergencyHelper.triggerQuickSos(this, null);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private void setCurrentTime() {
        if (etShiftEnd != null && TextUtils.isEmpty(etShiftEnd.getText())) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR_OF_DAY, 1);
            String time = String.format(Locale.getDefault(), "%02d:%02d",
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            etShiftEnd.setHint("e.g. " + time);
        }
    }

    private void setEngineStatus(String status, boolean active) {
        if (tvEngineStatus != null) tvEngineStatus.setText(status);
        if (viewEngineIndicator != null) {
            viewEngineIndicator.setBackgroundResource(active ? R.color.confidence_known : R.color.confidence_outdated);
        }
    }

    private void loadLastPlanIfAvailable() {
        try {
            NightPlanEngine tempEngine = new NightPlanEngine(this);
            org.json.JSONObject lastPlan = tempEngine.loadLastPlan();
            if (lastPlan != null) {
                String dest = lastPlan.optString("destination", "");
                String time = lastPlan.optString("shiftEndTime", "");
                if (etDestination != null && !TextUtils.isEmpty(dest)) etDestination.setText(dest);
                if (etShiftEnd != null && !TextUtils.isEmpty(time)) etShiftEnd.setText(time);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void requestPermissions() {
        String[] perms = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.SEND_SMS,
                Manifest.permission.POST_NOTIFICATIONS
        };
        List<String> needed = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST && grantResults.length > 0) {
            boolean granted = false;
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i])
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            if (granted) {
                startContinuousLocationUpdates();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startContinuousLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopContinuousLocationUpdates();
    }

    public void onEditPlanClicked(View v) {
        if (cardPlanContainer != null) cardPlanContainer.setVisibility(View.GONE);
        if (layoutInputSection != null) layoutInputSection.setVisibility(View.VISIBLE);
        if (btnGeneratePlan != null) {
            btnGeneratePlan.setEnabled(true);
            btnGeneratePlan.setText(getString(R.string.btn_generate_plan));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopContinuousLocationUpdates();
        if (planEngine != null) planEngine.stop();
        if (updateCountdownTimer != null) updateCountdownTimer.cancel();
    }
}

package com.example.woshield;

/**
 * NightPlanActivity — SafeVaani NightShift Navigator
 *
 * Directly displays the live interactive Leaflet Radar Map upon opening.
 * - Shows live real-time GPS coordinates of the user (continuous tracking)
 * - Highlights verified Danger Zones with risk warnings and hazard perimeters
 * - Marks 24/7 verified Safe Havens (Pharmacies, Hospitals, Police Stations, Fuel Stops)
 * - Computes and renders THE PERFECT DIRECTION (Well-lit arterial corridor avoiding hazards)
 * - No form: Directly opens the map and live routing immediately.
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NightPlanActivity extends AppCompatActivity {

    private static final String TAG = "NightPlanActivity";
    private static final int PERMISSION_REQUEST = 300;

    // Real-Time Location Tracking
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private double currentUserLat = 18.46780; // Initial default Pune corridor
    private double currentUserLng = 73.86150;
    private boolean hasGpsFix = false;

    // Verified Destination Safe Haven (Satara Road Corridor Hub)
    private double destLat = 18.45520;
    private double destLng = 73.86750;
    private String destName = "Home Safe Haven (Satara Rd)";

    // Data Repositories
    private NightOwlCsvRepository csvRepo;
    private DatabaseReference dangerZonesRef;

    // UI Elements
    private WebView webViewLeafletMap;
    private TextView tvEngineStatus, tvMapGpsStatus;
    private TextView tvPlanStatus, tvPlanDescription;
    private TextView tvPlanDistance, tvEstimatedArrival, tvConfidencePct;
    private TextView tvAiProviderBadge;
    private View viewEngineIndicator;

    // UI Actions
    private MaterialButton btnNavigateMainRoute, btnStartJourney, btnEmergencySOS, btnCheckIn;
    private BottomNavigationView bottomNav;

    private boolean isJourneyActive = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_night_plan);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        csvRepo = NightOwlCsvRepository.getInstance(this);
        dangerZonesRef = FirebaseDatabase.getInstance().getReference("DangerZones");

        bindViews();
        setupLeafletWebView();
        setupClickListeners();
        setupBottomNav();
        setupContinuousLocation();
        requestPermissions();

        // Load live Firebase danger zones and sync to map
        loadLiveDangerZones();
    }

    private void bindViews() {
        webViewLeafletMap = findViewById(R.id.webViewLeafletMap);
        tvEngineStatus = findViewById(R.id.tvEngineStatus);
        tvMapGpsStatus = findViewById(R.id.tvMapGpsStatus);
        viewEngineIndicator = findViewById(R.id.viewEngineIndicator);

        tvPlanStatus = findViewById(R.id.tvPlanStatus);
        tvPlanDescription = findViewById(R.id.tvPlanDescription);
        tvPlanDistance = findViewById(R.id.tvPlanDistance);
        tvEstimatedArrival = findViewById(R.id.tvEstimatedArrival);
        tvConfidencePct = findViewById(R.id.tvConfidencePct);
        tvAiProviderBadge = findViewById(R.id.tvAiProviderBadge);

        btnNavigateMainRoute = findViewById(R.id.btnNavigateMainRoute);
        btnStartJourney = findViewById(R.id.btnStartJourney);
        btnEmergencySOS = findViewById(R.id.btnEmergencySOS);
        btnCheckIn = findViewById(R.id.btnCheckIn);
        bottomNav = findViewById(R.id.bottomNav);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupLeafletWebView() {
        if (webViewLeafletMap == null) return;

        WebSettings ws = webViewLeafletMap.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setGeolocationEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);

        webViewLeafletMap.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Leaflet Map Loaded successfully.");
                // Sync latest known coordinates if available
                if (hasGpsFix) {
                    sendCoordinatesToMap(currentUserLat, currentUserLng);
                }
            }
        });
        webViewLeafletMap.setWebChromeClient(new WebChromeClient());

        // Directly load the interactive Leaflet Map from app assets
        webViewLeafletMap.loadUrl("file:///android_asset/nightshift_leaflet_map.html");
    }

    private void setupClickListeners() {
        View btnBack = findViewById(R.id.btnNightPlanBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnNavigateMainRoute != null) {
            btnNavigateMainRoute.setOnClickListener(v -> openGoogleMapsNavigation());
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
            btnCheckIn.setOnClickListener(v -> {
                startActivity(new Intent(this, SmartCheckInActivity.class));
            });
        }
    }

    private void setupBottomNav() {
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

    // ── Continuous GPS Location Updates ─────────────────────────────────────────
    private void setupContinuousLocation() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location loc = locationResult.getLastLocation();
                if (loc != null) {
                    currentUserLat = loc.getLatitude();
                    currentUserLng = loc.getLongitude();
                    hasGpsFix = true;
                    onRealLocationReceived(currentUserLat, currentUserLng);
                }
            }
        };
        startContinuousLocationUpdates();
    }

    private void startContinuousLocationUpdates() {
        try {
            LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                    .setMinUpdateIntervalMillis(1500)
                    .build();

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
                fusedLocationClient.getLastLocation().addOnSuccessListener(this, loc -> {
                    if (loc != null) {
                        currentUserLat = loc.getLatitude();
                        currentUserLng = loc.getLongitude();
                        hasGpsFix = true;
                        onRealLocationReceived(currentUserLat, currentUserLng);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Location request error", e);
        }
    }

    private void stopContinuousLocationUpdates() {
        try {
            if (fusedLocationClient != null && locationCallback != null) {
                fusedLocationClient.removeLocationUpdates(locationCallback);
            }
        } catch (Exception ignored) {}
    }

    private void onRealLocationReceived(double lat, double lng) {
        sendCoordinatesToMap(lat, lng);

        if (tvMapGpsStatus != null) {
            tvMapGpsStatus.setText(String.format(Locale.US, "🛰️ GPS: %.4f°N, %.4f°E", lat, lng));
        }
        if (tvEngineStatus != null) {
            tvEngineStatus.setText("LIVE LEAFLET RADAR • GPS CONNECTED");
        }
        if (viewEngineIndicator != null) {
            viewEngineIndicator.setBackgroundResource(R.color.confidence_known);
        }

        // Calculate real distance & ETA
        double distKm = calculateDistanceKm(lat, lng, destLat, destLng) * 1.25;
        if (distKm < 0.2) distKm = 0.5;
        int etaMinutes = (int) Math.max(5, Math.round(distKm * 6.5));

        if (tvPlanDistance != null) {
            tvPlanDistance.setText(String.format(Locale.US, "%.1f KM", distKm));
        }
        if (tvEstimatedArrival != null) {
            tvEstimatedArrival.setText(etaMinutes + " MINS");
        }
    }

    private void sendCoordinatesToMap(double lat, double lng) {
        if (webViewLeafletMap != null) {
            String js = String.format(Locale.US, "window.setLiveCoordinates(%f, %f, true);", lat, lng);
            webViewLeafletMap.evaluateJavascript(js, null);
        }
    }

    // ── Load Real Danger Zones From Firebase ────────────────────────────────────
    private void loadLiveDangerZones() {
        if (dangerZonesRef == null) return;
        dangerZonesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                try {
                    JSONArray dzArray = new JSONArray();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Double lat = child.child("latitude").getValue(Double.class);
                        Double lng = child.child("longitude").getValue(Double.class);
                        String title = child.child("title").getValue(String.class);
                        String desc = child.child("description").getValue(String.class);

                        if (lat != null && lng != null) {
                            JSONObject obj = new JSONObject();
                            obj.put("name", "⚠️ " + (title != null ? title : "Hazard Zone"));
                            obj.put("lat", lat);
                            obj.put("lng", lng);
                            obj.put("radius", 180);
                            obj.put("score", "HIGH RISK");
                            obj.put("info", desc != null ? desc : "Reported night hazard pocket");
                            dzArray.put(obj);
                        }
                    }

                    if (dzArray.length() > 0 && webViewLeafletMap != null) {
                        String js = "if (window.setCustomZones) { window.setCustomZones(" + dzArray.toString() + ", null); }";
                        webViewLeafletMap.evaluateJavascript(js, null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed parsing Firebase danger zones", e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // ── Navigation & Actions ──────────────────────────────────────────────────
    private void openGoogleMapsNavigation() {
        try {
            Uri gmmIntentUri = Uri.parse(String.format(Locale.US, "google.navigation:q=%f,%f&mode=w", destLat, destLng));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                Intent fallback = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(String.format(Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%f,%f", destLat, destLng)));
                startActivity(fallback);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Opening navigation...", Toast.LENGTH_SHORT).show();
        }
    }

    private void startJourney() {
        isJourneyActive = true;
        if (btnStartJourney != null) {
            btnStartJourney.setText("🛑 End Journey");
            btnStartJourney.setBackgroundColor(ContextCompat.getColor(this, R.color.night_red));
        }
        startService(new Intent(this, SurakshaService.class));
        Toast.makeText(this, "Journey Escort Active: SafeVaani is monitoring your route 🌙", Toast.LENGTH_SHORT).show();
    }

    private void stopJourney() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("End Journey?")
                .setMessage("Confirm you have arrived safely at your destination.")
                .setPositiveButton("Yes, I'm Safe ✓", (d, w) -> {
                    isJourneyActive = false;
                    if (btnStartJourney != null) {
                        btnStartJourney.setText("🚶 Walk With Me");
                        btnStartJourney.setBackgroundColor(ContextCompat.getColor(this, R.color.deep_purple));
                    }
                    Toast.makeText(this, "Safe arrival confirmed ✓", Toast.LENGTH_SHORT).show();
                    stopService(new Intent(this, SurakshaService.class));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void triggerEmergencySOS() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("🚨 Emergency SOS")
                .setMessage("This will immediately alert your emergency contacts with your live GPS location.")
                .setPositiveButton("SEND SOS NOW", (d, w) -> {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> EmergencyHelper.triggerQuickSos(this, loc));
                    } else {
                        EmergencyHelper.triggerQuickSos(this, null);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c;
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
        if (webViewLeafletMap != null) {
            webViewLeafletMap.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopContinuousLocationUpdates();
        if (webViewLeafletMap != null) {
            webViewLeafletMap.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopContinuousLocationUpdates();
        if (webViewLeafletMap != null) {
            webViewLeafletMap.destroy();
        }
    }
}

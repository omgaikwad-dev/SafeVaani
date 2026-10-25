package com.example.woshield;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "MapsActivity";

    // Intent extras
    public static final String EXTRA_ROUTINE = "extra_routine";
    public static final String EXTRA_SHOW_ALL_ROUTINES = "extra_show_all_routines";

    // Pune Core Anchor Coordinates centered on user's prompt (18°27'44.1"N 73°51'53.9"E)
    private static final double USER_LAT = 18.46225;
    private static final double USER_LON = 73.864972;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng userLocation;
    private DatabaseReference dangerZoneRef;

    private final List<LatLng> dangerZones = new ArrayList<>();
    private final List<Circle> dangerZoneCircles = new ArrayList<>();
    private final List<Marker> dangerZoneMarkers = new ArrayList<>();
    private final List<SafePlace> safePlacesList = new ArrayList<>();
    private final List<Routine> allRoutinesList = new ArrayList<>();
    private final List<Polyline> routePolylines = new ArrayList<>();
    private final List<Marker> routineMarkers = new ArrayList<>();

    private Routine activeRoutine;
    private boolean showAllRoutines;
    private LatLngBounds calculatedRouteBounds;

    // UI elements
    private TextView tvMapTitle, tvMapSubtitle;
    private TextView tvRoutineCategory, tvRoutineTime, tvRoutineStart, tvRoutineDest, tvSafeStopsCount;
    private MaterialCardView cardRoutineRouteInfo;
    private View btnCheckDangerZone, btnStartCheckIn;
    private ImageView btnMapBack, btnFitRoute, btnToggleOfflineMap;
    private OfflineNightMapView offlineMapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        activeRoutine = (Routine) getIntent().getSerializableExtra(EXTRA_ROUTINE);
        showAllRoutines = getIntent().getBooleanExtra(EXTRA_SHOW_ALL_ROUTINES, false);

        initViews();
        setupViewContent();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        dangerZoneRef = FirebaseDatabase.getInstance().getReference("DangerZones");

        // Automatically switch to Offline Vector Map if mobile network is OFF
        if (!NightAiPlanService.isNetworkAvailable(this)) {
            toggleOfflineMap();
        }
    }

    private void initViews() {
        tvMapTitle = findViewById(R.id.tv_map_title);
        tvMapSubtitle = findViewById(R.id.tv_map_subtitle);
        tvRoutineCategory = findViewById(R.id.tv_map_routine_category);
        tvRoutineTime = findViewById(R.id.tv_map_routine_time);
        tvRoutineStart = findViewById(R.id.tv_map_routine_start);
        tvRoutineDest = findViewById(R.id.tv_map_routine_destination);
        tvSafeStopsCount = findViewById(R.id.tv_map_safe_stops_count);
        cardRoutineRouteInfo = findViewById(R.id.card_routine_route_info);

        btnCheckDangerZone = findViewById(R.id.btn_check_danger_zone);
        btnStartCheckIn = findViewById(R.id.btn_start_journey_checkin);
        btnMapBack = findViewById(R.id.btn_map_back);
        btnFitRoute = findViewById(R.id.btn_fit_route);
        btnToggleOfflineMap = findViewById(R.id.btn_toggle_offline_map);
        offlineMapView = findViewById(R.id.offline_map_view);

        if (btnMapBack != null) {
            btnMapBack.setOnClickListener(v -> finish());
        }

        if (btnFitRoute != null) {
            btnFitRoute.setOnClickListener(v -> fitRouteOnMap());
        }

        if (btnToggleOfflineMap != null) {
            btnToggleOfflineMap.setOnClickListener(v -> toggleOfflineMap());
        }

        if (btnCheckDangerZone != null) {
            btnCheckDangerZone.setOnClickListener(v -> checkUserInDangerZone());
        }

        if (btnStartCheckIn != null) {
            btnStartCheckIn.setOnClickListener(v -> startCheckInForRoutine());
        }
    }

    private void toggleOfflineMap() {
        if (offlineMapView == null) return;
        boolean willShow = offlineMapView.getVisibility() != View.VISIBLE;
        offlineMapView.setVisibility(willShow ? View.VISIBLE : View.GONE);
        if (willShow) {
            setupOfflineMapData();
            Toast.makeText(this, "🛰️ Switched to 100% Offline Vector Map (Zero Data Needed)", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Switched to Online Google Map", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupOfflineMapData() {
        if (offlineMapView == null) return;
        NightOwlCsvRepository repo = NightOwlCsvRepository.getInstance(this);
        double curLat = userLocation != null ? userLocation.latitude : USER_LAT;
        double curLng = userLocation != null ? userLocation.longitude : USER_LON;
        double dLat = curLat + 0.025;
        double dLng = curLng + 0.025;

        List<NightOwlRecord> waypoints = repo.findBestSafeWaypoints(curLat, curLng);
        offlineMapView.setRouteData(curLat, curLng, dLat, dLng, waypoints, repo.getAllRecords());
    }

    private void setupViewContent() {
        if (activeRoutine != null) {
            if (tvMapTitle != null) tvMapTitle.setText(activeRoutine.getTitle());
            if (tvMapSubtitle != null) tvMapSubtitle.setText("Live Commute Route & Verified Havens");
            if (tvRoutineCategory != null) tvRoutineCategory.setText(activeRoutine.getCategory() != null ? activeRoutine.getCategory().toUpperCase() : "COMMUTE");
            if (tvRoutineTime != null) {
                String timeStr = activeRoutine.getStartTime() + " → " + activeRoutine.getArrivalTime();
                if (activeRoutine.getExpectedDurationMinutes() > 0) {
                    timeStr += " (" + activeRoutine.getExpectedDurationMinutes() + " mins)";
                }
                tvRoutineTime.setText(timeStr);
            }
            if (tvRoutineStart != null) tvRoutineStart.setText(activeRoutine.getStartLocation());
            if (tvRoutineDest != null) tvRoutineDest.setText(activeRoutine.getDestination());
            if (tvSafeStopsCount != null) {
                int stopsCount = activeRoutine.getSafeStopIds() != null ? activeRoutine.getSafeStopIds().size() : 0;
                tvSafeStopsCount.setText("🛡️ " + (stopsCount > 0 ? stopsCount + " Safe Stops on this route" : "Monitored Safe Commute Corridor"));
            }
        } else {
            if (tvMapTitle != null) tvMapTitle.setText("Daily Routines & Safety Map");
            if (tvMapSubtitle != null) tvMapSubtitle.setText("Commute Routes, Safe Havens & Risk Zones");
            if (tvRoutineCategory != null) tvRoutineCategory.setText("ALL ROUTES");
            if (tvRoutineTime != null) tvRoutineTime.setText("Real-Time Safety Layer");
            if (tvRoutineStart != null) tvRoutineStart.setText("Campus & Transit Corridors");
            if (tvRoutineDest != null) tvRoutineDest.setText("Home Safe Havens");
            if (tvSafeStopsCount != null) tvSafeStopsCount.setText("🛡️ Verified Trusted Safe Stops Active");
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        try {
            mMap.getUiSettings().setZoomControlsEnabled(true);
            mMap.getUiSettings().setCompassEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        } catch (Exception ignored) {
        }

        getUserLocation();
        loadDangerZones();
        loadSafePlacesAndPlotRoutes();

        // Allow user to add new danger zones
        mMap.setOnMapLongClickListener(this::addDangerZone);
    }

    private void getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null && mMap != null) {
                userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.addMarker(new MarkerOptions()
                        .position(userLocation)
                        .title("Your Current Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            }
        });
    }

    private void loadDangerZones() {
        dangerZoneRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                dangerZones.clear();
                for (Circle circle : dangerZoneCircles) {
                    circle.remove();
                }
                dangerZoneCircles.clear();
                for (Marker m : dangerZoneMarkers) {
                    m.remove();
                }
                dangerZoneMarkers.clear();

                for (DataSnapshot zoneSnapshot : snapshot.getChildren()) {
                    Double lat = zoneSnapshot.child("latitude").getValue(Double.class);
                    Double lng = zoneSnapshot.child("longitude").getValue(Double.class);
                    if (lat != null && lng != null) {
                        LatLng location = new LatLng(lat, lng);
                        dangerZones.add(location);

                        Circle circle = mMap.addCircle(new CircleOptions()
                                .center(location)
                                .radius(120)
                                .strokeColor(0xFFFF0000)
                                .fillColor(0x33FF0000));
                        dangerZoneCircles.add(circle);

                        Marker marker = mMap.addMarker(new MarkerOptions()
                                .position(location)
                                .title("Danger Zone")
                                .snippet("High-risk or dimly lit area")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
                        if (marker != null) dangerZoneMarkers.add(marker);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load danger zones", error.toException());
            }
        });
    }

    private void loadSafePlacesAndPlotRoutes() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "demo_user_123";

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(uid);

        // 1. Fetch Safe Places
        userRef.child("safePlaces").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                safePlacesList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    SafePlace place = ds.getValue(SafePlace.class);
                    if (place != null) {
                        if (place.getId() == null) place.setId(ds.getKey());
                        safePlacesList.add(place);
                    }
                }

                // If empty (e.g. offline demo), add standard Pune safe havens
                if (safePlacesList.isEmpty()) {
                    safePlacesList.add(new SafePlace("p1", uid, "Home Safe Haven", "HOME", "Bibwewadi-Dhankawadi Road, Pune", USER_LAT, USER_LON, true, "Primary Safe Haven", true));
                    safePlacesList.add(new SafePlace("p2", uid, "Bharati Vidyapeeth Women Safety Cell", "UNIVERSITY", "Dhankawadi Campus, Pune", 18.46780, 73.86150, true, "Campus Security: 1091", true));
                    safePlacesList.add(new SafePlace("p3", uid, "Katraj Police Helpdesk & Safety Post", "POLICE_STATION", "Katraj Transit Corridor, Pune", 18.45520, 73.86750, true, "Police Helpdesk: 100", true));
                }

                plotSafePlacesOnMap();

                // 2. Fetch routines or plot active routine
                if (activeRoutine != null) {
                    plotSingleRoutineRoute(activeRoutine);
                } else {
                    loadAllRoutinesAndPlot(userRef);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load safe places", error.toException());
                if (activeRoutine != null) {
                    plotSingleRoutineRoute(activeRoutine);
                }
            }
        });
    }

    private void plotSafePlacesOnMap() {
        if (mMap == null) return;
        for (SafePlace place : safePlacesList) {
            if (place.isHasCoordinates() && place.getLatitude() != 0.0) {
                LatLng pos = new LatLng(place.getLatitude(), place.getLongitude());
                mMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .title("🛡️ " + place.getName())
                        .snippet(place.getAddress() != null ? place.getAddress() : "Safe Haven")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            }
        }
    }

    private void plotSingleRoutineRoute(Routine routine) {
        if (mMap == null || routine == null) return;

        // Clear existing routine drawings
        for (Polyline polyline : routePolylines) {
            polyline.remove();
        }
        routePolylines.clear();
        for (Marker marker : routineMarkers) {
            marker.remove();
        }
        routineMarkers.clear();

        LatLng startLatLng = resolveCoordinates(routine.getStartLocation(), 18.46780, 73.86150);
        LatLng destLatLng = resolveCoordinates(routine.getDestination(), USER_LAT, USER_LON);

        List<LatLng> routePoints = buildRoutePoints(routine.getTitle(), startLatLng, destLatLng);

        Polyline polyline = mMap.addPolyline(new PolylineOptions()
                .addAll(routePoints)
                .width(14f)
                .color(Color.parseColor("#6D4AFF"))
                .geodesic(true)
                .startCap(new RoundCap())
                .endCap(new RoundCap())
                .jointType(JointType.ROUND));
        routePolylines.add(polyline);

        Marker startMarker = mMap.addMarker(new MarkerOptions()
                .position(startLatLng)
                .title("Start: " + routine.getStartLocation())
                .snippet("Scheduled Departure: " + routine.getStartTime())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));
        if (startMarker != null) routineMarkers.add(startMarker);

        Marker destMarker = mMap.addMarker(new MarkerOptions()
                .position(destLatLng)
                .title("Destination: " + routine.getDestination())
                .snippet("Scheduled Arrival: " + routine.getArrivalTime())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        if (destMarker != null) routineMarkers.add(destMarker);

        // Build camera bounds
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng pt : routePoints) {
            builder.include(pt);
        }
        for (SafePlace sp : safePlacesList) {
            if (sp.isHasCoordinates() && sp.getLatitude() != 0.0) {
                builder.include(new LatLng(sp.getLatitude(), sp.getLongitude()));
            }
        }
        for (LatLng dz : dangerZones) {
            builder.include(dz);
        }

        try {
            calculatedRouteBounds = builder.build();
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(calculatedRouteBounds, 140));
        } catch (Exception e) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, 15));
        }
    }

    private void loadAllRoutinesAndPlot(DatabaseReference userRef) {
        userRef.child("routines").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allRoutinesList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Routine r = ds.getValue(Routine.class);
                    if (r != null) {
                        if (r.getId() == null) r.setId(ds.getKey());
                        allRoutinesList.add(r);
                    }
                }

                if (allRoutinesList.isEmpty()) {
                    // Fallback to sample routines
                    allRoutinesList.add(new Routine(
                            "routine_metro_commute", "demo", "Evening Campus Commute", "COMMUTE",
                            "Bharati Vidyapeeth Campus Gate", "Home Safe Haven (Bibwewadi)",
                            "18:00", "19:30", 18, 0, 19, 30,
                            Arrays.asList("MON", "TUE", "WED", "THU", "FRI"), 90, true
                    ));
                    allRoutinesList.add(new Routine(
                            "routine_morning_walk", "demo", "Morning Neighborhood Jog", "EXERCISE",
                            "Home Safe Haven (Bibwewadi)", "Katraj Lake Green Belt",
                            "06:30", "07:30", 6, 30, 7, 30,
                            Arrays.asList("MON", "WED", "FRI", "SAT", "SUN"), 60, true
                    ));
                }

                plotAllRoutines();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load routines", error.toException());
            }
        });
    }

    private void plotAllRoutines() {
        if (mMap == null || allRoutinesList.isEmpty()) return;

        for (Polyline polyline : routePolylines) {
            polyline.remove();
        }
        routePolylines.clear();

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        int[] routeColors = {
                Color.parseColor("#6D4AFF"), // Royal Violet (Commute)
                Color.parseColor("#00897B"), // Teal (Exercise/Jog)
                Color.parseColor("#E65100")  // Amber
        };

        for (int i = 0; i < allRoutinesList.size(); i++) {
            Routine r = allRoutinesList.get(i);
            LatLng start = resolveCoordinates(r.getStartLocation(), 18.46780, 73.86150);
            LatLng dest = resolveCoordinates(r.getDestination(), USER_LAT, USER_LON);
            List<LatLng> points = buildRoutePoints(r.getTitle(), start, dest);

            int color = routeColors[i % routeColors.length];
            Polyline polyline = mMap.addPolyline(new PolylineOptions()
                    .addAll(points)
                    .width(12f)
                    .color(color)
                    .geodesic(true)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap())
                    .jointType(JointType.ROUND));
            routePolylines.add(polyline);

            mMap.addMarker(new MarkerOptions()
                    .position(start)
                    .title("Route: " + r.getTitle())
                    .snippet("Start: " + r.getStartLocation() + " (" + r.getStartTime() + ")")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));

            mMap.addMarker(new MarkerOptions()
                    .position(dest)
                    .title("Destination: " + r.getDestination())
                    .snippet("Arrival: " + r.getArrivalTime())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

            for (LatLng pt : points) {
                builder.include(pt);
            }
        }

        for (SafePlace sp : safePlacesList) {
            if (sp.isHasCoordinates() && sp.getLatitude() != 0.0) {
                builder.include(new LatLng(sp.getLatitude(), sp.getLongitude()));
            }
        }

        try {
            calculatedRouteBounds = builder.build();
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(calculatedRouteBounds, 140));
        } catch (Exception e) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(USER_LAT, USER_LON), 14));
        }

        // Show the first routine in card
        if (!allRoutinesList.isEmpty()) {
            activeRoutine = allRoutinesList.get(0);
            setupViewContent();
        }
    }

    private LatLng resolveCoordinates(String locationName, double defaultLat, double defaultLng) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return new LatLng(defaultLat, defaultLng);
        }
        String lower = locationName.toLowerCase(Locale.ROOT);

        // 1. Coordinates in string matching
        if (lower.contains("18°27'44") || lower.contains("73°51'53")) {
            return new LatLng(USER_LAT, USER_LON);
        }

        // 2. Known landmarks centered on prompt coordinates
        if (lower.contains("home") || lower.contains("bibwewadi") || lower.contains("residency")) {
            return new LatLng(USER_LAT, USER_LON);
        }
        if (lower.contains("campus") || lower.contains("bharati") || lower.contains("vidyapeeth") || lower.contains("college") || lower.contains("university")) {
            return new LatLng(18.46780, 73.86150);
        }
        if (lower.contains("katraj") || lower.contains("jog") || lower.contains("lake") || lower.contains("green belt")) {
            return new LatLng(18.45520, 73.86750);
        }
        if (lower.contains("police") || lower.contains("metro")) {
            return new LatLng(18.45520, 73.86750);
        }

        // 3. Match against safe places list
        for (SafePlace sp : safePlacesList) {
            if (sp.getName() != null && sp.getName().toLowerCase(Locale.ROOT).contains(lower) && sp.isHasCoordinates()) {
                return new LatLng(sp.getLatitude(), sp.getLongitude());
            }
        }

        // 4. Try Geocoder fallback
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocationName(locationName + ", Pune", 1);
            if (addresses != null && !addresses.isEmpty()) {
                return new LatLng(addresses.get(0).getLatitude(), addresses.get(0).getLongitude());
            }
        } catch (Exception ignored) {
        }

        return new LatLng(defaultLat, defaultLng);
    }

    private List<LatLng> buildRoutePoints(String routineTitle, LatLng start, LatLng dest) {
        List<LatLng> points = new ArrayList<>();
        points.add(start);

        String titleLower = routineTitle != null ? routineTitle.toLowerCase(Locale.ROOT) : "";
        if (titleLower.contains("campus") || (start.latitude > 18.465 && dest.latitude < 18.465)) {
            // Campus Gate to Home corridor
            points.add(new LatLng(18.46640, 73.86210)); // Dhankawadi Main Rd
            points.add(new LatLng(18.46490, 73.86320)); // Chavan Nagar Junction
            points.add(new LatLng(18.46350, 73.86410)); // Bibwewadi Connect
        } else if (titleLower.contains("jog") || titleLower.contains("lake") || (start.latitude > 18.460 && dest.latitude < 18.460)) {
            // Home to Katraj Lake corridor
            points.add(new LatLng(18.46010, 73.86550)); // Bibwewadi Link
            points.add(new LatLng(18.45780, 73.86650)); // Katraj Lake North Entrance
        } else {
            double midLat = (start.latitude + dest.latitude) / 2.0;
            double midLng = (start.longitude + dest.longitude) / 2.0;
            points.add(new LatLng(midLat, midLng));
        }

        points.add(dest);
        return points;
    }

    private void fitRouteOnMap() {
        if (mMap == null) return;
        if (calculatedRouteBounds != null) {
            try {
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(calculatedRouteBounds, 140));
                return;
            } catch (Exception ignored) {
            }
        }
        if (userLocation != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15));
        } else {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(USER_LAT, USER_LON), 14));
        }
    }

    private void checkUserInDangerZone() {
        LatLng checkPoint = userLocation;
        if (checkPoint == null && calculatedRouteBounds != null) {
            checkPoint = calculatedRouteBounds.getCenter();
        }
        if (checkPoint == null) {
            checkPoint = new LatLng(USER_LAT, USER_LON);
        }

        boolean inDanger = false;
        String warningMsg = "";
        for (LatLng zone : dangerZones) {
            float[] results = new float[1];
            Location.distanceBetween(checkPoint.latitude, checkPoint.longitude, zone.latitude, zone.longitude, results);
            if (results[0] < 120) {
                inDanger = true;
                warningMsg = "⚠️ Warning: Proximity to Danger Zone (" + (int) results[0] + "m away)! Take caution or reroute.";
                break;
            }
        }

        if (inDanger) {
            Toast.makeText(this, warningMsg, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "✅ Safety Scan: Commute corridor is clear. No danger zones within immediate proximity.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCheckInForRoutine() {
        Intent checkInIntent = new Intent(this, SmartCheckInActivity.class);
        if (activeRoutine != null) {
            checkInIntent.putExtra(SmartCheckInActivity.EXTRA_ROUTINE_ID, activeRoutine.getId());
            checkInIntent.putExtra(SmartCheckInActivity.EXTRA_ROUTINE_TITLE, activeRoutine.getTitle());
            checkInIntent.putExtra(SmartCheckInActivity.EXTRA_REASON, "Commute Journey: " + activeRoutine.getTitle());
            int duration = activeRoutine.getCheckInIntervalMinutes() > 0 ? activeRoutine.getCheckInIntervalMinutes() : 15;
            checkInIntent.putExtra(SmartCheckInActivity.EXTRA_DURATION_MINUTES, duration);
        } else {
            checkInIntent.putExtra(SmartCheckInActivity.EXTRA_REASON, "Commute Safety Checkpoint");
            checkInIntent.putExtra(SmartCheckInActivity.EXTRA_DURATION_MINUTES, 20);
        }
        startActivity(checkInIntent);
    }

    private void addDangerZone(LatLng latLng) {
        String key = dangerZoneRef.push().getKey();
        if (key == null) return;
        dangerZoneRef.child(key).setValue(new Dataholder(latLng.latitude, latLng.longitude))
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Danger Zone Added", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to add", Toast.LENGTH_SHORT).show());
    }

    public static class Dataholder {
        public double latitude, longitude;

        public Dataholder() {}
        public Dataholder(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}

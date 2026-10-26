package com.example.woshield;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Nearby_Safe extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "Nearby_Safe";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // Fallback baseline coordinates (Pune anchor) if GPS is cold or unavailable
    private static final double DEFAULT_LAT = 18.46225;
    private static final double DEFAULT_LON = 73.864972;

    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng currentUserLocation;

    private RecyclerView rvNearbyPlaces;
    private NearbySafePlaceAdapter adapter;
    private final List<NearbySafePlace> safePlacesList = new ArrayList<>();
    private final Map<String, Marker> placeMarkerMap = new HashMap<>();

    private ProgressBar progressBar;
    private TextView tvHeaderSubtitle;
    private TextView tvPlaceCountBadge;
    private BottomSheetBehavior<MaterialCardView> bottomSheetBehavior;

    // Offline Semantic Search Views
    private EditText etSemanticSearch;
    private ImageView btnTriggerSearch;
    private ImageView btnClearSearch;
    private TextView chipAll, chipHospital, chipPolice, chipPharmacy, chipTransit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_safe);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        tvHeaderSubtitle = findViewById(R.id.tv_header_subtitle);
        tvPlaceCountBadge = findViewById(R.id.tv_place_count_badge);
        progressBar = findViewById(R.id.progress_loading_places);

        rvNearbyPlaces = findViewById(R.id.rv_nearby_places);
        rvNearbyPlaces.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NearbySafePlaceAdapter(safePlacesList, new NearbySafePlaceAdapter.OnSafePlaceClickListener() {
            @Override
            public void onPlaceClicked(NearbySafePlace place) {
                focusPlaceOnMap(place);
                showNavigateOptionDialog(place);
            }

            @Override
            public void onNavigateClicked(NearbySafePlace place) {
                focusPlaceOnMap(place);
                launchGoogleMapsNavigation(place);
            }

            @Override
            public void onCallClicked(NearbySafePlace place) {
                callHelpline(place.getPhone());
            }
        });
        rvNearbyPlaces.setAdapter(adapter);

        MaterialCardView bottomSheet = findViewById(R.id.bottom_sheet_places);
        if (bottomSheet != null) {
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }

        findViewById(R.id.btn_locate_me).setOnClickListener(v -> {
            if (currentUserLocation != null && googleMap != null) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentUserLocation, 15f));
                Toast.makeText(this, "Centered on your location", Toast.LENGTH_SHORT).show();
            } else {
                requestUserLocation();
            }
        });

        // Initialize Offline Semantic Search
        etSemanticSearch = findViewById(R.id.et_semantic_search);
        btnTriggerSearch = findViewById(R.id.btn_trigger_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);

        chipAll = findViewById(R.id.chip_all);
        chipHospital = findViewById(R.id.chip_hospital);
        chipPolice = findViewById(R.id.chip_police);
        chipPharmacy = findViewById(R.id.chip_pharmacy);
        chipTransit = findViewById(R.id.chip_transit);

        if (btnTriggerSearch != null) {
            btnTriggerSearch.setOnClickListener(v -> {
                String q = etSemanticSearch != null ? etSemanticSearch.getText().toString().trim() : "";
                performOfflineSemanticSearch(q);
            });
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                if (etSemanticSearch != null) {
                    etSemanticSearch.setText("");
                }
                btnClearSearch.setVisibility(View.GONE);
                seedSafePlacesAroundLocation(currentUserLocation != null ? currentUserLocation : new LatLng(DEFAULT_LAT, DEFAULT_LON));
            });
        }

        if (etSemanticSearch != null) {
            etSemanticSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    performOfflineSemanticSearch(etSemanticSearch.getText().toString().trim());
                    return true;
                }
                return false;
            });

            etSemanticSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (btnClearSearch != null) {
                        btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // Chip Clicks
        if (chipAll != null) chipAll.setOnClickListener(v -> {
            if (etSemanticSearch != null) etSemanticSearch.setText("");
            performOfflineSemanticSearch("");
        });
        if (chipHospital != null) chipHospital.setOnClickListener(v -> {
            if (etSemanticSearch != null) etSemanticSearch.setText("hospital");
            performOfflineSemanticSearch("hospital");
        });
        if (chipPolice != null) chipPolice.setOnClickListener(v -> {
            if (etSemanticSearch != null) etSemanticSearch.setText("police station");
            performOfflineSemanticSearch("police station");
        });
        if (chipPharmacy != null) chipPharmacy.setOnClickListener(v -> {
            if (etSemanticSearch != null) etSemanticSearch.setText("pharmacy");
            performOfflineSemanticSearch("pharmacy");
        });
        if (chipTransit != null) chipTransit.setOnClickListener(v -> {
            if (etSemanticSearch != null) etSemanticSearch.setText("transit metro bus");
            performOfflineSemanticSearch("transit metro bus");
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        googleMap.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof NearbySafePlace) {
                NearbySafePlace place = (NearbySafePlace) tag;
                marker.showInfoWindow();
                showNavigateOptionDialog(place);
                // Also highlight in adapter
                int index = safePlacesList.indexOf(place);
                if (index != -1) {
                    adapter.setSelectedPosition(index);
                    rvNearbyPlaces.smoothScrollToPosition(index);
                }
                return true;
            }
            return false;
        });

        checkLocationPermissionAndFetch();
    }

    private void checkLocationPermissionAndFetch() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
            // In the meantime, seed around default location so user does not see a blank map
            seedSafePlacesAroundLocation(new LatLng(DEFAULT_LAT, DEFAULT_LON));
            return;
        }

        enableMapLocationLayer();
        requestUserLocation();
    }

    private void enableMapLocationLayer() {
        if (googleMap != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                googleMap.setMyLocationEnabled(true);
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException enabling MyLocation: " + e.getMessage());
            }
        }
    }

    private void requestUserLocation() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            seedSafePlacesAroundLocation(new LatLng(DEFAULT_LAT, DEFAULT_LON));
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                onLocationResolved(new LatLng(location.getLatitude(), location.getLongitude()));
            } else {
                // Request single fresh high accuracy update
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener(this, freshLocation -> {
                            if (freshLocation != null) {
                                onLocationResolved(new LatLng(freshLocation.getLatitude(), freshLocation.getLongitude()));
                            } else {
                                onLocationResolved(new LatLng(DEFAULT_LAT, DEFAULT_LON));
                            }
                        })
                        .addOnFailureListener(this, e -> {
                            Log.w(TAG, "Failed to get fresh location, using default", e);
                            onLocationResolved(new LatLng(DEFAULT_LAT, DEFAULT_LON));
                        });
            }
        }).addOnFailureListener(this, e -> {
            Log.w(TAG, "Failed to get last location, using default", e);
            onLocationResolved(new LatLng(DEFAULT_LAT, DEFAULT_LON));
        });
    }

    private void onLocationResolved(LatLng location) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        currentUserLocation = location;
        seedSafePlacesAroundLocation(currentUserLocation);
    }

    /**
     * Executes the 4-stage Regex + Cosine Similarity + Distance offline search pipeline.
     */
    private void performOfflineSemanticSearch(String query) {
        if (currentUserLocation == null) {
            currentUserLocation = new LatLng(DEFAULT_LAT, DEFAULT_LON);
        }

        if (TextUtils.isEmpty(query)) {
            seedSafePlacesAroundLocation(currentUserLocation);
            return;
        }

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        double uLat = currentUserLocation.latitude;
        double uLng = currentUserLocation.longitude;

        // Run Offline Semantic Search Engine (Regex + Cosine Sim + Structured Filter + Distance)
        List<OfflineSemanticSearchEngine.SearchResult> searchResults =
                OfflineSemanticSearchEngine.executeSearch(this, query, uLat, uLng, 10);

        safePlacesList.clear();

        for (OfflineSemanticSearchEngine.SearchResult sr : searchResults) {
            NightOwlRecord r = sr.record;
            if (r == null) continue;

            String catTag = r.category != null ? r.category.toUpperCase(Locale.ROOT) : "SAFE HAVEN";
            String phone = "112";
            if ("HOSPITAL".equalsIgnoreCase(catTag)) phone = "108";
            else if ("PUBLIC_FACILITY".equalsIgnoreCase(catTag) || (r.serviceSubtype != null && r.serviceSubtype.contains("police"))) {
                catTag = "POLICE";
                phone = "100";
            } else if ("TRANSPORT".equalsIgnoreCase(catTag)) {
                catTag = "TRANSIT";
                phone = "182";
            } else if ("PHARMACY".equalsIgnoreCase(catTag)) {
                phone = "104";
            }

            String notes = (r.hoursNote != null ? r.hoursNote : "") +
                    " • [" + (r.evidenceStatus != null ? r.evidenceStatus.toUpperCase() : "KNOWN") + "]" +
                    " • Sim: " + Math.round(sr.cosineSimilarity * 100) + "%";

            safePlacesList.add(createSafePlace(
                    r.recordId,
                    r.name,
                    catTag,
                    r.areaOrLocation + ", " + r.city,
                    r.latitude,
                    r.longitude,
                    uLat,
                    uLng,
                    phone,
                    r.evidenceStatus != null ? r.evidenceStatus.toUpperCase() : "KNOWN",
                    notes
            ));
        }

        if (progressBar != null) progressBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();

        if (tvHeaderSubtitle != null) {
            tvHeaderSubtitle.setText("Found " + safePlacesList.size() + " matches for \"" + query + "\" (Offline AI)");
        }
        if (tvPlaceCountBadge != null) {
            tvPlaceCountBadge.setText(safePlacesList.size() + " Matches");
        }

        plotPlacesOnMap();

        if (safePlacesList.isEmpty()) {
            Toast.makeText(this, "No records matched query: " + query, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Dynamically generates safe places near user from the 100 verified CSV seed records.
     */
    private void seedSafePlacesAroundLocation(LatLng userLoc) {
        if (userLoc == null) return;
        currentUserLocation = userLoc;

        safePlacesList.clear();
        double uLat = userLoc.latitude;
        double uLng = userLoc.longitude;

        // Query real records from NightOwlCsvRepository
        NightOwlCsvRepository repo = NightOwlCsvRepository.getInstance(this);
        List<NightOwlRecord> waypoints = repo.findBestSafeWaypoints(uLat, uLng);

        if (waypoints != null && !waypoints.isEmpty()) {
            for (NightOwlRecord r : waypoints) {
                String catTag = r.category != null ? r.category.toUpperCase(Locale.ROOT) : "SAFE HAVEN";
                String phone = "112";
                if ("HOSPITAL".equalsIgnoreCase(catTag)) phone = "108";
                else if ("PUBLIC_FACILITY".equalsIgnoreCase(catTag) || (r.serviceSubtype != null && r.serviceSubtype.contains("police"))) {
                    catTag = "POLICE";
                    phone = "100";
                } else if ("TRANSPORT".equalsIgnoreCase(catTag)) {
                    catTag = "TRANSIT";
                    phone = "182";
                } else if ("PHARMACY".equalsIgnoreCase(catTag)) {
                    phone = "104";
                }

                String notes = (r.hoursNote != null ? r.hoursNote : "") +
                        " • [" + (r.evidenceStatus != null ? r.evidenceStatus.toUpperCase() : "KNOWN") + "]";

                safePlacesList.add(createSafePlace(
                        r.recordId,
                        r.name,
                        catTag,
                        r.areaOrLocation + ", " + r.city,
                        r.latitude,
                        r.longitude,
                        uLat,
                        uLng,
                        phone,
                        r.evidenceStatus != null ? r.evidenceStatus.toUpperCase() : "KNOWN",
                        notes
                ));
            }
        } else {
            // Fallback seed
            safePlacesList.add(createSafePlace("PUN045", "Pune Police Commissionerate", "POLICE", "Camp area, Pune", uLat + 0.0024, uLng + 0.0019, uLat, uLng, "100", "KNOWN", "24/7 Police Headquarters"));
            safePlacesList.add(createSafePlace("PUN020", "Ruby Hall Clinic 24/7", "HOSPITAL", "Sassoon Road, Pune", uLat - 0.0036, uLng + 0.0028, uLat, uLng, "108", "PROBABLE", "Emergency & Trauma Admission"));
            safePlacesList.add(createSafePlace("PUN009", "Swargate Transit Hub", "TRANSIT", "Swargate, Pune", uLat + 0.0051, uLng - 0.0038, uLat, uLng, "182", "KNOWN", "All-night bus & metro terminus"));
            safePlacesList.add(createSafePlace("PUN026", "KEM Hospital 24H Pharmacy", "SAFE HAVEN", "Rasta Peth, Pune", uLat - 0.0065, uLng - 0.0052, uLat, uLng, "020-24201122", "PROBABLE", "24/7 Open Medical Chemist"));
            safePlacesList.add(createSafePlace("PUN023", "Noble Hospital Hadapsar", "HOSPITAL", "Hadapsar, Pune", 18.5018, 73.926, uLat, uLng, "108", "PROBABLE", "24/7 Emergency Hospital in Hadapsar"));
        }

        // Sort by real-time distance (nearest safe place first)
        Collections.sort(safePlacesList, (a, b) -> Float.compare(a.getDistanceMeters(), b.getDistanceMeters()));

        adapter.notifyDataSetChanged();

        if (tvHeaderSubtitle != null) {
            tvHeaderSubtitle.setText(safePlacesList.size() + " safe havens near your location");
        }
        if (tvPlaceCountBadge != null) {
            tvPlaceCountBadge.setText(safePlacesList.size() + " Safe Places");
        }

        plotPlacesOnMap();
    }

    private NearbySafePlace createSafePlace(String id, String name, String category, String address,
                                            double lat, double lng, double userLat, double userLng,
                                            String phone, String status, String notes) {
        float[] distResults = new float[2];
        Location.distanceBetween(userLat, userLng, lat, lng, distResults);
        float meters = distResults[0];
        float bearing = distResults.length > 1 ? distResults[1] : 0f;
        float normalizedBearing = (bearing % 360 + 360) % 360;

        String directionHeading;
        if (normalizedBearing >= 337.5 || normalizedBearing < 22.5) {
            directionHeading = "North ⬆";
        } else if (normalizedBearing < 67.5) {
            directionHeading = "North-East ↗";
        } else if (normalizedBearing < 112.5) {
            directionHeading = "East ➔";
        } else if (normalizedBearing < 157.5) {
            directionHeading = "South-East ↘";
        } else if (normalizedBearing < 202.5) {
            directionHeading = "South ⬇";
        } else if (normalizedBearing < 247.5) {
            directionHeading = "South-West ↙";
        } else if (normalizedBearing < 292.5) {
            directionHeading = "West ⬅";
        } else {
            directionHeading = "North-West ↖";
        }

        int walkMins = (int) Math.max(1, Math.round(meters / 75.0));
        String formattedDist;
        if (meters < 1000) {
            formattedDist = Math.round(meters) + " m (" + walkMins + " min walk)";
        } else {
            formattedDist = String.format(Locale.ENGLISH, "%.1f km (%d min walk)", meters / 1000f, walkMins);
        }

        NearbySafePlace place = new NearbySafePlace(id, name, category, address, lat, lng, meters, formattedDist, phone, status, notes);
        place.setDirectionHeading(directionHeading);
        place.setBearingDegrees(normalizedBearing);
        place.setDirectionDetails("🧭 " + directionHeading + " • " + formattedDist);
        return place;
    }

    private void plotPlacesOnMap() {
        if (googleMap == null || currentUserLocation == null) return;

        googleMap.clear();
        placeMarkerMap.clear();

        // Marker for user's current location
        googleMap.addMarker(new MarkerOptions()
                .position(currentUserLocation)
                .title("Your Current Location")
                .snippet("You are here")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boundsBuilder.include(currentUserLocation);

        for (NearbySafePlace place : safePlacesList) {
            LatLng pos = new LatLng(place.getLatitude(), place.getLongitude());
            boundsBuilder.include(pos);

            float markerColor = BitmapDescriptorFactory.HUE_VIOLET;
            if ("POLICE".equalsIgnoreCase(place.getCategory())) {
                markerColor = BitmapDescriptorFactory.HUE_BLUE;
            } else if ("HOSPITAL".equalsIgnoreCase(place.getCategory())) {
                markerColor = BitmapDescriptorFactory.HUE_RED;
            } else if ("TRANSIT".equalsIgnoreCase(place.getCategory())) {
                markerColor = BitmapDescriptorFactory.HUE_CYAN;
            } else if ("SAFE HAVEN".equalsIgnoreCase(place.getCategory())) {
                markerColor = BitmapDescriptorFactory.HUE_GREEN;
            } else if ("COMMUNITY HELP".equalsIgnoreCase(place.getCategory())) {
                markerColor = BitmapDescriptorFactory.HUE_MAGENTA;
            }

            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(place.getName())
                    .snippet(place.getDistanceFormatted() + " • " + place.getAddress())
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));

            if (marker != null) {
                marker.setTag(place);
                placeMarkerMap.put(place.getId(), marker);
            }
        }

        try {
            LatLngBounds bounds = boundsBuilder.build();
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 180));
        } catch (Exception e) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentUserLocation, 14.5f));
        }
    }

    private void focusPlaceOnMap(NearbySafePlace place) {
        if (googleMap == null || place == null) return;
        LatLng pos = new LatLng(place.getLatitude(), place.getLongitude());
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));

        Marker marker = placeMarkerMap.get(place.getId());
        if (marker != null) {
            marker.showInfoWindow();
        }
    }

    /**
     * Shows the Navigate option dialog when a safe place is clicked.
     */
    private void showNavigateOptionDialog(NearbySafePlace place) {
        if (place == null || isFinishing() || isDestroyed()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_safe_place_navigate, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvCategory = dialogView.findViewById(R.id.dialog_tv_category);
        TextView tvDistance = dialogView.findViewById(R.id.dialog_tv_distance);
        TextView tvName = dialogView.findViewById(R.id.dialog_tv_name);
        TextView tvAddress = dialogView.findViewById(R.id.dialog_tv_address);
        TextView tvStatus = dialogView.findViewById(R.id.dialog_tv_status);
        MaterialButton btnNavigate = dialogView.findViewById(R.id.dialog_btn_navigate);
        MaterialButton btnCall = dialogView.findViewById(R.id.dialog_btn_call);
        MaterialButton btnClose = dialogView.findViewById(R.id.dialog_btn_close);

        tvCategory.setText(place.getCategory() != null ? place.getCategory().toUpperCase() : "SAFE HAVEN");
        tvDistance.setText(place.getDistanceFormatted());
        tvName.setText(place.getName());
        tvAddress.setText(place.getAddress());
        tvStatus.setText(place.getStatus());

        // Navigate button action -> Redirects to Google Maps with direction route
        btnNavigate.setOnClickListener(v -> {
            dialog.dismiss();
            launchGoogleMapsNavigation(place);
        });

        // Call helpline action
        if (place.getPhone() != null && !place.getPhone().isEmpty()) {
            btnCall.setVisibility(View.VISIBLE);
            btnCall.setText("Call (" + place.getPhone() + ")");
            btnCall.setOnClickListener(v -> {
                dialog.dismiss();
                callHelpline(place.getPhone());
            });
        } else {
            btnCall.setVisibility(View.GONE);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Redirects to Google Maps app or browser showing the direction route
     * from user's current location to the safe place destination.
     */
    private void launchGoogleMapsNavigation(NearbySafePlace place) {
        if (place == null) return;

        Toast.makeText(this, "Opening direction route to " + place.getName(), Toast.LENGTH_SHORT).show();

        // Standard Google Maps Direction URL with origin (current location) and destination
        String uriString;
        if (currentUserLocation != null) {
            uriString = String.format(Locale.ENGLISH,
                    "https://www.google.com/maps/dir/?api=1&origin=%f,%f&destination=%f,%f&travelmode=walking",
                    currentUserLocation.latitude, currentUserLocation.longitude,
                    place.getLatitude(), place.getLongitude());
        } else {
            uriString = String.format(Locale.ENGLISH,
                    "https://www.google.com/maps/dir/?api=1&destination=%f,%f&travelmode=walking",
                    place.getLatitude(), place.getLongitude());
        }

        Uri gmmIntentUri = Uri.parse(uriString);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        try {
            startActivity(mapIntent);
        } catch (ActivityNotFoundException e) {
            // Fallback: If Google Maps app is not installed, open through browser or any map handler
            Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            try {
                startActivity(fallbackIntent);
            } catch (Exception ex) {
                Toast.makeText(this, "Could not open map: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void callHelpline(String phone) {
        if (phone == null || phone.isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phone.trim()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not initiate call: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMapLocationLayer();
                requestUserLocation();
            } else {
                Toast.makeText(this, "Location permission denied. Showing places near default area.", Toast.LENGTH_LONG).show();
                seedSafePlacesAroundLocation(new LatLng(DEFAULT_LAT, DEFAULT_LON));
            }
        }
    }
}

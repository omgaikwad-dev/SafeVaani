package com.example.woshield;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class SafeStopsActivity extends AppCompatActivity implements SafePlaceAdapter.OnSafePlaceActionListener {

    private RecyclerView rvSafeStops;
    private SafePlaceAdapter adapter;
    private final List<SafePlace> placeList = new ArrayList<>();
    private SafePlaceRepository repository;
    private ValueEventListener placesListener;

    private ProgressBar progressBar;
    private MaterialCardView cardEmpty;
    private TextView tvPlacesSummary;

    private static final String[] CATEGORIES = {"Home", "College", "Office", "Transit", "Medical", "Family", "Custom"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_stops);

        if (FirebaseAuth.getInstance().getCurrentUser() == null && !DemoDataSeeder.isDemoMode(this)) {
            finish();
            return;
        }

        repository = new SafePlaceRepository();

        Toolbar toolbar = findViewById(R.id.toolbar_safe_stops);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvSafeStops = findViewById(R.id.rv_safe_stops);
        progressBar = findViewById(R.id.progress_safe_stops);
        cardEmpty = findViewById(R.id.card_empty_safe_stops);
        tvPlacesSummary = findViewById(R.id.tv_places_summary);

        rvSafeStops.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SafePlaceAdapter(placeList, this);
        rvSafeStops.setAdapter(adapter);

        findViewById(R.id.btn_add_safe_place).setOnClickListener(v -> showSafePlaceDialog(null));
        findViewById(R.id.btn_empty_add_place).setOnClickListener(v -> showSafePlaceDialog(null));
        View btnExploreNearby = findViewById(R.id.btn_empty_find_safe_places);
        if (btnExploreNearby != null) {
            btnExploreNearby.setOnClickListener(v -> startActivity(new Intent(this, Nearby_Safe.class)));
        }
        findViewById(R.id.btn_view_map_safe_stops).setOnClickListener(v -> startActivity(new Intent(this, MapsActivity.class)));

        loadSafePlaces();
    }

    private void loadSafePlaces() {
        progressBar.setVisibility(View.VISIBLE);
        placesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;
                progressBar.setVisibility(View.GONE);
                placeList.clear();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    SafePlace place = ds.getValue(SafePlace.class);
                    if (place != null) {
                        if (place.getId() == null) place.setId(ds.getKey());
                        placeList.add(place);
                    }
                }

                adapter.notifyDataSetChanged();
                updateUIState();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFinishing() || isDestroyed()) return;
                progressBar.setVisibility(View.GONE);
                Toast.makeText(SafeStopsActivity.this, "Failed to load safe places: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                updateUIState();
            }
        };

        repository.listenToSafePlaces(placesListener);
    }

    private void updateUIState() {
        if (placeList.isEmpty()) {
            cardEmpty.setVisibility(View.VISIBLE);
            rvSafeStops.setVisibility(View.GONE);
            tvPlacesSummary.setText("0 Safe Places Configured");
        } else {
            cardEmpty.setVisibility(View.GONE);
            rvSafeStops.setVisibility(View.VISIBLE);
            tvPlacesSummary.setText(placeList.size() + (placeList.size() == 1 ? " Safe Place Configured" : " Safe Places Configured"));
        }
    }

    private void showSafePlaceDialog(SafePlace existingPlace) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_safe_place_form, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        EditText etName = dialogView.findViewById(R.id.et_dialog_place_name);
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinner_place_category);
        EditText etAddress = dialogView.findViewById(R.id.et_dialog_place_address);
        EditText etLat = dialogView.findViewById(R.id.et_dialog_place_lat);
        EditText etLon = dialogView.findViewById(R.id.et_dialog_place_lon);
        EditText etNotes = dialogView.findViewById(R.id.et_dialog_place_notes);
        SwitchMaterial switchTrusted = dialogView.findViewById(R.id.switch_dialog_trusted);

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CATEGORIES);
        spinnerCategory.setAdapter(catAdapter);

        if (existingPlace != null) {
            tvTitle.setText("Edit Safe Place");
            etName.setText(existingPlace.getName());
            etAddress.setText(existingPlace.getAddress());
            if (existingPlace.isHasCoordinates()) {
                etLat.setText(String.valueOf(existingPlace.getLatitude()));
                etLon.setText(String.valueOf(existingPlace.getLongitude()));
            }
            etNotes.setText(existingPlace.getNotes());
            switchTrusted.setChecked(existingPlace.isTrusted());

            for (int i = 0; i < CATEGORIES.length; i++) {
                if (CATEGORIES[i].equalsIgnoreCase(existingPlace.getCategory())) {
                    spinnerCategory.setSelection(i);
                    break;
                }
            }
        } else {
            tvTitle.setText("Add Safe Place");
        }

        dialogView.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btn_dialog_save).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                etName.setError("Place name is required");
                return;
            }

            String category = spinnerCategory.getSelectedItem().toString();
            String address = etAddress.getText().toString().trim();
            String latStr = etLat.getText().toString().trim();
            String lonStr = etLon.getText().toString().trim();
            String notes = etNotes.getText().toString().trim();
            boolean isTrusted = switchTrusted.isChecked();

            double lat = 0.0;
            double lon = 0.0;
            boolean hasCoords = false;

            if (!TextUtils.isEmpty(latStr) && !TextUtils.isEmpty(lonStr)) {
                try {
                    lat = Double.parseDouble(latStr);
                    lon = Double.parseDouble(lonStr);
                    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                        Toast.makeText(this, "Coordinates out of valid range (-90 to 90, -180 to 180)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    hasCoords = true;
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid coordinate numbers", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            SafePlace placeToSave = existingPlace != null ? existingPlace : new SafePlace();
            placeToSave.setName(name);
            placeToSave.setCategory(category);
            placeToSave.setAddress(address);
            placeToSave.setLatitude(lat);
            placeToSave.setLongitude(lon);
            placeToSave.setHasCoordinates(hasCoords);
            placeToSave.setNotes(notes);
            placeToSave.setTrusted(isTrusted);

            repository.saveSafePlace(placeToSave, (error, ref) -> {
                if (error == null) {
                    Toast.makeText(SafeStopsActivity.this, "Safe place saved successfully", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } else {
                    Toast.makeText(SafeStopsActivity.this, "Failed to save: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    @Override
    public void onToggleTrusted(SafePlace place) {
        boolean newTrusted = !place.isTrusted();
        repository.toggleTrusted(place.getId(), newTrusted, (error, ref) -> {
            if (error != null) {
                Toast.makeText(this, "Error updating status: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onEdit(SafePlace place) {
        showSafePlaceDialog(place);
    }

    @Override
    public void onDelete(SafePlace place) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Safe Place")
                .setMessage("Are you sure you want to remove '" + place.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.deleteSafePlace(place.getId(), (error, ref) -> {
                        if (error == null) {
                            Toast.makeText(SafeStopsActivity.this, "Safe place removed", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(SafeStopsActivity.this, "Failed to delete: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onSelect(SafePlace place) {
        // Selection handled in Routine form if launched for result
    }

    @Override
    public void onNavigate(SafePlace place) {
        if (place == null) return;

        new AlertDialog.Builder(this)
                .setTitle("📍 " + place.getName())
                .setMessage("Category: " + (place.getCategory() != null ? place.getCategory() : "Safe Haven") +
                        (place.getAddress() != null && !place.getAddress().isEmpty() ? "\nLocation: " + place.getAddress() : "") +
                        (place.getNotes() != null && !place.getNotes().isEmpty() ? "\nNotes: " + place.getNotes() : "") +
                        "\n\nWould you like to navigate to this safe place using Google Maps directions?")
                .setPositiveButton("Navigate (Google Maps)", (dialog, which) -> launchGoogleMapsRoute(place))
                .setNeutralButton("Edit Details", (dialog, which) -> onEdit(place))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchGoogleMapsRoute(SafePlace place) {
        String destinationQuery;
        if (place.isHasCoordinates() && place.getLatitude() != 0.0 && place.getLongitude() != 0.0) {
            destinationQuery = place.getLatitude() + "," + place.getLongitude();
        } else if (place.getAddress() != null && !place.getAddress().trim().isEmpty()) {
            destinationQuery = Uri.encode(place.getAddress().trim());
        } else {
            destinationQuery = Uri.encode(place.getName());
        }

        Toast.makeText(this, "Opening direction route to " + place.getName(), Toast.LENGTH_SHORT).show();

        String uriString = "https://www.google.com/maps/dir/?api=1&destination=" + destinationQuery + "&travelmode=walking";
        Uri gmmIntentUri = Uri.parse(uriString);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        try {
            startActivity(mapIntent);
        } catch (ActivityNotFoundException e) {
            Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            try {
                startActivity(fallbackIntent);
            } catch (Exception ex) {
                Toast.makeText(this, "Could not open map: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (repository != null && placesListener != null) {
            repository.removeSafePlacesListener(placesListener);
        }
    }
}

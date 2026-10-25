package com.example.woshield;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class BookActivity extends AppCompatActivity implements OnMapReadyCallback {
    EditText etEmail, etDescription, etFeedback;
    Button btnSubmit;
    TextView tvSelectedLocation;

    private GoogleMap mMap;
    private Marker selectedMarker;
    private double latitude = 0.0, longitude = 0.0;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.feedback2);

        etEmail = findViewById(R.id.et3);
        etDescription = findViewById(R.id.et4);
        etFeedback = findViewById(R.id.et6);
        tvSelectedLocation = findViewById(R.id.tvSelectedLocation);
        btnSubmit = findViewById(R.id.button2);

        // Load Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        btnSubmit.setOnClickListener(v -> book());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Default location (set to your region)
        LatLng defaultLocation = new LatLng(20.5937, 78.9629); // India
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 5));

        // Set click listener to place marker
        mMap.setOnMapClickListener(latLng -> {
            latitude = latLng.latitude;
            longitude = latLng.longitude;

            // Remove previous marker
            if (selectedMarker != null) {
                selectedMarker.remove();
            }

            // Add new marker
            selectedMarker = mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));

            // Update text
            tvSelectedLocation.setText("Latitude: " + latitude + ", Longitude: " + longitude);
        });
    }

    public void book() {
        String email = etEmail.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String feedback = etFeedback.getText().toString().trim();

        if (email.isEmpty() || description.isEmpty() || feedback.isEmpty() || latitude == 0.0 || longitude == 0.0) {
            Toast.makeText(this, "Please fill all fields and select a location", Toast.LENGTH_SHORT).show();
            return;
        }

        // Store data in Firebase
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        DatabaseReference node = db.getReference("DangerZones");

        Dataholder dangerZone = new Dataholder(email, description, feedback, latitude, longitude);
        node.push().setValue(dangerZone)
                .addOnSuccessListener(aVoid -> {
                    etEmail.setText(""); etDescription.setText(""); etFeedback.setText("");
                    tvSelectedLocation.setText("Latitude: , Longitude: ");
                    latitude = 0.0; longitude = 0.0;
                    Toast.makeText(this, "Feedback Successfully Submitted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to submit: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}

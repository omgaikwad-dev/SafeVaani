package com.example.woshield;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NearbySosAlertActivity extends AppCompatActivity {

    private OfflineMeshSosManager.NearbySosAlert alert;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn on screen and show when locked for critical safety alert
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        setContentView(R.layout.activity_nearby_sos_alert);

        alert = (OfflineMeshSosManager.NearbySosAlert) getIntent().getSerializableExtra("alert_data");

        bindViews();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        OfflineMeshSosManager.NearbySosAlert newAlert = (OfflineMeshSosManager.NearbySosAlert) intent.getSerializableExtra("alert_data");
        if (newAlert != null) {
            this.alert = newAlert;
            bindViews();
        }
    }

    private void bindViews() {
        TextView tvVictimName = findViewById(R.id.tvVictimName);
        TextView tvVictimPhone = findViewById(R.id.tvVictimPhone);
        TextView tvVictimLocation = findViewById(R.id.tvVictimLocation);
        TextView tvAlertTime = findViewById(R.id.tvAlertTime);
        TextView tvAlertTransport = findViewById(R.id.tvAlertTransport);

        MaterialButton btnOpenGoogleMaps = findViewById(R.id.btnOpenGoogleMaps);
        MaterialButton btnCallVictim = findViewById(R.id.btnCallVictim);
        MaterialButton btnCallPolice = findViewById(R.id.btnCallPolice);
        MaterialButton btnDismissAlert = findViewById(R.id.btnDismissAlert);

        if (alert != null) {
            if (tvVictimName != null) tvVictimName.setText(alert.victimName != null ? alert.victimName : "Nearby Citizen");
            if (tvVictimPhone != null) tvVictimPhone.setText(alert.victimPhone != null ? alert.victimPhone : "Available via Mesh");
            if (tvAlertTransport != null) tvAlertTransport.setText("📡 " + (alert.transport != null ? alert.transport.toUpperCase(Locale.US) : "OFFLINE MESH"));

            String locText = (alert.locationName != null && !alert.locationName.isEmpty())
                    ? alert.locationName
                    : String.format(Locale.US, "GPS: %.4f°N, %.4f°E", alert.latitude, alert.longitude);
            if (tvVictimLocation != null) tvVictimLocation.setText(locText);

            if (tvAlertTime != null && alert.timestamp > 0) {
                String timeStr = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date(alert.timestamp));
                tvAlertTime.setText("Received at " + timeStr);
            }
        }

        if (btnOpenGoogleMaps != null) {
            btnOpenGoogleMaps.setOnClickListener(v -> {
                OfflineMeshSosManager.stopAlarmSound();
                if (alert != null && alert.latitude != 0.0 && alert.longitude != 0.0) {
                    try {
                        String mapsUrl = String.format(Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%f,%f",
                                alert.latitude, alert.longitude);
                        Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl));
                        startActivity(mapIntent);
                    } catch (Exception e) {
                        Toast.makeText(this, "Opening map...", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Coordinates not available", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCallVictim != null) {
            btnCallVictim.setOnClickListener(v -> {
                OfflineMeshSosManager.stopAlarmSound();
                if (alert != null && alert.victimPhone != null && !alert.victimPhone.isEmpty()) {
                    try {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + alert.victimPhone.trim()));
                        startActivity(callIntent);
                    } catch (Exception e) {
                        Toast.makeText(this, "Cannot launch dialer", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Victim phone not provided", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCallPolice != null) {
            btnCallPolice.setOnClickListener(v -> {
                OfflineMeshSosManager.stopAlarmSound();
                try {
                    Intent policeIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"));
                    startActivity(policeIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "Calling 112...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnDismissAlert != null) {
            btnDismissAlert.setOnClickListener(v -> {
                OfflineMeshSosManager.stopAlarmSound();
                finish();
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        OfflineMeshSosManager.stopAlarmSound();
    }
}

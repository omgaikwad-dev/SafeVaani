package com.example.woshield;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class AdminDashboard extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        CardView cardDangerZones = findViewById(R.id.cardViewReports);
        CardView cardEmergencyLogs = findViewById(R.id.cardViewEmergencyLogs);
        CardView cardAnalytics = findViewById(R.id.cardViewAnalytics);
        CardView cardBroadcast = findViewById(R.id.cardViewBroadcast);
        Button btnLogout = findViewById(R.id.btnLogout);

        cardDangerZones.setOnClickListener(v -> {
            startActivity(new Intent(AdminDashboard.this, DangerZoneApprovalActivity.class));
        });

        cardEmergencyLogs.setOnClickListener(v -> {
            startActivity(new Intent(AdminDashboard.this, SOSLogActivity.class));
        });

        cardAnalytics.setOnClickListener(v -> {
            Toast.makeText(this, "Analytics feature coming soon", Toast.LENGTH_SHORT).show();
        });

        cardBroadcast.setOnClickListener(v -> {
            Toast.makeText(this, "Broadcast system initialized", Toast.LENGTH_SHORT).show();
        });

        btnLogout.setOnClickListener(v -> {
            startActivity(new Intent(AdminDashboard.this, LoginActivity.class));
            finish();
        });
    }
}

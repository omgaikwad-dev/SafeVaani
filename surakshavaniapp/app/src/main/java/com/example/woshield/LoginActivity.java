package com.example.woshield;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;
    private EditText etEmail, etPassword;
    private Button btnLogin, btnOfflineSos;
    private TextView tvForgotPassword, tvRegister;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnOfflineSos = findViewById(R.id.btnOfflineSos);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvRegister = findViewById(R.id.registerText);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnLogin.setOnClickListener(v -> loginUser());
        tvForgotPassword.setOnClickListener(v -> resetPassword());
        tvRegister.setOnClickListener(v -> goToSignup(v));
        
        btnOfflineSos.setOnClickListener(v -> handleOfflineSos());

        View btnSeedDemoData = findViewById(R.id.btnSeedDemoData);
        if (btnSeedDemoData != null) {
            btnSeedDemoData.setOnClickListener(v -> DemoDataSeeder.seedAll(this, true, null));
        }

        View cardNavUser = findViewById(R.id.cardNavUserDashboard);
        if (cardNavUser != null) {
            cardNavUser.setOnClickListener(v -> DemoDataSeeder.prepareAndLaunch(this, HomePage.class));
        }

        View cardNavFamily = findViewById(R.id.cardNavFamilyDashboard);
        if (cardNavFamily != null) {
            cardNavFamily.setOnClickListener(v -> DemoDataSeeder.prepareAndLaunch(this, FamilyDashboardActivity.class));
        }

        View cardNavAdmin = findViewById(R.id.cardNavAdminDashboard);
        if (cardNavAdmin != null) {
            cardNavAdmin.setOnClickListener(v -> DemoDataSeeder.prepareAndLaunch(this, AdminDashboard.class));
        }
    }

    private void handleOfflineSos() {
        if (checkPermissions()) {
            triggerOfflineSos();
        } else {
            requestPermissions();
        }
    }

    private void triggerOfflineSos() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Toast.makeText(this, "Fetching location...", Toast.LENGTH_SHORT).show();
        
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                sendOfflineAlerts(location);
            } else {
                Toast.makeText(this, "Could not get location. Ensure GPS is ON.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void sendOfflineAlerts(Location location) {
        List<String> contacts = getEmergencyContacts();
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No emergency contacts found locally!", Toast.LENGTH_LONG).show();
            return;
        }

        String locUrl = "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
        String message = "OFFLINE EMERGENCY ALERT!\nI am in danger. My location: " + locUrl;

        try {
            SmsManager smsManager = SmsManager.getDefault();
            for (String number : contacts) {
                smsManager.sendTextMessage(number, null, message, null, null);
            }
            Toast.makeText(this, "Offline SOS sent to " + contacts.size() + " contacts!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private List<String> getEmergencyContacts() {
        List<String> contacts = new ArrayList<>();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String contactsJson = prefs.getString("emergency_contacts", "[]");
        try {
            JSONArray array = new JSONArray(contactsJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                contacts.add(obj.getString("number"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return contacts;
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION}, 
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                triggerOfflineSos();
            } else {
                Toast.makeText(this, "Permissions denied. Cannot send SOS.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) { etEmail.setError("Email required"); return; }
        if (TextUtils.isEmpty(password)) { etPassword.setError("Password required"); return; }

        if (email.equals("admin@nightshift.in") && password.equals("admin123")) {
            Toast.makeText(this, "Admin Login — NightShift Navigator", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, AdminDashboard.class));
            finish();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
                        checkUserRole();
                    } else {
                        Toast.makeText(this, "Login Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkUserRole() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            mDatabase.child("Users").child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String role = snapshot.child("role").getValue(String.class);
                        if ("admin".equals(role)) {
                            startActivity(new Intent(LoginActivity.this, AdminDashboard.class));
                        } else {
                            startActivity(new Intent(LoginActivity.this, HomePage.class));
                        }
                        finish();
                    } else {
                        startActivity(new Intent(LoginActivity.this, HomePage.class));
                        finish();
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(LoginActivity.this, "Database Error", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void resetPassword() {
        String email = etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Enter email to reset password", Toast.LENGTH_SHORT).show();
            return;
        }
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Reset link sent!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    public void goToSignup(View v) {
        startActivity(new Intent(this, SignupActivity.class));
    }
}

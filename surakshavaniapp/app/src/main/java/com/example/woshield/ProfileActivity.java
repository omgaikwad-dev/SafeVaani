package com.example.woshield;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ProfileActivity extends AppCompatActivity {
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final String TAG = "ProfileActivity";
    
    private ImageView imgProfilePhoto;
    private Button btnChangePhoto, btnSaveProfile;
    private EditText etName, etMobileNumber;
    private Uri imageUri;
    
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private StorageReference mStorage;
    private String currentUserId;
    private String generatedOtp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            if (!DemoDataSeeder.isDemoMode(this)) {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }
        }
        
        currentUserId = (user != null) ? user.getUid() : DemoDataSeeder.DEMO_UID;
        mDatabase = FirebaseDatabase.getInstance().getReference("Users").child(currentUserId);
        mStorage = FirebaseStorage.getInstance().getReference("ProfilePictures").child(currentUserId + ".jpg");

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar_profile);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        imgProfilePhoto = findViewById(R.id.img_profile_photo);
        btnChangePhoto = findViewById(R.id.btn_change_photo);
        btnSaveProfile = findViewById(R.id.btn_save_profile);
        Button btnPrivacyCenter = findViewById(R.id.btn_privacy_center);
        etName = findViewById(R.id.et_name);
        etMobileNumber = findViewById(R.id.et_mobile_number);

        fetchUserProfile();

        btnChangePhoto.setOnClickListener(v -> openGallery());
        btnSaveProfile.setOnClickListener(v -> validateAndVerify());
        if (btnPrivacyCenter != null) {
            btnPrivacyCenter.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, PrivacyCenterActivity.class)));
        }
    }

    private void fetchUserProfile() {
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    String mobile = snapshot.child("mobile").getValue(String.class);
                    String photoUrl = snapshot.child("profilePhotoUrl").getValue(String.class);

                    if (name != null) etName.setText(name);
                    if (mobile != null) etMobileNumber.setText(mobile);
                    if (photoUrl != null && !isDestroyed()) {
                        Glide.with(ProfileActivity.this).load(photoUrl).placeholder(R.drawable.profile_icon).into(imgProfilePhoto);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void validateAndVerify() {
        String name = etName.getText().toString().trim();
        String mobile = etMobileNumber.getText().toString().trim();

        if (name.isEmpty() || !mobile.matches("\\d{10}")) {
            Toast.makeText(this, "Valid name and 10-digit mobile required", Toast.LENGTH_SHORT).show();
            return;
        }

        generatedOtp = String.valueOf(1000 + new Random().nextInt(9000));
        Toast.makeText(this, "Verification OTP: " + generatedOtp, Toast.LENGTH_LONG).show();
        showOtpDialog();
    }

    private void showOtpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Verify Changes");
        final EditText inputOtp = new EditText(this);
        inputOtp.setHint("Enter OTP");
        inputOtp.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(inputOtp);

        builder.setPositiveButton("Verify", (dialog, which) -> {
            if (inputOtp.getText().toString().trim().equals(generatedOtp)) {
                saveProfileToFirebase();
            } else {
                Toast.makeText(this, "Incorrect OTP", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveProfileToFirebase() {
        Toast.makeText(this, "Updating profile...", Toast.LENGTH_SHORT).show();
        
        if (imageUri != null) {
            // Upload Image First
            mStorage.putFile(imageUri).addOnSuccessListener(taskSnapshot -> {
                mStorage.getDownloadUrl().addOnSuccessListener(uri -> {
                    updateDatabase(uri.toString());
                });
            }).addOnFailureListener(e -> {
                Toast.makeText(ProfileActivity.this, "Photo upload failed", Toast.LENGTH_SHORT).show();
                updateDatabase(null); // Fallback to update just text
            });
        } else {
            updateDatabase(null);
        }
    }

    private void updateDatabase(String photoUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", etName.getText().toString().trim());
        updates.put("mobile", etMobileNumber.getText().toString().trim());
        if (photoUrl != null) updates.put("profilePhotoUrl", photoUrl);

        mDatabase.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(ProfileActivity.this, "Profile Updated Successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ProfileActivity.this, "Failed to update database", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                imgProfilePhoto.setImageBitmap(bitmap);
            } catch (IOException e) { e.printStackTrace(); }
        }
    }
}

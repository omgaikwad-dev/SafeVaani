package com.example.woshield;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * SplashActivity — SafeVaani Splash / Flashscreen
 * Shows the new SafeVaani app logo and "The City That Sleeps While You Work" branding.
 * Automatically routes authenticated users or demo session to HomePage.
 */
public class SplashActivity extends AppCompatActivity {

    public static final String TAG = "SplashActivity";
    private static final long SPLASH_DELAY_MS = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_splash);

        // Gentle scale & fade-in animation for brand logo
        View logoCard = findViewById(R.id.card_splash_logo);
        View centerLayout = findViewById(R.id.layout_splash_center);
        if (centerLayout != null) {
            centerLayout.setAlpha(0f);
            centerLayout.setTranslationY(30f);
            centerLayout.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(700)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            Intent nextIntent;
            if (currentUser != null || DemoDataSeeder.isDemoMode(SplashActivity.this)) {
                nextIntent = new Intent(SplashActivity.this, HomePage.class);
            } else {
                nextIntent = new Intent(SplashActivity.this, LoginActivity.class);
            }
            startActivity(nextIntent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, SPLASH_DELAY_MS);
    }
}

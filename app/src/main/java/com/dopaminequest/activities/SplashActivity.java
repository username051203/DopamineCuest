package com.dopaminequest.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.dopaminequest.R;
import com.dopaminequest.utils.AppState;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent next;
            if (AppState.isOnboarded(this)) {
                next = new Intent(this, MainActivity.class);
            } else {
                next = new Intent(this, OnboardingActivity.class);
            }
            startActivity(next);
            finish();
        }, 1200);
    }
}

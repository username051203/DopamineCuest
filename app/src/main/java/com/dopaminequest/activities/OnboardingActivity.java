package com.dopaminequest.activities;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.dopaminequest.R;
import com.dopaminequest.receivers.DQDeviceAdminReceiver;
import com.dopaminequest.utils.AppState;

public class OnboardingActivity extends AppCompatActivity {

    // Steps: 0=welcome, 1=accessibility, 2=battery, 3=device_admin, 4=apps
    private int step = 0;

    private TextView  tvStep, tvTitle, tvBody;
    private Button    btnPrimary, btnSkip;
    private ProgressBar progressBar;

    private final String[] TITLES = {
        "Welcome to\nDopamineQuest",
        "Enable Shield",
        "Stay protected",
        "Prevent removal",
        "Choose your apps"
    };
    private final String[] BODIES = {
        "A focus tool that actually works.\nWe need a few permissions to protect you from distractions.",
        "Accessibility access lets DopamineQuest detect when a blocked app opens and cover it with your task list.",
        "Disable battery optimization so the Shield stays active — even when your phone tries to save power.",
        "Device Admin prevents DopamineQuest from being uninstalled without completing 3 tasks first.",
        "Select which apps you want to keep using. Everything else will be blocked until you complete tasks."
    };
    private final String[] BUTTONS = {
        "Let's go",
        "Enable Accessibility",
        "Disable Battery Optimization",
        "Enable Device Admin",
        "Choose Apps"
    };

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> advanceIfReady()
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        tvStep      = findViewById(R.id.tv_step);
        tvTitle     = findViewById(R.id.tv_title);
        tvBody      = findViewById(R.id.tv_body);
        btnPrimary  = findViewById(R.id.btn_primary);
        btnSkip     = findViewById(R.id.btn_skip);
        progressBar = findViewById(R.id.progress_bar);

        renderStep();

        btnPrimary.setOnClickListener(v -> handlePrimary());
        btnSkip.setOnClickListener(v -> {
            step++;
            renderStep();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        advanceIfReady();
    }

    private void renderStep() {
        progressBar.setMax(TITLES.length - 1);
        progressBar.setProgress(step);
        tvStep.setText("Step " + (step + 1) + " of " + TITLES.length);
        tvTitle.setText(TITLES[step]);
        tvBody.setText(BODIES[step]);
        btnPrimary.setText(BUTTONS[step]);
        btnSkip.setVisibility(step == 0 ? View.GONE : View.VISIBLE);
    }

    private void handlePrimary() {
        switch (step) {
            case 0:
                step++;
                renderStep();
                break;

            case 1: // Accessibility
                Intent acc = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                launcher.launch(acc);
                break;

            case 2: // Battery optimization
                Intent bat = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                launcher.launch(bat);
                break;

            case 3: // Device Admin
                Intent admin = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                admin.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        DQDeviceAdminReceiver.getComponentName(this));
                admin.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Required to prevent removal without completing tasks.");
                launcher.launch(admin);
                break;

            case 4: // App selection
                startActivity(new Intent(this, AppSelectActivity.class));
                break;
        }
    }

    private void advanceIfReady() {
        switch (step) {
            case 1:
                if (isAccessibilityEnabled()) { step++; renderStep(); }
                break;
            case 2:
                if (isBatteryOptimizationDisabled()) { step++; renderStep(); }
                break;
            case 3:
                if (isDeviceAdminEnabled()) { step++; renderStep(); }
                break;
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            int enabled = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
            if (enabled != 1) return false;
            String services = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return services != null && services.contains(getPackageName());
        } catch (Exception e) { return false; }
    }

    private boolean isBatteryOptimizationDisabled() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean isDeviceAdminEnabled() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName cn = DQDeviceAdminReceiver.getComponentName(this);
        return dpm != null && dpm.isAdminActive(cn);
    }
}

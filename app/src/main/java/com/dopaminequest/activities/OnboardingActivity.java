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
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.dopaminequest.R;
import com.dopaminequest.receivers.DQDeviceAdminReceiver;
import com.dopaminequest.utils.AppState;
import com.dopaminequest.utils.GeminiService;

public class OnboardingActivity extends AppCompatActivity {

    // Steps: 0=welcome, 1=accessibility, 2=battery, 3=device_admin, 4=api_key, 5=apps
    private int step = 0;

    private TextView  tvStep, tvTitle, tvBody;
    private Button    btnPrimary, btnSkip;
    private ProgressBar progressBar;
    private EditText  etApiKey;
    private View      layoutApiKey;

    private final String[] TITLES = {
        "Welcome to\nDopamineQuest",
        "Enable Shield",
        "Stay protected",
        "Prevent removal",
        "Gemini API Key",
        "Choose your apps"
    };
    private final String[] BODIES = {
        "A focus tool that actually works.\nWe need a few permissions to protect you.",
        "Accessibility access lets DopamineQuest detect when a blocked app opens and show your task list.",
        "Disable battery optimization so the Shield stays active even when your phone tries to save power.",
        "Device Admin prevents DopamineQuest from being uninstalled without completing 3 tasks first.",
        "Paste your Groq API key. It's used to generate quiz questions and power the coach.\n\nGet a FREE key at: console.groq.com\n14,400 requests/day free. Key never leaves your device.",
        "Select apps you want to keep using. Everything else will be blocked until you complete tasks."
    };
    private final String[] BUTTONS = {
        "Let's go",
        "Enable Accessibility",
        "Disable Battery Optimization",
        "Enable Device Admin",
        "Validate & Continue",
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
        etApiKey    = findViewById(R.id.et_api_key);
        layoutApiKey= findViewById(R.id.layout_api_key);

        renderStep();
        btnPrimary.setOnClickListener(v -> handlePrimary());
        btnSkip.setOnClickListener(v -> { step++; renderStep(); });
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
        btnSkip.setVisibility(step == 0 || step == 4 ? View.GONE : View.VISIBLE);
        layoutApiKey.setVisibility(step == 4 ? View.VISIBLE : View.GONE);
    }

    private void handlePrimary() {
        switch (step) {
            case 0:
                step++; renderStep(); break;
            case 1:
                launcher.launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); break;
            case 2:
                launcher.launch(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()))); break;
            case 3:
                Intent admin = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                admin.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    DQDeviceAdminReceiver.getComponentName(this));
                admin.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Required to prevent removal without completing tasks.");
                launcher.launch(admin); break;
            case 4:
                validateApiKey(); break;
            case 5:
                startActivity(new Intent(this, AppSelectActivity.class)); break;
        }
    }

    private void validateApiKey() {
        String key = etApiKey.getText().toString().trim();
        if (key.isEmpty()) {
            tvBody.setText("Please paste your Gemini API key first.");
            return;
        }
        btnPrimary.setEnabled(false);
        btnPrimary.setText("Validating…");
        tvBody.setText("Checking your key with Gemini…");

        GeminiService.validateKey(this, key, new GeminiService.Callback<Boolean>() {
            @Override public void onResult(Boolean ok) {
                runOnUiThread(() -> {
                    btnPrimary.setEnabled(true);
                    if (ok) {
                        AppState.setGeminiKey(OnboardingActivity.this, key);
                        step++; renderStep();
                    } else {
                        btnPrimary.setText(BUTTONS[4]);
                        tvBody.setText("Key invalid or no internet. Check the key and try again.\n\nGet a free key at: console.groq.com");
                    }
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    btnPrimary.setEnabled(true);
                    btnPrimary.setText(BUTTONS[4]);
                    tvBody.setText("Error: " + error + "\n\nCheck your internet and try again.");
                });
            }
        });
    }

    private void advanceIfReady() {
        switch (step) {
            case 1: if (isAccessibilityEnabled())        { step++; renderStep(); } break;
            case 2: if (isBatteryOptimizationDisabled()) { step++; renderStep(); } break;
            case 3: if (isDeviceAdminEnabled())          { step++; renderStep(); } break;
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            int en = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
            if (en != 1) return false;
            String svcs = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return svcs != null && svcs.contains(getPackageName());
        } catch (Exception e) { return false; }
    }
    private boolean isBatteryOptimizationDisabled() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }
    private boolean isDeviceAdminEnabled() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(DQDeviceAdminReceiver.getComponentName(this));
    }
}

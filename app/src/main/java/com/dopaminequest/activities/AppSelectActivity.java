package com.dopaminequest.activities;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dopaminequest.R;
import com.dopaminequest.adapters.AppSelectAdapter;
import com.dopaminequest.services.PersistenceService;
import com.dopaminequest.utils.AppState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppSelectActivity extends AppCompatActivity {

    private AppSelectAdapter adapter;
    private Button btnDone;
    private TextView tvSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_select);

        tvSubtitle = findViewById(R.id.tv_subtitle);
        tvSubtitle.setText("Loading apps…");

        RecyclerView rv = findViewById(R.id.rv_apps);
        rv.setLayoutManager(new LinearLayoutManager(this));

        btnDone = findViewById(R.id.btn_done);
        btnDone.setEnabled(false);
        btnDone.setOnClickListener(v -> saveAndProceed());

        loadApps(rv);
    }

    @SuppressWarnings("deprecation")
    private void loadApps(RecyclerView rv) {
        AsyncTask.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            List<AppSelectAdapter.AppItem> items = new ArrayList<>();
            Set<String> systemDefaults = AppState.getAllowlist(this);

            for (ApplicationInfo info : installed) {
                // Skip system apps that are already hardcoded — no need to show them
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                // Skip our own package
                if (info.packageName.equals(getPackageName())) continue;

                String label = pm.getApplicationLabel(info).toString();
                boolean preChecked = systemDefaults.contains(info.packageName);
                items.add(new AppSelectAdapter.AppItem(info.packageName, label, info, preChecked));
            }

            // Sort alphabetically
            Collections.sort(items, (a, b) -> a.label.compareToIgnoreCase(b.label));

            runOnUiThread(() -> {
                tvSubtitle.setText("Select apps you want to keep using.\nEverything else will be blocked.");
                adapter = new AppSelectAdapter(items, count ->
                    btnDone.setText("Allow " + count + " app" + (count == 1 ? "" : "s") + " →"));
                adapter.setHasStableIds(true);
        rv.setAdapter(adapter);
                btnDone.setEnabled(true);
                btnDone.setText("Allow 0 apps →");
            });
        });
    }

    private void saveAndProceed() {
        if (adapter == null) return;

        Set<String> selected = new HashSet<>(adapter.getSelectedPackages());
        AppState.setAllowlist(this, selected);
        AppState.setOnboarded(this, true);
        AppState.setShieldEnabled(this, true);

        // Start persistence service
        Intent svc = new Intent(this, PersistenceService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        startActivity(new Intent(this, MainActivity.class));
        finishAffinity();
    }
}

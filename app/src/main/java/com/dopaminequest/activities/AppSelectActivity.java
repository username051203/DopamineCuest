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
    private Button           btnDone;
    private TextView         tvSubtitle;

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
            // GET_META_DATA gets all apps
            List<ApplicationInfo> installed =
                pm.getInstalledApplications(PackageManager.GET_META_DATA);

            // System apps that should be pre-checked (hardcoded allowlist)
            Set<String> hardcoded = AppState.getAllowlist(this);

            List<AppSelectAdapter.AppItem> userApps   = new ArrayList<>();
            List<AppSelectAdapter.AppItem> systemApps = new ArrayList<>();

            for (ApplicationInfo info : installed) {
                if (info.packageName.equals(getPackageName())) continue;

                // Skip purely internal system processes with no label
                String label = pm.getApplicationLabel(info).toString();
                if (label.equals(info.packageName)) continue;

                // Skip apps with no launch intent and not in hardcoded list
                Intent launch = pm.getLaunchIntentForPackage(info.packageName);
                boolean isHardcoded = hardcoded.contains(info.packageName);
                if (launch == null && !isHardcoded) continue;

                boolean isSystem = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean preChecked = isHardcoded || isSystem;

                AppSelectAdapter.AppItem item = new AppSelectAdapter.AppItem(
                    info.packageName, label, info, preChecked);

                if (isSystem) systemApps.add(item);
                else          userApps.add(item);
            }

            // Sort each group alphabetically
            Collections.sort(userApps,   (a, b) -> a.label.compareToIgnoreCase(b.label));
            Collections.sort(systemApps, (a, b) -> a.label.compareToIgnoreCase(b.label));

            // User apps first (most relevant), then system apps
            List<AppSelectAdapter.AppItem> all = new ArrayList<>();
            all.addAll(userApps);
            all.addAll(systemApps);

            runOnUiThread(() -> {
                tvSubtitle.setText(
                    "Check apps you want to keep accessible.\n" +
                    "System apps are pre-checked. Uncheck to block them.");
                adapter = new AppSelectAdapter(all,
                    count -> btnDone.setText(
                        "Allow " + count + " app" + (count == 1 ? "" : "s") + " →"));
                adapter.setHasStableIds(true);
                rv.setAdapter(adapter);
                btnDone.setEnabled(true);

                // Count pre-checked
                int pre = 0;
                for (AppSelectAdapter.AppItem i : all) if (i.selected) pre++;
                btnDone.setText("Allow " + pre + " apps →");
            });
        });
    }

    private void saveAndProceed() {
        if (adapter == null) return;
        Set<String> selected = new HashSet<>(adapter.getSelectedPackages());
        AppState.setAllowlist(this, selected);
        AppState.setOnboarded(this, true);
        AppState.setShieldEnabled(this, true);

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

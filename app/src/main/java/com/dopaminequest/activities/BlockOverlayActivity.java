package com.dopaminequest.activities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dopaminequest.R;
import com.dopaminequest.adapters.AllowedAppAdapter;
import com.dopaminequest.adapters.OverlayTaskAdapter;
import com.dopaminequest.models.Task;
import com.dopaminequest.receivers.AccessWindowReceiver;
import com.dopaminequest.utils.AppState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class BlockOverlayActivity extends AppCompatActivity {

    private TextView     tvAccessTimer, tvBlockedPkg;
    private CountDownTimer accessTimer;
    private RecyclerView rvTasks, rvAllowedApps;
    private View         layoutAccessActive, layoutTaskList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_block_overlay);

        tvBlockedPkg       = findViewById(R.id.tv_blocked_pkg);
        tvAccessTimer      = findViewById(R.id.tv_access_timer);
        layoutAccessActive = findViewById(R.id.layout_access_active);
        layoutTaskList     = findViewById(R.id.layout_task_list);
        rvTasks            = findViewById(R.id.rv_tasks);
        rvAllowedApps      = findViewById(R.id.rv_allowed_apps);

        final String blockedPkg = getIntent().getStringExtra("blocked_pkg");
        if (blockedPkg != null) {
            tvBlockedPkg.setText(getAppLabel(blockedPkg));
        }

        // Coach chat button
        findViewById(R.id.btn_coach_chat).setOnClickListener(v -> {
            android.widget.Toast.makeText(this,"opening coach",android.widget.Toast.LENGTH_SHORT).show(); Intent ci = new Intent(this, CoachChatActivity.class);
            ci.putExtra("blocked_pkg", blockedPkg);
            startActivity(ci);
        });

        // Allowed apps grid — user can open these directly
        loadAllowedApps();

        // Task list
        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        OverlayTaskAdapter adapter = new OverlayTaskAdapter(this, Task.all(), task -> {
            Intent i = new Intent(this, TaskDetailActivity.class);
            i.putExtra("task_id", task.id);
            i.putExtra("from_overlay", true);
            startActivity(i);
        });
        rvTasks.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Small delay to ensure SharedPreferences commit() has flushed
        // before we check the access window state
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            this::checkAccessWindow, 120);
    }

    private void checkAccessWindow() {
        if (AppState.hasActiveAccessWindow(this)
                || AppState.hasTempAppAccess(this,
                    getIntent().getStringExtra("blocked_pkg"))) {
            // Access granted — dismiss overlay entirely so user gets to their app
            finish();
        }
        // else stay on overlay showing task list
    }

    private void showAccessActiveUI() {
        layoutAccessActive.setVisibility(View.VISIBLE);
        layoutTaskList.setVisibility(View.GONE);

        long remaining = AppState.getAccessWindowEnd(this) - System.currentTimeMillis();
        if (accessTimer != null) accessTimer.cancel();
        accessTimer = new CountDownTimer(remaining, 1000) {
            @Override public void onTick(long ms) {
                long m = TimeUnit.MILLISECONDS.toMinutes(ms);
                long s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
                tvAccessTimer.setText(String.format(Locale.US, "%02d:%02d", m, s));
            }
            @Override public void onFinish() {
                AppState.revokeAccessWindow(BlockOverlayActivity.this);
                showTaskListUI();
            }
        }.start();

        findViewById(R.id.btn_use_apps).setOnClickListener(v -> finish());
    }

    private void showTaskListUI() {
        layoutAccessActive.setVisibility(View.GONE);
        layoutTaskList.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    private void loadAllowedApps() {
        rvAllowedApps.setLayoutManager(
            new GridLayoutManager(this, 4));

        AsyncTask.execute(() -> {
            PackageManager pm = getPackageManager();
            Set<String> allowlist = AppState.getAllowlist(this);
            List<AllowedAppAdapter.AppEntry> entries = new ArrayList<>();

            for (String pkg : allowlist) {
                if (pkg.equals(getPackageName())) continue;
                try {
                    ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                    // Only show apps that are actually installed and launchable
                    Intent launch = pm.getLaunchIntentForPackage(pkg);
                    if (launch == null) continue;
                    String label = pm.getApplicationLabel(info).toString();
                    android.graphics.drawable.Drawable icon =
                        pm.getApplicationIcon(info);
                    entries.add(new AllowedAppAdapter.AppEntry(pkg, label, icon));
                } catch (PackageManager.NameNotFoundException ignored) {}
            }

            // Sort alphabetically
            entries.sort((a, b) -> a.label.compareToIgnoreCase(b.label));

            runOnUiThread(() -> {
                AllowedAppAdapter adapter = new AllowedAppAdapter(entries, pkg -> {
                    // Launch the allowed app directly
                    Intent launch = pm.getLaunchIntentForPackage(pkg);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launch);
                        finish(); // dismiss overlay
                    }
                });
                rvAllowedApps.setAdapter(adapter);
            });
        });
    }

    private String getAppLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(
                pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg; }
    }

    public static void grantAccessAndScheduleExpiry(
            android.content.Context ctx, Task task) {
        AppState.grantAccessWindow(ctx, task.accessMinutes);
        AlarmManager am =
            (AlarmManager) ctx.getSystemService(android.content.Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, AccessWindowReceiver.class);
        i.setAction("com.dopaminequest.ACTION_ACCESS_EXPIRED");
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long triggerAt = AppState.getAccessWindowEnd(ctx);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    @Override
    public void onBackPressed() {
        if (AppState.hasActiveAccessWindow(this)) {
            super.onBackPressed();
        }
        // else swallow back press
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (accessTimer != null) accessTimer.cancel();
    }
}

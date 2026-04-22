package com.dopaminequest.activities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
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

    private TextView     tvBlockedPkg, tvAccessTimer;
    private CountDownTimer accessTimer;

    // Tab views
    private Button   tabApps, tabTasks;
    private View     panelApps, panelTasks;

    // Access active panel
    private View     layoutAccessActive, layoutMain;
    private Button   btnUseApps;

    private RecyclerView rvTasks, rvAllowedApps;
    private String   blockedPkg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_block_overlay);

        blockedPkg         = getIntent().getStringExtra("blocked_pkg");
        tvBlockedPkg       = findViewById(R.id.tv_blocked_pkg);
        tvAccessTimer      = findViewById(R.id.tv_access_timer);
        layoutAccessActive = findViewById(R.id.layout_access_active);
        layoutMain         = findViewById(R.id.layout_main);
        btnUseApps         = findViewById(R.id.btn_use_apps);
        tabApps            = findViewById(R.id.tab_apps);
        tabTasks           = findViewById(R.id.tab_tasks);
        panelApps          = findViewById(R.id.panel_apps);
        panelTasks         = findViewById(R.id.panel_tasks);
        rvTasks            = findViewById(R.id.rv_tasks);
        rvAllowedApps      = findViewById(R.id.rv_allowed_apps);

        if (blockedPkg != null)
            tvBlockedPkg.setText(getAppLabel(blockedPkg));

        // Coach button
        findViewById(R.id.btn_coach_chat).setOnClickListener(v -> {
            Intent ci = new Intent(this, CoachChatActivity.class);
            ci.putExtra("blocked_pkg", blockedPkg);
            startActivity(ci);
        });

        // Tabs
        tabApps.setOnClickListener(v  -> showTab(true));
        tabTasks.setOnClickListener(v -> showTab(false));

        // Default: show apps tab first
        showTab(true);
        loadAllowedApps();

        // Tasks
        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        rvTasks.setAdapter(new OverlayTaskAdapter(this, Task.all(), task -> {
            Intent i = new Intent(this, TaskDetailActivity.class);
            i.putExtra("task_id", task.id);
            i.putExtra("from_overlay", true);
            i.putExtra("blocked_pkg", blockedPkg);
            startActivity(i);
        }));

        // Use apps button
        btnUseApps.setOnClickListener(v -> launchBlockedAndFinish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Small delay to let SharedPreferences commit() flush
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAccess, 150);
    }

    private void checkAccess() {
        boolean globalWindow = AppState.hasActiveAccessWindow(this);
        boolean tempWindow   = AppState.hasTempAppAccess(this, blockedPkg);

        if (globalWindow || tempWindow) {
            // Access earned — launch the blocked app and dismiss
            launchBlockedAndFinish();
        }
        // else stay on overlay
    }

    private void launchBlockedAndFinish() {
        // Try to launch the specific blocked app
        if (blockedPkg != null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(blockedPkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                              | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launch);
            }
        }
        finish();
    }

    private void showTab(boolean appsActive) {
        panelApps.setVisibility(appsActive ? View.VISIBLE : View.GONE);
        panelTasks.setVisibility(appsActive ? View.GONE : View.VISIBLE);

        // Active tab: ink background, paper text
        // Inactive: paper background, ink3 text
        int activeColor   = getResources().getColor(R.color.ink,   getTheme());
        int inactiveColor = getResources().getColor(R.color.ink3,  getTheme());
        int activeBg      = getResources().getColor(R.color.ink,   getTheme());
        int inactiveBg    = getResources().getColor(R.color.rule,  getTheme());
        int activeText    = getResources().getColor(R.color.paper, getTheme());

        tabApps.setBackgroundColor(appsActive  ? activeBg  : inactiveBg);
        tabApps.setTextColor(appsActive        ? activeText : inactiveColor);
        tabTasks.setBackgroundColor(appsActive ? inactiveBg : activeBg);
        tabTasks.setTextColor(appsActive       ? inactiveColor : activeText);
    }

    @SuppressWarnings("deprecation")
    private void loadAllowedApps() {
        rvAllowedApps.setLayoutManager(new GridLayoutManager(this, 4));

        AsyncTask.execute(() -> {
            PackageManager pm = getPackageManager();
            Set<String> allowlist = AppState.getAllowlist(this);
            List<AllowedAppAdapter.AppEntry> entries = new ArrayList<>();

            for (String pkg : allowlist) {
                if (pkg.equals(getPackageName())) continue;
                try {
                    ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                    Intent launch = pm.getLaunchIntentForPackage(pkg);
                    if (launch == null) continue;
                    entries.add(new AllowedAppAdapter.AppEntry(
                        pkg,
                        pm.getApplicationLabel(info).toString(),
                        pm.getApplicationIcon(info)));
                } catch (PackageManager.NameNotFoundException ignored) {}
            }

            entries.sort((a, b) -> a.label.compareToIgnoreCase(b.label));

            runOnUiThread(() -> rvAllowedApps.setAdapter(
                new AllowedAppAdapter(entries, pkg -> {
                    Intent launch = pm.getLaunchIntentForPackage(pkg);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launch);
                        finish();
                    }
                })));
        });
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
        long at = AppState.getAccessWindowEnd(ctx);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private String getAppLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(
                getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg; }
    }

    @Override
    public void onBackPressed() { /* swallow — no escaping overlay */ }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (accessTimer != null) accessTimer.cancel();
    }
}

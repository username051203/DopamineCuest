package com.dopaminequest.activities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dopaminequest.R;
import com.dopaminequest.adapters.OverlayTaskAdapter;
import com.dopaminequest.models.Task;
import com.dopaminequest.receivers.AccessWindowReceiver;
import com.dopaminequest.utils.AppState;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class BlockOverlayActivity extends AppCompatActivity {

    private TextView         tvAccessTimer;
    private CountDownTimer   accessTimer;
    private RecyclerView     rvTasks;
    private TextView         tvBlockedPkg;
    private View             layoutAccessActive;
    private View             layoutTaskList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_block_overlay);

        tvBlockedPkg      = findViewById(R.id.tv_blocked_pkg);
        tvAccessTimer     = findViewById(R.id.tv_access_timer);
        layoutAccessActive = findViewById(R.id.layout_access_active);
        layoutTaskList    = findViewById(R.id.layout_task_list);
        rvTasks           = findViewById(R.id.rv_tasks);

        String blockedPkg = getIntent().getStringExtra("blocked_pkg");
        if (blockedPkg != null) {
            tvBlockedPkg.setText(blockedPkg.replace("com.", "").replace(".", " ").toUpperCase());
        }

        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        loadTasks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAccessWindow();
    }

    private void checkAccessWindow() {
        if (AppState.hasActiveAccessWindow(this)) {
            showAccessActiveUI();
        } else {
            showTaskListUI();
        }
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

        // Button to dismiss and use apps freely during window
        findViewById(R.id.btn_use_apps).setOnClickListener(v -> finish());
    }

    private void showTaskListUI() {
        layoutAccessActive.setVisibility(View.GONE);
        layoutTaskList.setVisibility(View.VISIBLE);
        loadTasks();
    }

    private void loadTasks() {
        List<Task> tasks = Task.all();
        OverlayTaskAdapter adapter = new OverlayTaskAdapter(this, tasks, task -> {
            // User tapped a task — open task detail
            Intent i = new Intent(this, TaskDetailActivity.class);
            i.putExtra("task_id", task.id);
            i.putExtra("from_overlay", true);
            startActivity(i);
        });
        rvTasks.setAdapter(adapter);
    }

    /**
     * Called by TaskDetailActivity result when a task is completed from overlay context.
     * Grant access window based on task weight.
     */
    public static void grantAccessAndScheduleExpiry(android.content.Context ctx, Task task) {
        AppState.grantAccessWindow(ctx, task.accessMinutes);

        // Schedule alarm to revoke window when it expires
        AlarmManager am = (AlarmManager) ctx.getSystemService(android.content.Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, AccessWindowReceiver.class);
        i.setAction("com.dopaminequest.ACTION_ACCESS_EXPIRED");
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        long triggerAt = AppState.getAccessWindowEnd(ctx);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    @Override
    public void onBackPressed() {
        // Do NOT allow back press to escape overlay unless access window active
        if (AppState.hasActiveAccessWindow(this)) {
            super.onBackPressed();
        }
        // else: swallow back press — user stays on overlay
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (accessTimer != null) accessTimer.cancel();
    }
}

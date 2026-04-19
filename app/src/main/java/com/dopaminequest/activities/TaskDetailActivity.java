package com.dopaminequest.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dopaminequest.R;
import com.dopaminequest.models.Task;
import com.dopaminequest.utils.AppState;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TaskDetailActivity extends AppCompatActivity {

    private static final int REQUEST_PHOTO = 101;

    private Task          task;
    private boolean       fromOverlay;

    private TextView      tvEmoji, tvTitle, tvTag, tvXp, tvTimer;
    private Button        btnAction;
    private View          progressLine;

    private CountDownTimer timer;
    private boolean        timerFinished = false;
    private long           totalSecs;
    private long           remainingSecs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_detail);

        int taskId  = getIntent().getIntExtra("task_id", -1);
        fromOverlay = getIntent().getBooleanExtra("from_overlay", false);
        task        = Task.findById(taskId);

        if (task == null) { finish(); return; }

        tvEmoji      = findViewById(R.id.tv_emoji);
        tvTitle      = findViewById(R.id.tv_title);
        tvTag        = findViewById(R.id.tv_tag);
        tvXp         = findViewById(R.id.tv_xp);
        tvTimer      = findViewById(R.id.tv_timer);
        btnAction    = findViewById(R.id.btn_action);
        progressLine = findViewById(R.id.progress_line);

        // Set pivot to left edge so scale grows left→right
        progressLine.setPivotX(0f);
        progressLine.setScaleX(0f);

        tvEmoji.setText(task.emoji);
        tvTitle.setText(task.title);
        tvTag.setText(task.tag.toUpperCase());
        tvXp.setText("+" + task.xp + " XP  ·  +" + task.accessMinutes + " min access");

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        if (task.verification == Task.VerificationType.TIMER) {
            setupTimer();
        } else {
            setupPhoto();
        }
    }

    // ── TIMER ────────────────────────────────────────────────────────────────

    private void setupTimer() {
        tvTimer.setVisibility(View.VISIBLE);
        totalSecs = task.durationMins * 60L;
        remainingSecs = totalSecs;
        updateTimerDisplay(totalSecs * 1000);

        btnAction.setText("Start Timer");
        btnAction.setEnabled(true);
        btnAction.setOnClickListener(v -> startTimer());
    }

    private void startTimer() {
        btnAction.setText("Running…");
        btnAction.setEnabled(false);

        timer = new CountDownTimer(remainingSecs * 1000, 1000) {
            @Override public void onTick(long ms) {
                remainingSecs = ms / 1000;
                updateTimerDisplay(ms);
                float pct = 1f - (float) ms / (totalSecs * 1000f);
                progressLine.setScaleX(pct);
            }
            @Override public void onFinish() {
                timerFinished = true;
                tvTimer.setText("00:00");
                progressLine.setScaleX(1f);
                btnAction.setText("Complete Task ✓");
                btnAction.setEnabled(true);
                btnAction.setOnClickListener(v2 -> completeTask());
            }
        }.start();
    }

    private void updateTimerDisplay(long ms) {
        long m = TimeUnit.MILLISECONDS.toMinutes(ms);
        long s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        tvTimer.setText(String.format(Locale.US, "%02d:%02d", m, s));
    }

    // ── PHOTO ─────────────────────────────────────────────────────────────────

    private void setupPhoto() {
        tvTimer.setVisibility(View.GONE);
        btnAction.setText("Take Photo Proof");
        btnAction.setEnabled(true);
        btnAction.setOnClickListener(v -> {
            Intent i = new Intent(this, CameraProofActivity.class);
            i.putExtra("task_id", task.id);
            startActivityForResult(i, REQUEST_PHOTO);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PHOTO && resultCode == RESULT_OK) {
            completeTask();
        }
    }

    // ── COMPLETION ────────────────────────────────────────────────────────────

    private void completeTask() {
        // Record completion
        AppState.markTaskCompleted(this, task.id);
        AppState.incrementTasksDoneToday(this);
        AppState.addXp(this, task.xp);

        // Gate progress
        if (AppState.isGateActive(this)) {
            boolean unlocked = AppState.incrementGateTask(this);
            if (unlocked) {
                // Gate cleared — finish and let user uninstall
                setResult(RESULT_OK);
                finish();
                return;
            }
        }

        // Grant access window if coming from overlay
        if (fromOverlay) {
            BlockOverlayActivity.grantAccessAndScheduleExpiry(this, task);
            setResult(RESULT_OK);
            finish();
            return;
        }

        btnAction.setText("Done! +" + task.xp + " XP");
        btnAction.setEnabled(false);

        // Return to previous screen after brief delay
        tvEmoji.postDelayed(this::finish, 1200);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}

package com.dopaminequest.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dopaminequest.R;
import com.dopaminequest.adapters.OverlayTaskAdapter;
import com.dopaminequest.models.Task;
import com.dopaminequest.utils.AppState;

import java.util.List;

public class UninstallGateActivity extends AppCompatActivity {

    private TextView  tvProgress;
    private RecyclerView rvTasks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uninstall_gate);
        updateProgress();

        rvTasks = findViewById(R.id.rv_tasks);
        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        loadTasks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Gate may have been cleared by TaskDetailActivity
        if (!AppState.isGateActive(this)) {
            // All tasks done — allow uninstall
            finish();
        }
        updateProgress();
        loadTasks();
    }

    private void updateProgress() {
        tvProgress = findViewById(R.id.tv_progress);
        int done  = AppState.getGateTasksDone(this);
        int total = AppState.GATE_TASKS_REQUIRED;
        tvProgress.setText(done + " / " + total + " tasks complete");
    }

    private void loadTasks() {
        List<Task> tasks = Task.all();
        OverlayTaskAdapter adapter = new OverlayTaskAdapter(this, tasks, task -> {
            Intent i = new Intent(this, TaskDetailActivity.class);
            i.putExtra("task_id", task.id);
            i.putExtra("from_overlay", false); // gate tasks don't grant access window
            startActivity(i);
        });
        rvTasks.setAdapter(adapter);
    }

    @Override
    public void onBackPressed() {
        // Cannot escape gate by pressing back
    }
}

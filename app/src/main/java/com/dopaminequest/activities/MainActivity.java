package com.dopaminequest.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.dopaminequest.R;
import com.dopaminequest.services.PersistenceService;
import com.dopaminequest.utils.AppState;

public class MainActivity extends AppCompatActivity {

    private LinearLayout navHome, navTasks, navQuests;
    private TextView tvNavHome, tvNavTasks, tvNavQuests;
    private View dotHome, dotTasks, dotQuests;

    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start persistence service
        Intent svc = new Intent(this, PersistenceService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        navHome   = findViewById(R.id.nav_home);
        navTasks  = findViewById(R.id.nav_tasks);
        navQuests = findViewById(R.id.nav_quests);

        navHome.setOnClickListener(v -> switchTab(0));
        navTasks.setOnClickListener(v -> switchTab(1));
        navQuests.setOnClickListener(v -> switchTab(2));

        switchTab(0);
    }

    private void switchTab(int tab) {
        currentTab = tab;
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);

        Fragment f;
        switch (tab) {
            case 1:  f = new com.dopaminequest.fragments.TasksFragment(); break;
            case 2:  f = new com.dopaminequest.fragments.QuestsFragment(); break;
            default: f = new com.dopaminequest.fragments.HomeFragment();   break;
        }
        ft.replace(R.id.fragment_container, f);
        ft.commit();
        updateNavHighlight(tab);
    }

    private void updateNavHighlight(int tab) {
        int inactive = getResources().getColor(R.color.ink3, getTheme());
        int active   = getResources().getColor(R.color.accent, getTheme());

        // Reset all
        for (int i = 0; i < 3; i++) {
            LinearLayout nav = i == 0 ? navHome : i == 1 ? navTasks : navQuests;
            TextView tv = nav.findViewWithTag("nav_label_" + i);
            View dot    = nav.findViewWithTag("nav_dot_" + i);
            if (tv  != null) tv.setTextColor(i == tab ? active : inactive);
            if (dot != null) dot.setVisibility(i == tab ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If gate became active while we were away, recheck
        if (AppState.isGateActive(this)) {
            startActivity(new Intent(this, UninstallGateActivity.class));
        }
    }
}

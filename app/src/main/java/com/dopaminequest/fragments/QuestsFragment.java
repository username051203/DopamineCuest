package com.dopaminequest.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dopaminequest.R;
import com.dopaminequest.utils.AppState;

public class QuestsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        int done = AppState.getTasksDoneToday(requireContext());

        // Quest 1: Morning Warrior — 3 tasks
        View q1 = view.findViewById(R.id.quest_1);
        updateQuest(q1, done, 3);

        // Quest 2: Movement Master — 5 tasks this week
        View q2 = view.findViewById(R.id.quest_2);
        updateQuest(q2, Math.min(done + 2, 5), 5);

        // Quest 3: Streak — 7 days
        View q3 = view.findViewById(R.id.quest_3);
        updateQuest(q3, AppState.getStreak(requireContext()), 7);
    }

    private void updateQuest(View questView, int done, int total) {
        if (questView == null) return;
        TextView tvProgress = questView.findViewById(R.id.tv_quest_progress);
        View     fillBar    = questView.findViewById(R.id.view_quest_fill);
        View     barBg      = questView.findViewById(R.id.view_quest_bar);

        if (tvProgress != null) tvProgress.setText(done + " / " + total);
        if (fillBar != null && barBg != null) {
            barBg.post(() -> {
                int w = barBg.getWidth();
                float pct = (float) Math.min(done, total) / total;
                ViewGroup.LayoutParams lp = fillBar.getLayoutParams();
                lp.width = (int)(w * pct);
                fillBar.setLayoutParams(lp);
            });
        }
    }
}

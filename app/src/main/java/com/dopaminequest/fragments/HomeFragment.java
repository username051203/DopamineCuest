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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Date
        TextView tvDate = view.findViewById(R.id.tv_date);
        String date = new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(new Date());
        tvDate.setText(date.toUpperCase());

        // Stats
        int xp     = AppState.getXp(requireContext());
        int level  = AppState.getLevel(requireContext());
        int streak = AppState.getStreak(requireContext());
        int done   = AppState.getTasksDoneToday(requireContext());
        int total  = 10;

        ((TextView) view.findViewById(R.id.tv_xp)).setText(String.valueOf(xp));
        ((TextView) view.findViewById(R.id.tv_streak)).setText(streak + "d");
        ((TextView) view.findViewById(R.id.tv_done)).setText(done + "/" + total);
        ((TextView) view.findViewById(R.id.tv_level)).setText(String.valueOf(level));

        // Progress bar
        View progressFill = view.findViewById(R.id.view_progress_fill);
        View progressBar  = view.findViewById(R.id.view_progress_bar);
        progressBar.post(() -> {
            int width = progressBar.getWidth();
            float pct  = (float) done / total;
            ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
            lp.width = (int) (width * pct);
            progressFill.setLayoutParams(lp);
        });

        // Coach CTA
        view.findViewById(R.id.layout_coach_cta).setOnClickListener(v -> {
        });
    }
}

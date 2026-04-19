package com.dopaminequest.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dopaminequest.R;
import com.dopaminequest.models.Task;
import com.dopaminequest.utils.AppState;

import org.json.JSONArray;
import org.json.JSONObject;

public class QuizActivity extends AppCompatActivity {

    private TextView tvQuestion, tvProgress, tvFeedback;
    private Button[] optionBtns = new Button[4];
    private Button   btnNext;

    private JSONArray questions;
    private int       currentIndex  = 0;
    private int       correctCount  = 0;
    private int       attempts      = 0; // attempts on current question
    private boolean   fromOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        tvQuestion  = findViewById(R.id.tv_question);
        tvProgress  = findViewById(R.id.tv_progress);
        tvFeedback  = findViewById(R.id.tv_feedback);
        btnNext     = findViewById(R.id.btn_next);
        optionBtns[0] = findViewById(R.id.btn_option_0);
        optionBtns[1] = findViewById(R.id.btn_option_1);
        optionBtns[2] = findViewById(R.id.btn_option_2);
        optionBtns[3] = findViewById(R.id.btn_option_3);

        fromOverlay = getIntent().getBooleanExtra("from_overlay", false);

        try {
            questions = new JSONArray(getIntent().getStringExtra("questions_json"));
        } catch (Exception e) {
            finish(); return;
        }

        btnNext.setOnClickListener(v -> nextQuestion());
        showQuestion();
    }

    private void showQuestion() {
        if (currentIndex >= questions.length()) {
            finishQuiz(); return;
        }

        attempts = 0;
        tvFeedback.setVisibility(View.INVISIBLE);
        btnNext.setVisibility(View.GONE);

        try {
            JSONObject q = questions.getJSONObject(currentIndex);
            tvQuestion.setText((currentIndex + 1) + ". " + q.getString("q"));
            tvProgress.setText((currentIndex + 1) + " / " + questions.length());

            JSONArray options = q.getJSONArray("options");
            for (int i = 0; i < optionBtns.length; i++) {
                final int idx = i;
                String label = (char)('A' + i) + ".  " + options.getString(i);
                optionBtns[i].setText(label);
                optionBtns[i].setEnabled(true);
                optionBtns[i].setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                        getResources().getColor(R.color.ink, getTheme())));
                optionBtns[i].setOnClickListener(v -> handleAnswer(idx));
            }
        } catch (Exception e) {
            nextQuestion();
        }
    }

    private void handleAnswer(int selected) {
        try {
            JSONObject q      = questions.getJSONObject(currentIndex);
            int        correct = q.getInt("answer");
            attempts++;

            // Disable all options
            for (Button b : optionBtns) b.setEnabled(false);

            if (selected == correct) {
                // Correct
                correctCount++;
                optionBtns[selected].setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                        getResources().getColor(R.color.verified_green, getTheme())));
                tvFeedback.setText("Correct!");
                tvFeedback.setTextColor(getResources().getColor(R.color.verified_green, getTheme()));
                tvFeedback.setVisibility(View.VISIBLE);
                btnNext.setVisibility(View.VISIBLE);
                btnNext.setText(currentIndex + 1 < questions.length() ? "Next →" : "Finish");

            } else {
                // Wrong
                optionBtns[selected].setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                        getResources().getColor(R.color.red_wrong, getTheme())));
                // Show correct
                optionBtns[correct].setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                        getResources().getColor(R.color.verified_green, getTheme())));

                if (attempts >= AppState.QUIZ_MAX_ATTEMPTS) {
                    // Max attempts — queue for later popup, move on
                    AppState.enqueueWrongQuestion(this, q.toString());
                    tvFeedback.setText("Queued for later — you'll see this again today.");
                    tvFeedback.setTextColor(getResources().getColor(R.color.accent, getTheme()));
                    tvFeedback.setVisibility(View.VISIBLE);
                    btnNext.setVisibility(View.VISIBLE);
                    btnNext.setText(currentIndex + 1 < questions.length() ? "Next →" : "Finish");
                } else {
                    // Let them try again
                    tvFeedback.setText("Wrong. " + (AppState.QUIZ_MAX_ATTEMPTS - attempts) + " attempt(s) left.");
                    tvFeedback.setTextColor(getResources().getColor(R.color.accent, getTheme()));
                    tvFeedback.setVisibility(View.VISIBLE);
                    // Re-enable after brief delay
                    tvFeedback.postDelayed(() -> {
                        for (Button b : optionBtns) b.setEnabled(true);
                        // Reset colors
                        for (Button b : optionBtns) b.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(
                                getResources().getColor(R.color.ink, getTheme())));
                    }, 1200);
                }
            }
        } catch (Exception e) {
            nextQuestion();
        }
    }

    private void nextQuestion() {
        currentIndex++;
        showQuestion();
    }

    private void finishQuiz() {
        // All questions answered — grant access
        Task studyTask = Task.findById(11);
        if (studyTask != null) {
            AppState.markTaskCompleted(this, studyTask.id);
            AppState.incrementTasksDoneToday(this);
            AppState.addXp(this, studyTask.xp);
            if (fromOverlay) {
                BlockOverlayActivity.grantAccessAndScheduleExpiry(this, studyTask);
            }
            if (AppState.isGateActive(this)) {
                AppState.incrementGateTask(this);
            }
        }

        // Schedule wrong answer popups if any queued
        if (AppState.hasWrongQuestions(this)) {
            com.dopaminequest.utils.PopupScheduler.scheduleNext(this);
        }

        // Show result screen
        tvQuestion.setText("Done!");
        tvProgress.setText(correctCount + " / " + questions.length() + " correct");
        tvFeedback.setText("25 minutes unlocked.\n"
            + (AppState.wrongQueueSize(this) > 0
                ? AppState.wrongQueueSize(this) + " questions will pop up during the day."
                : "Perfect score!"));
        tvFeedback.setVisibility(View.VISIBLE);
        for (Button b : optionBtns) b.setVisibility(View.GONE);
        btnNext.setText("Done");
        btnNext.setVisibility(View.VISIBLE);
        btnNext.setOnClickListener(v -> finish());
    }

    @Override
    public void onBackPressed() {
        // Cannot exit quiz — must complete it
    }
}

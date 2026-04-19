package com.dopaminequest.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dopaminequest.R;
import com.dopaminequest.utils.AppState;
import com.dopaminequest.utils.PopupScheduler;

import org.json.JSONObject;

public class QuizPopupActivity extends AppCompatActivity {

    private TextView tvQuestion, tvFeedback;
    private Button[] optionBtns = new Button[4];
    private String   questionJson;
    private int      attempts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_popup);

        tvQuestion  = findViewById(R.id.tv_question);
        tvFeedback  = findViewById(R.id.tv_feedback);
        optionBtns[0] = findViewById(R.id.btn_option_0);
        optionBtns[1] = findViewById(R.id.btn_option_1);
        optionBtns[2] = findViewById(R.id.btn_option_2);
        optionBtns[3] = findViewById(R.id.btn_option_3);

        questionJson = AppState.dequeueWrongQuestion(this);
        if (questionJson == null) { finish(); return; }

        loadQuestion();
    }

    private void loadQuestion() {
        try {
            JSONObject q = new JSONObject(questionJson);
            tvQuestion.setText(q.getString("q"));
            org.json.JSONArray options = q.getJSONArray("options");
            int correct = q.getInt("answer");

            for (int i = 0; i < optionBtns.length; i++) {
                final int idx = i;
                optionBtns[i].setText((char)('A' + i) + ".  " + options.getString(i));
                optionBtns[i].setEnabled(true);
                resetColor(optionBtns[i]);
                optionBtns[i].setOnClickListener(v -> handleAnswer(idx, correct, q));
            }
        } catch (Exception e) {
            finish();
        }
    }

    private void handleAnswer(int selected, int correct, JSONObject q) {
        attempts++;
        for (Button b : optionBtns) b.setEnabled(false);

        if (selected == correct) {
            optionBtns[selected].setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                    getResources().getColor(R.color.verified_green, getTheme())));
            tvFeedback.setText("Correct! You can close this.");
            tvFeedback.setVisibility(View.VISIBLE);
            // Schedule next popup if more questions remain
            if (AppState.hasWrongQuestions(this)) {
                PopupScheduler.scheduleNext(this);
            }
            // Dismiss after 1.5s
            tvFeedback.postDelayed(this::finish, 1500);

        } else {
            optionBtns[selected].setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                    getResources().getColor(R.color.red_wrong, getTheme())));
            optionBtns[correct].setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                    getResources().getColor(R.color.verified_green, getTheme())));

            if (attempts >= AppState.QUIZ_MAX_ATTEMPTS) {
                // Re-queue for tomorrow
                AppState.enqueueWrongQuestion(this, questionJson);
                tvFeedback.setText("Re-queued for tomorrow.");
                tvFeedback.setVisibility(View.VISIBLE);
                tvFeedback.postDelayed(this::finish, 1500);
            } else {
                tvFeedback.setText("Wrong. " + (AppState.QUIZ_MAX_ATTEMPTS - attempts) + " attempt(s) left.");
                tvFeedback.setVisibility(View.VISIBLE);
                tvFeedback.postDelayed(() -> {
                    for (Button b : optionBtns) { b.setEnabled(true); resetColor(b); }
                    loadQuestion(); // reload same question
                }, 1200);
            }
        }
    }

    private void resetColor(Button b) {
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            getResources().getColor(R.color.ink, getTheme())));
    }

    @Override
    public void onBackPressed() {
        // Cannot dismiss without answering
    }
}

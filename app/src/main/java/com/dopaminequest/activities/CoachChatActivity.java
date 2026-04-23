package com.dopaminequest.activities;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dopaminequest.R;
import com.dopaminequest.utils.AIController;
import com.dopaminequest.utils.AppState;

public class CoachChatActivity extends AppCompatActivity {

    private LinearLayout chatContainer;
    private ScrollView   scrollView;
    private EditText     etInput;
    private TextView     btnSend, tvTitle;
    private ProgressBar  progressBar;

    private String  blockedPkg;
    private String  appLabel;
    private boolean decided = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coach_chat);

        chatContainer = findViewById(R.id.layout_messages);
        scrollView    = findViewById(R.id.scroll_messages);
        etInput       = findViewById(R.id.et_input);
        btnSend       = findViewById(R.id.btn_send);
        tvTitle       = findViewById(R.id.tv_coach_title);
        progressBar   = findViewById(R.id.progress_bar);

        blockedPkg = getIntent().getStringExtra("blocked_pkg");
        appLabel   = resolveLabel(blockedPkg);
        tvTitle.setText("Coach — " + appLabel);

        addBubble("coach",
            "You want " + appLabel + ". Give me one clear reason why you need it right now.");

        btnSend.setOnClickListener(v -> submit());
        etInput.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEND) { submit(); return true; }
            return false;
        });
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void submit() {
        if (decided) return;
        String reason = etInput.getText().toString().trim();
        if (reason.isEmpty()) return;

        etInput.setText("");
        hideKeyboard();
        addBubble("user", reason);
        setBusy(true);

        AIController.decide(this, blockedPkg, appLabel, reason,
            new AIController.Callback() {
                @Override public void onDecision(AIController.Decision d) {
                    runOnUiThread(() -> {
                        setBusy(false);
                        addBubble("coach", d.message);
                        if (d.isGrant()) {
                            doGrant(d.minutes);
                        } else if (d.isTaskFirst()) {
                            etInput.setEnabled(false);
                            btnSend.setEnabled(false);
                            tvTitle.setText("Do a task first");
                            chatContainer.postDelayed(() -> finish(), 2500);
                        }
                        // DENY: user can try again with better reason
                        scrollToBottom();
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> {
                        setBusy(false);
                        addBubble("coach", "Connection issue. Try again.");
                        scrollToBottom();
                    });
                }
            });
    }

    private void doGrant(int minutes) {
        decided = true;
        AppState.grantTempAppAccess(this, blockedPkg, minutes);
        tvTitle.setText("✓ " + minutes + " min granted");
        etInput.setEnabled(false);
        btnSend.setEnabled(false);
        chatContainer.postDelayed(this::finish, 1600);
    }

    private void addBubble(String role, String text) {
        View bubble = LayoutInflater.from(this)
            .inflate(R.layout.item_chat_bubble, chatContainer, false);

        TextView tvRole = bubble.findViewById(R.id.tv_role);
        TextView tvText = bubble.findViewById(R.id.tv_text);
        View     bg     = bubble.findViewById(R.id.bubble_bg);

        tvRole.setText(role.equals("coach") ? "COACH" : "YOU");
        tvText.setText(text);
        tvText.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels * 0.78f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = lp.bottomMargin = 4;

        if (role.equals("user")) {
            lp.gravity   = Gravity.END;
            lp.leftMargin = 60;
            bg.setBackgroundColor(getResources().getColor(R.color.ink, getTheme()));
            tvText.setTextColor(getResources().getColor(R.color.paper, getTheme()));
            tvRole.setTextColor(getResources().getColor(R.color.ink3, getTheme()));
            tvRole.setGravity(Gravity.END);
        } else {
            lp.gravity    = Gravity.START;
            lp.rightMargin = 60;
            bg.setBackgroundColor(getResources().getColor(R.color.rule, getTheme()));
            tvText.setTextColor(getResources().getColor(R.color.ink, getTheme()));
            tvRole.setTextColor(getResources().getColor(R.color.accent, getTheme()));
        }
        bubble.setLayoutParams(lp);
        chatContainer.addView(bubble);
    }

    private void setBusy(boolean on) {
        progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(!on);
        etInput.setEnabled(!on);
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etInput.getWindowToken(), 0);
    }

    private String resolveLabel(String pkg) {
        if (pkg == null) return "that app";
        try {
            return getPackageManager()
                .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))
                .toString();
        } catch (Exception e) { return pkg; }
    }

    @Override public void onBackPressed() { finish(); }
}

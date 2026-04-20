package com.dopaminequest.activities;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.dopaminequest.R;
import com.dopaminequest.utils.AppState;
import com.dopaminequest.utils.GeminiService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoachChatActivity extends AppCompatActivity {

    private LinearLayout layoutMessages;
    private ScrollView   scrollView;
    private EditText     etInput;
    private View         btnSend, progressBar;

    private final JSONArray history = new JSONArray();
    private String blockedPkg;
    private String appLabel;
    private boolean waiting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coach_chat);

        blockedPkg = getIntent().getStringExtra("blocked_pkg");
        appLabel   = resolveAppLabel(blockedPkg);

        layoutMessages = findViewById(R.id.layout_messages);
        scrollView     = findViewById(R.id.scroll_messages);
        etInput        = findViewById(R.id.et_input);
        btnSend        = findViewById(R.id.btn_send);
        progressBar    = findViewById(R.id.progress_bar);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tv_coach_title)).setText("Coach — " + appLabel);

        btnSend.setOnClickListener(v -> sendMessage());
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });

        appendBubble("coach",
            "Hey. You want access to " + appLabel + ".\n" +
            "Tell me why you need it right now — be specific.");
    }

    private void sendMessage() {
        if (waiting) return;
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;

        etInput.setText("");
        hideKeyboard();
        appendBubble("user", text);
        setWaiting(true);

        GeminiService.coachChat(this, blockedPkg, appLabel, history, text,
            new GeminiService.Callback<String>() {
                @Override public void onResult(String reply) {
                    try {
                        history.put(new JSONObject().put("role", "user").put("text", text));
                        history.put(new JSONObject().put("role", "model").put("text", reply));
                    } catch (Exception ignored) {}
                    runOnUiThread(() -> {
                        setWaiting(false);
                        appendBubble("coach", reply);
                        checkForGrant(reply);
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> {
                        setWaiting(false);
                        appendBubble("error", "Error: " + error);
                    });
                }
            });
    }

    private void checkForGrant(String reply) {
        Matcher m = Pattern.compile("\\[GRANT:(\\d+)]").matcher(reply);
        if (!m.find()) return;
        int minutes = Math.min(30, Integer.parseInt(m.group(1)));
        AppState.grantAccessWindow(this, minutes);
        appendBubble("system", "✓ Access granted for " + minutes + " min. Go.");
        scrollView.postDelayed(this::finish, 1800);
    }

    private void appendBubble(String role, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setLineSpacing(0, 1.45f);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(8);

        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        tv.setMaxWidth(maxW);
        tv.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));

        switch (role) {
            case "user":
                tv.setBackgroundColor(ContextCompat.getColor(this, R.color.ink));
                tv.setTextColor(ContextCompat.getColor(this, R.color.paper));
                lp.gravity = android.view.Gravity.END;
                lp.leftMargin = dpToPx(48);
                break;
            case "system":
                tv.setBackgroundColor(ContextCompat.getColor(this, R.color.verified_green));
                tv.setTextColor(ContextCompat.getColor(this, R.color.white));
                lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                tv.setTextSize(13f);
                break;
            case "error":
                tv.setBackgroundColor(ContextCompat.getColor(this, R.color.red_wrong));
                tv.setTextColor(ContextCompat.getColor(this, R.color.white));
                lp.gravity = android.view.Gravity.START;
                lp.rightMargin = dpToPx(48);
                break;
            default:
                tv.setBackgroundColor(ContextCompat.getColor(this, R.color.rule));
                tv.setTextColor(ContextCompat.getColor(this, R.color.ink));
                lp.gravity = android.view.Gravity.START;
                lp.rightMargin = dpToPx(48);
                break;
        }

        tv.setLayoutParams(lp);
        layoutMessages.addView(tv);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void setWaiting(boolean on) {
        waiting = on;
        progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(!on);
        etInput.setEnabled(!on);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etInput.getWindowToken(), 0);
    }

    private String resolveAppLabel(String pkg) {
        if (pkg == null) return "that app";
        try {
            return getPackageManager()
                .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))
                .toString();
        } catch (Exception e) { return pkg; }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

package com.dopaminequest.activities;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dopaminequest.R;
import com.dopaminequest.utils.AppState;
import com.dopaminequest.utils.CoachEngine;
import com.dopaminequest.utils.GeminiService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoachChatActivity extends AppCompatActivity {

    // IDs match activity_coach_chat.xml exactly
    private LinearLayout chatContainer;  // R.id.layout_messages
    private ScrollView   scrollView;     // R.id.scroll_messages
    private EditText     etInput;        // R.id.et_input
    private TextView     btnSend;        // R.id.btn_send (TextView in layout)
    private TextView     tvTitle;        // R.id.tv_coach_title
    private ProgressBar  progressBar;    // R.id.progress_bar

    private String   blockedPkg;
    private String   appLabel;
    private JSONArray history   = new JSONArray();
    private boolean  granted   = false;
    private int      turnCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coach_chat);

        // Match actual IDs from the XML
        chatContainer = findViewById(R.id.layout_messages);
        scrollView    = findViewById(R.id.scroll_messages);
        etInput       = findViewById(R.id.et_input);
        btnSend       = findViewById(R.id.btn_send);
        tvTitle       = findViewById(R.id.tv_coach_title);
        progressBar   = findViewById(R.id.progress_bar);

        blockedPkg = getIntent().getStringExtra("blocked_pkg");
        appLabel   = getAppLabel(blockedPkg);
        tvTitle.setText("Coach — " + appLabel);

        addBubble("coach",
            "You're trying to open " + appLabel + ". " +
            "Give me a real reason and I'll consider it.");

        btnSend.setOnClickListener(v -> sendMessage());
        findViewById(R.id.btn_back).setOnClickListener(v -> saveAndFinish());

        // Handle IME send action
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void sendMessage() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty() || granted) return;

        etInput.setText("");
        setBusy(true);
        addBubble("user", text);
        turnCount++;

        // Try local engine first — zero network calls
        CoachEngine.Decision local =
            CoachEngine.evaluate(text, appLabel, turnCount);

        if (!local.needsGemini) {
            setBusy(false);
            if (local.granted) {
                doGrant(local.minutes,
                    local.message.replaceAll("\\[GRANT:\\d+\\]", "").trim());
            } else {
                addBubble("coach", local.message);
            }
            scrollToBottom();
            return;
        }

        // Escalate to Gemini for ambiguous cases
        if (!AppState.hasGeminiKey(this)) {
            // No key — be fair after sustained effort
            setBusy(false);
            doGrant(10, "I'll take your word for it. 10 minutes — use it well.");
            scrollToBottom();
            return;
        }

        // Record in history for Gemini
        try {
            history.put(new JSONObject().put("role", "user").put("text", text));
        } catch (Exception ignored) {}

        GeminiService.coachChat(this, blockedPkg, appLabel, history, text,
            new GeminiService.Callback<String>() {
                @Override public void onResult(String reply) {
                    runOnUiThread(() -> {
                        setBusy(false);
                        try {
                            history.put(new JSONObject()
                                .put("role", "model").put("text", reply));
                        } catch (Exception ignored) {}

                        int minutes = parseGrant(reply);
                        String display = reply
                            .replaceAll("\\[GRANT:\\d+\\]", "").trim();
                        addBubble("coach", display);

                        if (minutes > 0) doGrant(minutes, null);
                        scrollToBottom();
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> {
                        setBusy(false);
                        // Network failed — grant generously since user argued their case
                        doGrant(15,
                            "Connection issue — I'll trust you. " +
                            "15 minutes, make it count.");
                        scrollToBottom();
                    });
                }
            });

        scrollToBottom();
    }

    private void doGrant(int minutes, String message) {
        granted = true;
        AppState.grantTempAppAccess(this, blockedPkg, minutes);
        scheduleExpiry();
        if (message != null && !message.isEmpty()) {
            addBubble("coach", message);
        }
        tvTitle.setText("✓ " + minutes + " min granted");
        etInput.setEnabled(false);
        btnSend.setEnabled(false);
        // Dismiss after showing confirmation
        chatContainer.postDelayed(this::saveAndFinish, 1600);
    }

    private void scheduleExpiry() {
        android.app.AlarmManager am =
            (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;
        android.content.Intent i = new android.content.Intent(this,
            com.dopaminequest.receivers.AccessWindowReceiver.class);
        i.setAction("com.dopaminequest.ACTION_ACCESS_EXPIRED");
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
            this, 9002, i,
            android.app.PendingIntent.FLAG_IMMUTABLE |
            android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        long at = AppState.getTempAppUntil(this);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private int parseGrant(String text) {
        Matcher m = Pattern.compile("\\[GRANT:(\\d+)\\]").matcher(text);
        if (m.find()) {
            try { return Math.min(30, Integer.parseInt(m.group(1))); }
            catch (Exception ignored) {}
        }
        return 0;
    }

    private void setBusy(boolean busy) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(!busy);
    }

    private void addBubble(String role, String text) {
        View bubble = LayoutInflater.from(this)
            .inflate(R.layout.item_chat_bubble, chatContainer, false);

        TextView tvRole = bubble.findViewById(R.id.tv_role);
        TextView tvText = bubble.findViewById(R.id.tv_text);
        View     bg     = bubble.findViewById(R.id.bubble_bg);

        tvRole.setText(role.equals("coach") ? "COACH" : "YOU");
        tvText.setText(text);

        // Constrain width via TextView maxWidth (works on the inner TextView)
        int maxW = (int)(getResources().getDisplayMetrics().widthPixels * 0.78f);
        tvText.setMaxWidth(maxW);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin    = 4;
        lp.bottomMargin = 4;

        if (role.equals("user")) {
            lp.gravity   = Gravity.END;
            lp.leftMargin = 60;
            bg.setBackgroundColor(
                getResources().getColor(R.color.ink, getTheme()));
            tvText.setTextColor(
                getResources().getColor(R.color.paper, getTheme()));
            tvRole.setTextColor(
                getResources().getColor(R.color.ink3, getTheme()));
            tvRole.setGravity(Gravity.END);
        } else {
            lp.gravity    = Gravity.START;
            lp.rightMargin = 60;
            bg.setBackgroundColor(
                getResources().getColor(R.color.rule, getTheme()));
            tvText.setTextColor(
                getResources().getColor(R.color.ink, getTheme()));
            tvRole.setTextColor(
                getResources().getColor(R.color.accent, getTheme()));
        }
        bubble.setLayoutParams(lp);
        chatContainer.addView(bubble);
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void saveAndFinish() {
        try {
            AppState.logConversation(this, blockedPkg, history.toString());
        } catch (Exception ignored) {}
        finish();
    }

    private String getAppLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(
                pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg != null ? pkg : "that app"; }
    }

    @Override public void onBackPressed() { saveAndFinish(); }
}
